package app.reminder.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = 0xFFF1F4F1, INK = 0xFF16221E, MUTED = 0xFF566660, ACCENT = 0xFF0F6B5C, LINE = 0xFFD6DDD8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 30000); }
    };
    private final List<Reminder> items = new ArrayList<>();
    private ListView listView;
    private TextView nextView, banner;
    private RowAdapter adapter;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.ensureChannel(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(28), dp(16), dp(8));

        root.addView(tv("ตั้งเวลาเตือน", 26, true, INK));
        nextView = tv("", 14, false, ACCENT);
        nextView.setPadding(0, dp(2), 0, dp(10));
        root.addView(nextView);

        banner = tv("", 14, false, 0xFF7A2E0E);
        banner.setBackgroundColor(0xFFFDE8D7);
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.bottomMargin = dp(10);
        root.addView(banner, bl);

        Button add = new Button(this);
        add.setText("+ เพิ่มการเตือน");
        add.setTextColor(Color.WHITE);
        add.setAllCaps(false);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(ACCENT);
        addBg.setCornerRadius(dp(10));
        add.setBackground(addBg);
        add.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        al.bottomMargin = dp(12);
        root.addView(add, al);

        listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
        listView.setDividerHeight(dp(10));
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setItemsCanFocus(false);
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView empty = tv("ยังไม่มีรายการ\nแตะ \"+ เพิ่มการเตือน\" เพื่อเริ่มต้น", 15, false, MUTED);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        listView.setEmptyView(empty);

        LinearLayout bk = new LinearLayout(this);
        bk.setOrientation(LinearLayout.HORIZONTAL);
        bk.setGravity(Gravity.CENTER);
        TextView bExp = tv("สำรองข้อมูล", 14, true, ACCENT);
        TextView bImp = tv("นำเข้าข้อมูล", 14, true, ACCENT);
        bExp.setPadding(dp(16), dp(10), dp(16), dp(10));
        bImp.setPadding(dp(16), dp(10), dp(16), dp(10));
        bExp.setOnClickListener(v -> startBackup());
        bImp.setOnClickListener(v -> startRestore());
        bk.addView(bExp);
        bk.addView(bImp);
        root.addView(bk, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView about = tv("ผู้สร้าง: MawinIoT · โทร 065-5099395 · v.1", 12, false, MUTED);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(10), 0, dp(10));
        about.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:0655099395")));
            } catch (Exception e) {
                // เครื่องที่ไม่มีแอปโทรศัพท์ ไม่ต้องทำอะไร
            }
        });
        root.addView(about, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Alarms.catchUp(this);
        Alarms.scheduleAll(this);
        refresh();
        updateBanner();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateBanner();
    }

    private static final int REQ_BACKUP = 101, REQ_RESTORE = 102;

    private void startBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "reminder-backup.json");
        try { startActivityForResult(i, REQ_BACKUP); }
        catch (Exception e) { toast("เครื่องนี้เปิดหน้าเลือกไฟล์ไม่ได้"); }
    }

    private void startRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, REQ_RESTORE); }
        catch (Exception e) { toast("เครื่องนี้เปิดหน้าเลือกไฟล์ไม่ได้"); }
    }

    private void toast(String m) { android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show(); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (req == REQ_BACKUP) {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                out.write(Store.exportJson(this).getBytes(StandardCharsets.UTF_8));
                toast("สำรองข้อมูลแล้ว " + Store.all(this).size() + " รายการ");
            } catch (Exception e) {
                toast("สำรองข้อมูลไม่สำเร็จ");
            }
        } else if (req == REQ_RESTORE) {
            final String text;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                toast("อ่านไฟล์ไม่ได้");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("นำเข้าข้อมูล?")
                    .setMessage("รายการปัจจุบันทั้งหมดจะถูกแทนที่ด้วยข้อมูลในไฟล์")
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("นำเข้า", (d, w) -> {
                        try {
                            for (Reminder r : Store.all(this)) { Alarms.cancel(this, r); Notifier.cancel(this, r.id); }
                            int n = Store.importJson(this, text);
                            Alarms.scheduleAll(this);
                            refresh();
                            toast("นำเข้าแล้ว " + n + " รายการ");
                        } catch (Exception e) {
                            Alarms.scheduleAll(this);
                            refresh();
                            toast("ไฟล์ไม่ถูกต้อง นำเข้าไม่ได้ (ข้อมูลเดิมไม่เปลี่ยน)");
                        }
                    })
                    .show();
        }
    }

    private void openEdit(int id) {
        Intent i = new Intent(this, EditActivity.class);
        i.putExtra("id", id);
        startActivity(i);
    }

    private void confirmDelete(Reminder r) {
        new AlertDialog.Builder(this)
                .setTitle("ลบรายการนี้?")
                .setMessage(r.title)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("ลบ", (d, w) -> {
                    Alarms.cancel(this, r);
                    Notifier.cancel(this, r.id);
                    Store.delete(this, r.id);
                    refresh();
                })
                .show();
    }

    private static int rank(Reminder r) {
        if (r.done && r.snoozeUntil == 0 && Reminder.ONCE.equals(r.type)) return 2;
        return r.enabled ? 0 : 1;
    }

    private void refresh() {
        final long now = System.currentTimeMillis();
        items.clear();
        items.addAll(Store.all(this));
        Collections.sort(items, new Comparator<Reminder>() {
            @Override public int compare(Reminder a, Reminder b) {
                int ra = rank(a), rb = rank(b);
                if (ra != rb) return Integer.compare(ra, rb);
                long na = Schedule.nextOf(a, now), nb = Schedule.nextOf(b, now);
                if (na < 0) na = Long.MAX_VALUE;
                if (nb < 0) nb = Long.MAX_VALUE;
                return Long.compare(na, nb);
            }
        });
        adapter.notifyDataSetChanged();

        Reminder best = null;
        long bt = Long.MAX_VALUE;
        for (Reminder r : items) {
            long n = Schedule.nextOf(r, now);
            if (n >= 0 && n < bt) { bt = n; best = r; }
        }
        nextView.setText(best == null ? "ยังไม่มีการเตือนที่รออยู่"
                : "ถัดไป: " + best.title + " · " + Fmt.when(bt) + " · " + Fmt.left(bt - now));
    }

    /** แจ้งสิ่งที่ต้องอนุญาตเพื่อให้เตือนได้จริง แตะเพื่อไปหน้าตั้งค่า */
    private void updateBanner() {
        String msg = null;
        Runnable act = null;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            msg = "แตะเพื่ออนุญาตการแจ้งเตือน (ไม่อนุญาตจะไม่มีเสียงเตือน)";
            act = () -> {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            };
        } else if (Build.VERSION.SDK_INT >= 31
                && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            msg = "แตะเพื่ออนุญาต \"สัญญาณปลุกและการเตือน\" ให้เตือนตรงเวลา";
            act = () -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        } else if (!getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())) {
            msg = "แนะนำ: แตะเพื่อตั้งแอปนี้ให้ไม่ถูกจำกัดแบตเตอรี่ จะเตือนเสถียรขึ้น";
            act = () -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
        if (msg == null) {
            banner.setVisibility(View.GONE);
        } else {
            banner.setText(msg);
            final Runnable a = act;
            banner.setOnClickListener(v -> { try { a.run(); } catch (Exception e) { /* บางเครื่องไม่มีหน้านี้ */ } });
            banner.setVisibility(View.VISIBLE);
        }
    }

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return items.get(i).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            final Reminder r = items.get(pos);
            final long now = System.currentTimeMillis();
            final int rk = rank(r);
            Context c = MainActivity.this;

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), LINE);
            row.setBackground(bg);
            if (rk != 0) row.setAlpha(0.6f);

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);

            int typeColor = Reminder.DAILY.equals(r.type) ? 0xFF0F766E : Reminder.MONTHLY.equals(r.type) ? 0xFFB45309 : 0xFF4338CA;
            TextView chip = tv(Fmt.typeName(r), 12, true, typeColor);
            col.addView(chip);
            col.addView(tv(r.title, 17, true, INK));
            if (r.note != null && !r.note.isEmpty()) col.addView(tv(r.note, 14, false, MUTED));
            col.addView(tv(Fmt.sched(r), 13, false, MUTED));

            String nextText;
            if (rk == 2) nextText = "เสร็จแล้ว";
            else if (rk == 1) nextText = "หยุดชั่วคราว";
            else {
                long n = Schedule.nextOf(r, now);
                nextText = n < 0 ? "" : (r.snoozeUntil > 0 ? "เลื่อนไปเตือน " : "ครั้งถัดไป ") + Fmt.when(n) + " · " + Fmt.left(n - now);
            }
            col.addView(tv(nextText, 13, true, typeColor));
            TextView editLink = tv("แตะเพื่อแก้ไข ›", 13, true, ACCENT);
            editLink.setPadding(0, dp(8), 0, dp(4));
            editLink.setOnClickListener(v -> openEdit(r.id));
            col.addView(editLink);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Switch sw = new Switch(c);
            sw.setChecked(r.enabled && rk != 2);
            sw.setEnabled(rk != 2);
            sw.setOnCheckedChangeListener((b, on) -> {
                Reminder x = Store.get(c, r.id);
                if (x == null) return;
                x.enabled = on;
                x.snoozeUntil = 0;
                if (on) x.cursor = System.currentTimeMillis();
                Store.upsert(c, x);
                if (on) Alarms.schedule(c, x); else { Alarms.cancel(c, x); Notifier.cancel(c, x.id); }
                handler.post(MainActivity.this::refresh);
            });
            sw.setFocusable(false);
            row.addView(sw);
            row.setOnClickListener(v -> openEdit(r.id));
            row.setOnLongClickListener(v -> { confirmDelete(r); return true; });
            return row;
        }
    }
}
