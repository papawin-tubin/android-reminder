package app.reminder.local;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/** จัดรูปแบบข้อความภาษาไทย */
public final class Fmt {
    private Fmt() {}

    /** เช่น "เสาร์ 7 พ.ย. 2569 14:00" ; ปีเป็น พ.ศ. คำนวณเองเพื่อไม่ให้ขึ้นกับปฏิทินของเครื่อง */
    public static String when(long ms) {
        Locale th = Locale.forLanguageTag("th-TH");
        GregorianCalendar g = new GregorianCalendar();
        g.setTimeInMillis(ms);
        String head = new SimpleDateFormat("EEE d MMM", th).format(new Date(ms));
        String hm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
        return head + " " + (g.get(Calendar.YEAR) + 543) + " " + hm;
    }

    public static String left(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (d > 0) return "อีก " + d + " วัน " + h + " ชม.";
        if (h > 0) return "อีก " + h + " ชม. " + m + " นาที";
        if (m > 0) return "อีก " + m + " นาที";
        return "อีกไม่ถึงนาที";
    }

    public static String late(long ms) {
        long m = Math.round(ms / 60000.0);
        if (m < 60) return m + " นาที";
        long h = m / 60;
        if (h < 24) return h + " ชม. " + (m % 60) + " นาที";
        return (h / 24) + " วัน " + (h % 24) + " ชม.";
    }

    private static final String[] DAY_SHORT = {"อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส."};

    public static String days(int mask) {
        if ((mask & 127) == 127) return "ทุกวัน";
        StringBuilder sb = new StringBuilder("ทุก");
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) sb.append(' ').append(DAY_SHORT[i]);
        return sb.toString();
    }

    public static String typeName(Reminder r) {
        if (Reminder.DAILY.equals(r.type)) return (r.weekdays & 127) == 127 ? "ทุกวัน" : "รายสัปดาห์";
        if (Reminder.MONTHLY.equals(r.type)) return "ทุกเดือน";
        return "ครั้งเดียว";
    }

    public static String sched(Reminder r) {
        if (Reminder.DAILY.equals(r.type)) return days(r.weekdays) + " เวลา " + r.time + " น.";
        if (Reminder.MONTHLY.equals(r.type)) return "ทุกเดือน วันที่ " + r.day + " เวลา " + r.time + " น.";
        return "ครั้งเดียว " + when(Schedule.onceTime(r)) + " น.";
    }
}
