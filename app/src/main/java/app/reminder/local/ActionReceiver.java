package app.reminder.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** ปุ่ม รับทราบ / เลื่อน 5 นาที บนการแจ้งเตือน */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", 0);
        if (id == 0) return;
        Notifier.cancel(c, id);
        if (Notifier.ACTION_SNOOZE.equals(intent.getAction())) {
            Reminder r = Store.get(c, id);
            if (r == null) return;
            r.snoozeUntil = System.currentTimeMillis() + Notifier.SNOOZE_MS;
            Store.upsert(c, r);
            Alarms.schedule(c, r);
        }
    }
}
