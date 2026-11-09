package app.reminder.local;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;

/** เปิดเว็บเซิร์ฟเวอร์ชั่วคราว ให้คอมพิวเตอร์ในวงแลนเดียวกันเพิ่ม/แก้ไข/ลบรายการ ปิดเองเมื่อออกจากหน้านี้ */
public class LanActivity extends Activity {
    private LanServer server;
    private TextView status;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String t, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF1F4F1);
        root.setPadding(dp(20), dp(32), dp(20), dp(16));
        root.addView(tv("เชื่อมต่อคอมพิวเตอร์", 24, true, 0xFF16221E));
        status = tv("", 16, false, 0xFF16221E);
        status.setPadding(0, dp(16), 0, dp(16));
        root.addView(status);
        Button close = new Button(this);
        close.setText("ปิดการเชื่อมต่อ");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        root.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        String pin = String.format("%06d", new SecureRandom().nextInt(1000000));
        server = new LanServer(pin, new LanApi(this));
        try {
            int port = server.start(8080, 8090);
            StringBuilder sb = new StringBuilder("เปิดเบราว์เซอร์บนคอมพิวเตอร์ (ต้องต่อ Wi-Fi/แลนเดียวกับมือถือ) แล้วเข้า:\n\n");
            boolean any = false;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        sb.append("http://").append(a.getHostAddress()).append(':').append(port).append("\n");
                        any = true;
                    }
                }
            }
            if (!any) sb.append("(ไม่พบ IP ในวงแลน ตรวจสอบว่าเชื่อมต่อ Wi-Fi แล้ว)\n");
            sb.append("\nรหัส PIN: ").append(pin).append("\n\nเปิดหน้านี้ค้างไว้ระหว่างใช้งาน ออกจากหน้านี้หรือปิดจอ การเชื่อมต่อจะปิดเอง");
            status.setText(sb.toString());
        } catch (Exception e) {
            status.setText("เปิดการเชื่อมต่อไม่สำเร็จ");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (server != null) { server.stop(); server = null; }
    }
}
