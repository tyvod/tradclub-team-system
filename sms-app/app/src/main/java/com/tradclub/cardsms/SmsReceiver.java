package com.tradclub.cardsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/** SMS 수신 시 본문을 모아 Sender로 넘긴다. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        // 여러 조각으로 나뉜 긴 문자를 발신번호별로 합친다
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = String.valueOf(m.getOriginatingAddress());
            StringBuilder sb = bodies.get(from);
            if (sb == null) { sb = new StringBuilder(); bodies.put(from, sb); }
            sb.append(m.getMessageBody());
        }

        final Context app = context.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                for (StringBuilder sb : bodies.values()) Sender.handle(app, sb.toString(), "SMS");
            } finally {
                pr.finish();
            }
        }).start();
    }
}
