package app.reminder.local;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** ตัวจัดการคำขอจากคอมพิวเตอร์ ใช้ Store/Alarms ชุดเดียวกับแอป */
public final class LanApi implements LanServer.Handler {
    private final Context c;

    public LanApi(Context ctx) { this.c = ctx.getApplicationContext(); }

    @Override
    public LanServer.Response handle(String method, String path, String body) throws Exception {
        if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
            try (InputStream in = c.getAssets().open("lan.html")) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return LanServer.Response.html(bo.toByteArray());
            }
        }
        if ("GET".equals(method) && "/api/list".equals(path)) {
            return LanServer.Response.json(200, Store.exportJson(c));
        }
        if ("POST".equals(method) && "/api/save".equals(path)) return save(new JSONObject(body));
        if ("POST".equals(method) && "/api/delete".equals(path)) {
            int id = new JSONObject(body).optInt("id", 0);
            Reminder r = Store.get(c, id);
            if (r == null) return LanServer.Response.json(404, "{\"error\":\"ไม่พบรายการ\"}");
            Alarms.cancel(c, r);
            Notifier.cancel(c, r.id);
            Store.delete(c, id);
            return LanServer.Response.json(200, "{\"ok\":true}");
        }
        return LanServer.Response.json(404, "{\"error\":\"not found\"}");
    }

    private LanServer.Response save(JSONObject o) throws Exception {
        int id = o.optInt("id", 0);
        Reminder r = null;
        if (id > 0) {
            r = Store.get(c, id);
            if (r == null) return LanServer.Response.json(404, "{\"error\":\"ไม่พบรายการ\"}");
        }
        if (r == null) r = new Reminder();
        String type = o.optString("type", "");
        if (!Reminder.ONCE.equals(type) && !Reminder.DAILY.equals(type) && !Reminder.MONTHLY.equals(type))
            return bad("ชนิดการเตือนไม่ถูกต้อง");
        String time = o.optString("time", "");
        if (!time.matches("([01]\\d|2[0-3]):[0-5]\\d")) return bad("เวลาไม่ถูกต้อง");
        String date = o.optString("date", "");
        if (Reminder.ONCE.equals(type) && !date.matches("\\d{4}-\\d{2}-\\d{2}")) return bad("วันที่ไม่ถูกต้อง");
        r.title = trunc(o.optString("title", "").trim(), 100);
        r.note = trunc(o.optString("note", "").trim(), 300);
        r.type = type;
        r.time = time;
        r.date = Reminder.ONCE.equals(type) ? date : "";
        r.day = Reminder.MONTHLY.equals(type) ? Math.max(1, Math.min(31, o.optInt("day", 1))) : 1;
        r.weekdays = Reminder.DAILY.equals(type) ? (o.optInt("weekdays", 127) & 127) : 127;
        r.enabled = true;
        r.done = false;
        r.snoozeUntil = 0;
        r.cursor = System.currentTimeMillis();
        String e = Schedule.validate(r, r.cursor);
        if (e != null) return bad(e);
        Store.upsert(c, r);
        Notifier.cancel(c, r.id);
        Alarms.schedule(c, r);
        return LanServer.Response.json(200, "{\"ok\":true,\"id\":" + r.id + "}");
    }

    private static LanServer.Response bad(String msg) throws Exception {
        return LanServer.Response.json(400, new JSONObject().put("error", msg).toString());
    }

    private static String trunc(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
}
