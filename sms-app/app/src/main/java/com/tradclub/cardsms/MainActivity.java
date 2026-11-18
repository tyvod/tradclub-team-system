package com.tradclub.cardsms;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText urlEt, tokenEt, kwEt;
    private TextView statusTv, logTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("카드 승인 문자 → 구글 시트");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        statusTv = new TextView(this);
        statusTv.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusTv);

        SharedPreferences p = Sender.prefs(this);
        urlEt = field(root, "웹앱 주소 (…/exec)", p.getString("url", ""), false);
        tokenEt = field(root, "토큰 (스크립트 TOKEN 값)", p.getString("token", ""), true);
        kwEt = field(root, "포함 단어", p.getString("keyword", Sender.DEFAULT_KEYWORD), false);

        button(root, "1. 설정 저장", v -> {
            Sender.prefs(this).edit()
                    .putString("url", urlEt.getText().toString().trim())
                    .putString("token", tokenEt.getText().toString().trim())
                    .putString("keyword", kwEt.getText().toString().trim())
                    .apply();
            toast("저장했습니다");
            refresh();
        });
        button(root, "2. 문자 수신 권한 허용", v ->
                requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 1));
        button(root, "3. 알림 접근 허용 (장문 문자용)", v ->
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        button(root, "권한이 막히면: 앱 정보 → ⋮ → 제한된 설정 허용", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));
        button(root, "배터리 제한 해제", v ->
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button(root, "테스트 전송 (시트에 1원 테스트 줄 추가)", v -> {
            String now = new SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(new Date());
            String sample = "신한카드(0000)승인 테스트 1원(일시불)" + now + " 앱테스트 누적1원";
            new Thread(() -> {
                SharedPreferences sp = Sender.prefs(this);
                String res;
                try {
                    res = Sender.post(sp.getString("url", ""), sp.getString("token", ""), sample);
                } catch (Exception e) {
                    res = "오류 " + e.getClass().getSimpleName();
                }
                Sender.log(this, "테스트 결과: " + res.trim());
                runOnUiThread(this::refresh);
            }).start();
        });
        button(root, "미전송 다시 보내기", v -> new Thread(() -> {
            Sender.flush(this);
            runOnUiThread(this::refresh);
        }).start());

        TextView logTitle = new TextView(this);
        logTitle.setText("\n최근 기록");
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(logTitle);

        logTv = new TextView(this);
        logTv.setTextSize(12);
        logTv.setTextIsSelectable(true);
        root.addView(logTv);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    private void refresh() {
        boolean sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean noti = enabled != null && enabled.contains(getPackageName());
        SharedPreferences p = Sender.prefs(this);
        boolean cfg = !p.getString("url", "").isEmpty() && !p.getString("token", "").isEmpty();
        statusTv.setText(
                (cfg ? "✅" : "❌") + " 웹앱 주소/토큰\n"
                        + (sms ? "✅" : "❌") + " 문자 수신 권한\n"
                        + (noti ? "✅" : "⚠️") + " 알림 접근 (장문 문자 보완)\n"
                        + "미전송 " + Sender.pendingCount(this) + "건");
        logTv.setText(p.getString("log", "(아직 기록 없음)"));
    }

    private EditText field(LinearLayout root, String hint, String value, boolean secret) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setPadding(0, dp(8), 0, 0);
        root.addView(label);
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(value);
        if (secret) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(et);
        return et;
    }

    private void button(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
