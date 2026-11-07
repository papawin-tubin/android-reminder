import app.reminder.local.Reminder;
import app.reminder.local.Schedule;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** ทดสอบตรรกะเวลาบน JVM (ไม่ต้องใช้ Android): ดูวิธีรันใน README */
public class ScheduleCheck {
    static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    static void eq(long a, long b, String m) { if (a != b) throw new AssertionError(m + " expected " + b + " got " + a); }
    static void ok(boolean c, String m) { if (!c) throw new AssertionError(m); }

    static Reminder mk(String type, String time, String date, int day) {
        Reminder r = new Reminder();
        r.title = "x"; r.type = type; r.time = time; r.date = date; r.day = day;
        return r;
    }

    public static void main(String[] a) {
        Reminder daily = mk("daily", "08:00", "", 1);
        eq(Schedule.nextOcc(daily, at(2026, 11, 7, 7, 59)), at(2026, 11, 7, 8, 0), "daily before");
        eq(Schedule.nextOcc(daily, at(2026, 11, 7, 8, 0)), at(2026, 11, 8, 8, 0), "daily exact -> tomorrow");
        eq(Schedule.nextOcc(daily, at(2026, 12, 31, 9, 0)), at(2027, 1, 1, 8, 0), "daily new year");


        // ทุกวัน เฉพาะบางวันในสัปดาห์ (บิต0=อาทิตย์ ... บิต6=เสาร์) ; 2026-11-07 เป็นวันเสาร์
        Reminder mwf = mk("daily", "08:00", "", 1); mwf.weekdays = (1 << 1) | (1 << 3) | (1 << 5); // จ. พ. ศ.
        eq(Schedule.nextOcc(mwf, at(2026, 11, 7, 9, 0)), at(2026, 11, 9, 8, 0), "sat -> mon");
        eq(Schedule.nextOcc(mwf, at(2026, 11, 9, 7, 0)), at(2026, 11, 9, 8, 0), "mon before time");
        eq(Schedule.nextOcc(mwf, at(2026, 11, 9, 8, 0)), at(2026, 11, 11, 8, 0), "mon exact -> wed");
        eq(Schedule.nextOcc(mwf, at(2026, 11, 13, 9, 0)), at(2026, 11, 16, 8, 0), "fri after -> mon");
        Reminder sun = mk("daily", "08:00", "", 1); sun.weekdays = 1; // อาทิตย์อย่างเดียว
        eq(Schedule.nextOcc(sun, at(2026, 11, 7, 9, 0)), at(2026, 11, 8, 8, 0), "sat -> sun");
        eq(Schedule.nextOcc(sun, at(2026, 11, 8, 8, 0)), at(2026, 11, 15, 8, 0), "sun exact -> next sun");
        Reminder sat = mk("daily", "08:00", "", 1); sat.weekdays = 1 << 6; // เสาร์อย่างเดียว
        eq(Schedule.nextOcc(sat, at(2026, 11, 7, 7, 0)), at(2026, 11, 7, 8, 0), "sat today");
        Reminder none = mk("daily", "08:00", "", 1); none.weekdays = 0;
        eq(Schedule.nextOcc(none, at(2026, 11, 7, 7, 0)), -1, "no weekday");
        ok(Schedule.validate(none, at(2026, 11, 7, 12, 0)) != null, "weekdays required");
        ok(Schedule.validate(mwf, at(2026, 11, 7, 12, 0)) == null, "weekdays ok");

        Reminder m5 = mk("monthly", "09:00", "", 5);
        eq(Schedule.nextOcc(m5, at(2026, 11, 7, 10, 0)), at(2026, 12, 5, 9, 0), "monthly next month");
        eq(Schedule.nextOcc(m5, at(2026, 11, 5, 8, 0)), at(2026, 11, 5, 9, 0), "monthly same day");
        eq(Schedule.nextOcc(m5, at(2026, 12, 20, 0, 0)), at(2027, 1, 5, 9, 0), "monthly new year");

        Reminder m31 = mk("monthly", "10:00", "", 31);
        eq(Schedule.nextOcc(m31, at(2026, 1, 31, 11, 0)), at(2026, 2, 28, 10, 0), "feb 28");
        eq(Schedule.nextOcc(m31, at(2028, 1, 31, 11, 0)), at(2028, 2, 29, 10, 0), "feb 29 leap");
        eq(Schedule.nextOcc(m31, at(2026, 4, 5, 0, 0)), at(2026, 4, 30, 10, 0), "apr 30");
        eq(Schedule.nextOcc(m31, at(2026, 4, 30, 10, 0)), at(2026, 5, 31, 10, 0), "after apr 30");

        Reminder once = mk("once", "14:00", "2026-11-08", 1);
        eq(Schedule.nextOcc(once, at(2026, 11, 7, 0, 0)), at(2026, 11, 8, 14, 0), "once future");
        eq(Schedule.nextOcc(once, at(2026, 11, 8, 14, 0)), -1, "once past");

        Reminder off = mk("daily", "08:00", "", 1); off.enabled = false;
        eq(Schedule.nextOf(off, 0), -1, "disabled");
        Reminder sn = mk("daily", "08:00", "", 1); sn.snoozeUntil = 12345;
        eq(Schedule.nextOf(sn, 0), 12345, "snoozed");
        Reminder dn = mk("once", "14:00", "2026-11-08", 1); dn.done = true;
        eq(Schedule.nextOf(dn, 0), -1, "done once");

        long now = at(2026, 11, 7, 12, 0);
        ok(Schedule.validate(mk("daily", "08:00", "", 1), now) == null, "valid daily");
        Reminder empty = mk("daily", "08:00", "", 1); empty.title = " ";
        ok(Schedule.validate(empty, now) != null, "empty title");
        ok(Schedule.validate(mk("daily", "24:00", "", 1), now) != null, "bad time");
        ok(Schedule.validate(mk("weekly", "08:00", "", 1), now) != null, "bad type");
        ok(Schedule.validate(mk("once", "11:00", "2026-11-07", 1), now) != null, "once in past");
        ok(Schedule.validate(mk("once", "12:01", "2026-11-07", 1), now) == null, "once future");
        ok(Schedule.validate(mk("once", "10:00", "2026-02-30", 1), now) != null, "no such date");
        ok(Schedule.validate(mk("monthly", "08:00", "", 32), now) != null, "bad day");
        System.out.println("Schedule checks passed");
    }
}
