package app.reminder.local;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

/** ตั้งนาฬิกาปลุกของระบบ (AlarmManager) ให้แต่ละรายการ เตือนได้แม้ปิดแอปและเครื่องหลับ */
public final class Alarms {
    public static final String ACTION_FIRE = "app.reminder.local.FIRE";
    public static final String ACTION_SNOOZE_FIRE = "app.reminder.local.SNOOZE_FIRE";
    private static final long GRACE_MS = 60_000; // ที่เลยเวลาไม่ถึง 1 นาที ปล่อยให้ alarm ปกติจัดการ

    private Alarms() {}

    private static PendingIntent pi(Context c, int id, boolean snooze, long occ) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(snooze ? ACTION_SNOOZE_FIRE : ACTION_FIRE);
        i.putExtra("id", id);
        i.putExtra("snooze", snooze);
        i.putExtra("occ", occ);
        return PendingIntent.getBroadcast(c, id * 2 + (snooze ? 1 : 0), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context c, Reminder r) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pi(c, r.id, false, 0));
        am.cancel(pi(c, r.id, true, 0));
    }

    private static void set(Context c, long at, PendingIntent p) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); // ไม่มีสิทธิ์แบบเป๊ะ: เตือนได้แต่อาจเลื่อนหลายนาที
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    /** ตั้ง alarm ถัดไปของรายการเดียว (ล้างของเดิมก่อน) */
    public static void schedule(Context c, Reminder r) {
        cancel(c, r);
        if (!r.enabled) return;
        long now = System.currentTimeMillis();
        if (r.snoozeUntil > 0) set(c, Math.max(r.snoozeUntil, now + 1000), pi(c, r.id, true, r.snoozeUntil));
        long t;
        if (Reminder.ONCE.equals(r.type)) {
            if (r.done) return;
            t = Schedule.onceTime(r);
            if (t <= now) return; // เลยเวลาแล้ว ให้ catchUp จัดการ
        } else {
            t = Schedule.nextOcc(r, now);
            if (t < 0) return;
        }
        set(c, t, pi(c, r.id, false, t));
    }

    public static void scheduleAll(Context c) {
        for (Reminder r : Store.all(c)) schedule(c, r);
    }

    /**
     * เตือนรายการที่พลาดไปแล้ว (เครื่องปิด/ถูกฆ่าแอป/เปลี่ยนเวลา) พร้อมบอกว่าล่าช้ากี่นาที
     * เรียกตอนเปิดเครื่อง เปิดแอป และเวลาระบบเปลี่ยน
     */
    public static void catchUp(Context c) {
        long now = System.currentTimeMillis();
        for (Reminder r : Store.all(c)) {
            if (!r.enabled) continue;
            boolean changed = false;
            if (r.snoozeUntil > 0 && now - r.snoozeUntil > GRACE_MS) {
                long was = r.snoozeUntil;
                r.snoozeUntil = 0;
                Notifier.show(c, r, now, was, true);
                changed = true;
            }
            if (Reminder.ONCE.equals(r.type)) {
                if (!r.done) {
                    long t = Schedule.onceTime(r);
                    if (now - t > GRACE_MS) { r.done = true; Notifier.show(c, r, now, t, false); changed = true; }
                }
            } else {
                long occ = Schedule.nextOcc(r, r.cursor > 0 ? r.cursor : now);
                if (occ > 0 && now - occ > GRACE_MS) { r.cursor = now; Notifier.show(c, r, now, occ, false); changed = true; }
            }
            if (changed) Store.upsert(c, r);
        }
    }
}
