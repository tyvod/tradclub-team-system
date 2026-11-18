package com.tradclub.cardsms;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 카드 승인 문자를 걸러서 Apps Script 웹앱으로 전송하고, 실패분은 보관했다가 재전송한다. */
public final class Sender {
    static final String PREF = "cfg";
    static final String DEFAULT_KEYWORD = "신한카드";
    private static final long DEDUPE_MS = 24L * 3600 * 1000;
    private static final Object LOCK = new Object();

    private Sender() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.replace("[Web발신]", "").replaceAll("\\s+", " ").trim();
    }

    /** 수신 경로(SMS/알림)에서 호출. 네트워크 작업이 있으므로 백그라운드 스레드에서 부를 것. */
    static void handle(Context ctx, String body, String source) {
        SharedPreferences p = prefs(ctx);
        String kw = p.getString("keyword", DEFAULT_KEYWORD).trim();
        String norm = normalize(body);
        if (norm.isEmpty()) return;
        if (!kw.isEmpty() && !norm.contains(kw)) return;
        if (!(norm.contains("승인") || norm.contains("취소"))) return;

        synchronized (LOCK) {
            if (isRecentDuplicate(p, norm)) return;
            addPending(p, norm);
        }
        log(ctx, "수신(" + source + "): " + shorten(norm));
        flush(ctx);
    }

    private static boolean isRecentDuplicate(SharedPreferences p, String norm) {
        long now = System.currentTimeMillis();
        StringBuilder keep = new StringBuilder();
        boolean dup = false;
        for (String line : p.getString("recent", "").split("\n")) {
            int t = line.indexOf('\t');
            if (t < 0) continue;
            long ts;
            try { ts = Long.parseLong(line.substring(0, t)); } catch (Exception e) { continue; }
            if (now - ts > DEDUPE_MS) continue;
            String txt = line.substring(t + 1);
            if (txt.equals(norm) || txt.contains(norm) || norm.contains(txt)) dup = true;
            keep.append(line).append('\n');
        }
        if (!dup) keep.append(now).append('\t').append(norm).append('\n');
        p.edit().putString("recent", keep.toString()).apply();
        return dup;
    }

    private static JSONArray pending(SharedPreferences p) {
        try { return new JSONArray(p.getString("pending", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static void addPending(SharedPreferences p, String text) {
        JSONArray arr = pending(p);
        arr.put(text);
        p.edit().putString("pending", arr.toString()).apply();
    }

    static int pendingCount(Context ctx) {
        return pending(prefs(ctx)).length();
    }

    /** 대기 중인 문자를 모두 전송 시도. */
    static void flush(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String url = p.getString("url", "").trim();
        String token = p.getString("token", "").trim();
        if (url.isEmpty() || token.isEmpty()) {
            log(ctx, "전송 보류: 웹앱 주소/토큰을 먼저 저장하세요");
            return;
        }
        synchronized (LOCK) {
            JSONArray arr = pending(p);
            JSONArray left = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                String text = arr.optString(i, "");
                if (text.isEmpty()) continue;
                String res;
                try {
                    res = post(url, token, text);
                } catch (Exception e) {
                    res = "네트워크 오류 " + e.getClass().getSimpleName();
                }
                String r = res.trim();
                if (r.equals("ok") || r.equals("duplicate")) {
                    log(ctx, (r.equals("ok") ? "시트 입력 완료: " : "이미 입력됨: ") + shorten(text));
                } else {
                    log(ctx, "전송 실패(" + shorten(r) + "): " + shorten(text));
                    left.put(text);
                }
            }
            p.edit().putString("pending", left.toString()).apply();
        }
    }

    static String post(String url, String token, String body) throws Exception {
        String full = url + (url.contains("?") ? "&" : "?") + "token=" + URLEncoder.encode(token, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(full).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        try (OutputStream o = c.getOutputStream()) { o.write(b); }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        }
        c.disconnect();
        if (code >= 400) return "HTTP " + code;
        // 로그인 페이지 등 HTML이 오면 배포 권한 문제
        if (text.trim().startsWith("<")) return "HTML 응답(배포 권한 확인)";
        return text;
    }

    static void log(Context ctx, String msg) {
        SharedPreferences p = prefs(ctx);
        String ts = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA).format(new Date());
        String old = p.getString("log", "");
        String all = ts + "  " + msg + "\n" + old;
        String[] lines = all.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 40); i++) sb.append(lines[i]).append('\n');
        p.edit().putString("log", sb.toString()).apply();
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
