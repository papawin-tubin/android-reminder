package app.reminder.local;

/** รายการเตือนหนึ่งรายการ (POJO ล้วน ไม่พึ่ง Android เพื่อให้ทดสอบบน JVM ได้) */
public class Reminder {
    public static final String ONCE = "once";
    public static final String DAILY = "daily";
    public static final String MONTHLY = "monthly";

    public int id;
    public String title = "";
    public String note = "";
    public String type = DAILY;
    public String time = "08:00";   // HH:mm
    public String date = "";        // yyyy-MM-dd (เฉพาะครั้งเดียว)
    public int day = 1;             // วันที่ของเดือน 1-31 (เฉพาะทุกเดือน)
    public int weekdays = 127;      // เฉพาะทุกวัน: บิต0=อาทิตย์ ... บิต6=เสาร์ (127 = ทุกวัน)
    public boolean enabled = true;
    public boolean done = false;    // ครั้งเดียวที่เตือนไปแล้ว
    public long cursor = 0;         // เวลาที่ตรวจล่าสุด กันเตือนซ้ำและใช้จับรายการที่พลาด
    public long snoozeUntil = 0;    // 0 = ไม่ได้เลื่อน
}
