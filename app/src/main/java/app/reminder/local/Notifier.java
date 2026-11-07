package app.reminder.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/** แสดงการแจ้งเตือน เสียงดังซ้ำจนกว่าจะรับทราบ (หรือครบ 2 นาที) พร้อมปุ่ม รับทราบ / เลื่อน 5 นาที */
public final class Notifier {
    public static final String CHANNEL = "reminders_v1";
    public static final String ACTION_ACK = "app.reminder.local.ACK";
    public static final String ACTION_SNOOZE = "app.reminder.local.SNOOZE_NOW";
    public static final long SNOOZE_MS = 5 * 60 * 1000L;
    private static final long TIMEOUT_MS = 2 * 60 * 1000L;

    private Notifier() {}

    public static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "การเตือน", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("เสียงและการแจ้งเตือนเมื่อถึงเวลา");
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ch.setSound(sound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 800});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent action(Context c, int id, String act, int offset) {
        Intent i = new Intent(c, ActionReceiver.class);
        i.setAction(act);
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, id * 10 + offset, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void show(Context c, Reminder r, long now, long occ, boolean snooze) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);

        StringBuilder text = new StringBuilder();
        if (r.note != null && !r.note.isEmpty()) text.append(r.note).append('\n');
        if (!snooze && now - occ > 120000) {
            text.append("กำหนด ").append(Fmt.when(occ)).append(" (ล่าช้า ").append(Fmt.late(now - occ)).append(")");
        } else {
            text.append(snooze ? "เลื่อนไว้ ถึงเวลาแล้ว" : "ถึงเวลาแล้ว");
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, r.id * 10 + 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Icon icon = Icon.createWithResource(c, R.drawable.ic_stat_bell);
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle("⏰ " + r.title)
                .setContentText(text.toString().split("\n")[0])
                .setStyle(new Notification.BigTextStyle().bigText(text.toString()))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setWhen(now)
                .setShowWhen(true)
                .setContentIntent(openPi)
                .setTimeoutAfter(TIMEOUT_MS)
                .addAction(new Notification.Action.Builder(icon, "รับทราบ", action(c, r.id, ACTION_ACK, 1)).build())
                .addAction(new Notification.Action.Builder(icon, "เลื่อน 5 นาที", action(c, r.id, ACTION_SNOOZE, 2)).build());

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // เสียงวนซ้ำจนกว่าจะรับทราบ
        try {
            nm.notify(r.id, n);
        } catch (SecurityException e) {
            // ยังไม่ได้อนุญาตการแจ้งเตือน (Android 13+) ไม่ต้องทำอะไร แอปจะแสดงแบนเนอร์ให้กดอนุญาต
        }
    }

    public static void cancel(Context c, int id) {
        c.getSystemService(NotificationManager.class).cancel(id);
    }
}
