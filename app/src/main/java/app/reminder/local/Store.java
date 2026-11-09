package app.reminder.local;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** เก็บรายการเป็นไฟล์ JSON ในพื้นที่ส่วนตัวของแอป (ไม่ออกเน็ต ไม่ต้องขอสิทธิ์) */
public final class Store {
    private static final String FILE = "reminders.json";
    private static int nextId = 1;

    private Store() {}

    private static List<Reminder> load(Context c) {
        List<Reminder> list = new ArrayList<>();
        File f = new File(c.getFilesDir(), FILE);
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject root = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            nextId = root.optInt("nextId", 1);
            JSONArray a = root.optJSONArray("items");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Reminder r = new Reminder();
                    r.id = o.getInt("id");
                    r.title = o.optString("title", "");
                    r.note = o.optString("note", "");
                    r.type = o.optString("type", Reminder.DAILY);
                    r.time = o.optString("time", "08:00");
                    r.date = o.optString("date", "");
                    r.day = o.optInt("day", 1);
                    r.weekdays = o.optInt("weekdays", 127);
                    r.enabled = o.optBoolean("enabled", true);
                    r.done = o.optBoolean("done", false);
                    r.cursor = o.optLong("cursor", 0);
                    r.snoozeUntil = o.optLong("snoozeUntil", 0);
                    list.add(r);
                }
            }
        } catch (FileNotFoundException e) {
            // ยังไม่มีไฟล์ = ยังไม่มีรายการ
        } catch (Exception e) {
            // ไฟล์เสีย: เริ่มใหม่ (ไม่ให้แอปล่ม)
        }
        return list;
    }

    private static void save(Context c, List<Reminder> list) {
        try {
            JSONArray a = new JSONArray();
            for (Reminder r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("title", r.title);
                o.put("note", r.note);
                o.put("type", r.type);
                o.put("time", r.time);
                o.put("date", r.date);
                o.put("day", r.day);
                o.put("weekdays", r.weekdays);
                o.put("enabled", r.enabled);
                o.put("done", r.done);
                o.put("cursor", r.cursor);
                o.put("snoozeUntil", r.snoozeUntil);
                a.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("nextId", nextId);
            root.put("items", a);
            File tmp = new File(c.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            File dst = new File(c.getFilesDir(), FILE);
            if (!tmp.renameTo(dst)) throw new RuntimeException("rename failed");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized List<Reminder> all(Context c) {
        return load(c);
    }

    public static synchronized Reminder get(Context c, int id) {
        for (Reminder r : load(c)) if (r.id == id) return r;
        return null;
    }

    /** เพิ่มหรือแทนที่ตาม id (id = 0 คือรายการใหม่ จะได้เลขใหม่) */
    public static synchronized Reminder upsert(Context c, Reminder r) {
        List<Reminder> list = load(c);
        if (r.id == 0) {
            r.id = nextId++;
            list.add(r);
        } else {
            boolean found = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == r.id) { list.set(i, r); found = true; break; }
            }
            if (!found) list.add(r);
        }
        save(c, list);
        return r;
    }

    public static synchronized void delete(Context c, int id) {
        List<Reminder> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) { list.remove(i); break; }
        }
        save(c, list);
    }
    /** ข้อความ JSON สำหรับสำรองข้อมูล */
    public static synchronized String exportJson(Context c) {
        List<Reminder> list = load(c);
        save(c, list);
        try (InputStream in = new FileInputStream(new File(c.getFilesDir(), FILE))) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** นำเข้าแทนที่ข้อมูลทั้งหมด คืนจำนวนรายการ ถ้าไฟล์ไม่ถูกต้องจะโยน Exception โดยไม่แตะข้อมูลเดิม */
    public static synchronized int importJson(Context c, String text) throws Exception {
        JSONObject root = new JSONObject(text);
        JSONArray a = root.getJSONArray("items");
        int max = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String type = o.optString("type", "");
            if (!Reminder.ONCE.equals(type) && !Reminder.DAILY.equals(type) && !Reminder.MONTHLY.equals(type))
                throw new IllegalArgumentException("ชนิดรายการไม่ถูกต้อง");
            max = Math.max(max, o.getInt("id"));
        }
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(new File(c.getFilesDir(), FILE))) throw new RuntimeException("rename failed");
        nextId = Math.max(root.optInt("nextId", 1), max + 1);
        List<Reminder> list = load(c);
        save(c, list);
        return list.size();
    }
}
