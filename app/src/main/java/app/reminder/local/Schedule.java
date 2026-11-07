package app.reminder.local;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/** ตรรกะเวลา ครั้งเดียว / ทุกวัน / ทุกเดือน (เดือนสั้นใช้วันสุดท้ายของเดือน) */
public final class Schedule {
    private Schedule() {}

    private static long at(LocalDate d, LocalTime t) {
        return ZonedDateTime.of(d, t, ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long onceTime(Reminder r) {
        return at(LocalDate.parse(r.date), LocalTime.parse(r.time));
    }

    /** เวลาเตือนครั้งถัดไป "หลัง" after (ms) ; -1 = ไม่มีอีกแล้ว */
    public static long nextOcc(Reminder r, long after) {
        if (Reminder.ONCE.equals(r.type)) {
            long t = onceTime(r);
            return t > after ? t : -1;
        }
        LocalTime lt = LocalTime.parse(r.time);
        LocalDate today = Instant.ofEpochMilli(after).atZone(ZoneId.systemDefault()).toLocalDate();
        if (Reminder.DAILY.equals(r.type)) {
            for (int i = 0; i < 8; i++) { // เลือกเฉพาะบางวันในสัปดาห์ได้
                LocalDate d = today.plusDays(i);
                if ((r.weekdays & (1 << (d.getDayOfWeek().getValue() % 7))) == 0) continue; // อาทิตย์ = 7 % 7 = 0
                long t = at(d, lt);
                if (t > after) return t;
            }
            return -1;
        }
        YearMonth ym = YearMonth.from(today);
        for (int i = 0; i < 3; i++) {
            YearMonth m = ym.plusMonths(i);
            long t = at(m.atDay(Math.min(r.day, m.lengthOfMonth())), lt);
            if (t > after) return t;
        }
        return -1;
    }

    /** เวลาที่จะเตือนต่อไปของรายการนี้ (คำนึงถึงสถานะเลื่อน/หยุด/เสร็จ) ; -1 = ไม่มี */
    public static long nextOf(Reminder r, long now) {
        if (!r.enabled) return -1;
        if (r.snoozeUntil > 0) return r.snoozeUntil;
        if (Reminder.ONCE.equals(r.type)) return r.done ? -1 : onceTime(r);
        return nextOcc(r, now);
    }

    /** คืนข้อความผิดพลาด หรือ null ถ้าถูกต้อง */
    public static String validate(Reminder r, long now) {
        if (r.title == null || r.title.trim().isEmpty()) return "กรอกชื่อรายการ";
        if (r.title.length() > 80) return "ชื่อยาวเกิน 80 ตัวอักษร";
        if (!Reminder.ONCE.equals(r.type) && !Reminder.DAILY.equals(r.type) && !Reminder.MONTHLY.equals(r.type))
            return "ประเภทไม่ถูกต้อง";
        try {
            LocalTime.parse(r.time);
            if (Reminder.ONCE.equals(r.type)) {
                if (onceTime(r) <= now) return "เวลานี้ผ่านไปแล้ว เลือกวันหรือเวลาในอนาคต";
            }
        } catch (DateTimeParseException e) {
            return "วันหรือเวลาไม่ถูกต้อง";
        }
        if (Reminder.MONTHLY.equals(r.type) && (r.day < 1 || r.day > 31)) return "วันที่ของเดือนต้องเป็น 1-31";
        if (Reminder.DAILY.equals(r.type) && (r.weekdays & 127) == 0) return "เลือกอย่างน้อย 1 วัน";
        return null;
    }
}
