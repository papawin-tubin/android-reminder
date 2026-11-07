package app.reminder.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** ถึงเวลา: แสดงการแจ้งเตือน แล้วตั้ง alarm รอบถัดไป */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", 0);
        boolean snooze = intent.getBooleanExtra("snooze", false);
        long now = System.currentTimeMillis();
        long occ = intent.getLongExtra("occ", now);
        if (id == 0) return;
        Reminder r = Store.get(c, id);
        if (r == null || !r.enabled) return;

        if (snooze) {
            if (r.snoozeUntil == 0) return; // ถูกยกเลิกไปแล้ว
            r.snoozeUntil = 0;
        } else if (Reminder.ONCE.equals(r.type)) {
            if (r.done) return;
            r.done = true;
        } else {
            r.cursor = now;
        }
        Store.upsert(c, r);
        Notifier.show(c, r, now, occ, snooze);
        Alarms.schedule(c, r);
    }
}
