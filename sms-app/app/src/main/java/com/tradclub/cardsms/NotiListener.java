package com.tradclub.cardsms;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * 메시지 앱 알림에서 본문을 읽는다.
 * 장문(LMS/MMS)으로 와서 SMS 수신 이벤트로 안 잡히는 승인 문자를 보완하기 위함.
 */
public class NotiListener extends NotificationListenerService {

    private static boolean isMessagingApp(String pkg) {
        return pkg != null && (pkg.equals("com.samsung.android.messaging")
                || pkg.equals("com.google.android.apps.messaging")
                || pkg.equals("com.android.mms")
                || pkg.contains("messaging"));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !isMessagingApp(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;

        final List<String> texts = new ArrayList<>();
        Bundle ex = n.extras;

        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null) {
            for (Parcelable p : msgs) {
                if (p instanceof Bundle) {
                    CharSequence t = ((Bundle) p).getCharSequence("text");
                    if (t != null) texts.add(t.toString());
                }
            }
        }
        if (texts.isEmpty()) {
            CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence txt = ex.getCharSequence(Notification.EXTRA_TEXT);
            if (big != null) texts.add(big.toString());
            else if (txt != null) texts.add(txt.toString());
        }
        if (texts.isEmpty()) return;

        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            for (String t : texts) Sender.handle(app, t, "알림");
        }).start();
    }
}
