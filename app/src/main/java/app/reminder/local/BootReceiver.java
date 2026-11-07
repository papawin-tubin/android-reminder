package app.reminder.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** เปิดเครื่อง / อัปเดตแอป / เปลี่ยนเวลาหรือเขตเวลา / ได้สิทธิ์ alarm แบบเป๊ะ: เตือนที่พลาดแล้วตั้ง alarm ใหม่ทั้งหมด */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Alarms.catchUp(c);
        Alarms.scheduleAll(c);
    }
}
