package app.reminder.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/** ฟอร์มเพิ่ม/แก้ไขการเตือน */
public class EditActivity extends Activity {
    private static final int BG = 0xFFF1F4F1, INK = 0xFF16221E, MUTED = 0xFF566660, ACCENT = 0xFF0F6B5C, DANGER = 0xFFB42318;

    private Reminder existing;
    private EditText titleIn, noteIn;
    private RadioButton rbOnce, rbDaily, rbMonthly;
    private Button dateBtn, timeBtn;
    private Spinner daySp;
    private LinearLayout dateBox, dayBox;
    private TextView hint, err;
    private int year, month, dom, hour, minute;
    private final boolean[] sel = new boolean[7];
    private final TextView[] chips = new TextView[7];
    private LinearLayout weekBox;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private Button button(String text, int fill, int textColor, boolean outlined) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        if (outlined) { g.setColor(Color.WHITE); g.setStroke(dp(1), textColor); } else g.setColor(fill);
        b.setBackground(g);
        return b;
    }

    private LinearLayout.LayoutParams lp(int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        p.topMargin = dp(8);
        return p;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra("id", 0);
        existing = id != 0 ? Store.get(this, id) : null;

        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.HOUR_OF_DAY, 1);
        year = cal.get(Calendar.YEAR); month = cal.get(Calendar.MONTH); dom = cal.get(Calendar.DAY_OF_MONTH);
        hour = cal.get(Calendar.HOUR_OF_DAY); minute = 0;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(24));
        scroll.addView(root);

        TextView head = new TextView(this);
        head.setText(existing == null ? "เพิ่มการเตือน" : "แก้ไขการเตือน");
        head.setTextSize(24);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(INK);
        root.addView(head);

        root.addView(label("ชื่อรายการ"));
        titleIn = new EditText(this);
        titleIn.setHint("เช่น จ่ายค่าไฟ");
        titleIn.setSingleLine(true);
        titleIn.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        root.addView(titleIn);

        root.addView(label("บันทึกเพิ่มเติม (ไม่บังคับ)"));
        noteIn = new EditText(this);
        noteIn.setMinLines(2);
        noteIn.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        root.addView(noteIn);

        root.addView(label("การเตือน"));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rbOnce = new RadioButton(this); rbOnce.setText("ครั้งเดียว"); rbOnce.setId(View.generateViewId());
        rbDaily = new RadioButton(this); rbDaily.setText("ทุกวัน"); rbDaily.setId(View.generateViewId());
        rbMonthly = new RadioButton(this); rbMonthly.setText("ทุกเดือน"); rbMonthly.setId(View.generateViewId());
        rg.addView(rbOnce); rg.addView(rbDaily); rg.addView(rbMonthly);
        rg.setOnCheckedChangeListener((g, checked) -> syncFields());
        root.addView(rg);

        Arrays.fill(sel, true);
        weekBox = new LinearLayout(this);
        weekBox.setOrientation(LinearLayout.VERTICAL);
        weekBox.addView(label("วันที่ต้องการให้เตือน (แตะเพื่อเลือก/ยกเลิก)"));
        LinearLayout weekRow = new LinearLayout(this);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] dn = {"อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส."};
        for (int i = 0; i < 7; i++) {
            final int k = i;
            TextView t = new TextView(this);
            t.setText(dn[i]);
            t.setTextSize(14);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(android.view.Gravity.CENTER);
            t.setOnClickListener(v -> { sel[k] = !sel[k]; styleChip(k); });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            cp.setMargins(dp(2), 0, dp(2), 0);
            weekRow.addView(t, cp);
            chips[i] = t;
        }
        weekBox.addView(weekRow);
        root.addView(weekBox);

        dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        dateBox.addView(label("วันที่"));
        dateBtn = button("", 0, INK, true);
        dateBtn.setOnClickListener(v -> {
            DatePickerDialog d = new DatePickerDialog(this, (p, y, m, dd) -> { year = y; month = m; dom = dd; updateButtons(); }, year, month, dom);
            d.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            d.show();
        });
        dateBox.addView(dateBtn, lp(dp(48)));
        root.addView(dateBox);

        dayBox = new LinearLayout(this);
        dayBox.setOrientation(LinearLayout.VERTICAL);
        dayBox.addView(label("วันที่ของเดือน"));
        daySp = new Spinner(this);
        String[] days = new String[31];
        for (int i = 0; i < 31; i++) days[i] = String.valueOf(i + 1);
        daySp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, days));
        dayBox.addView(daySp);
        root.addView(dayBox);

        root.addView(label("เวลา"));
        timeBtn = button("", 0, INK, true);
        timeBtn.setOnClickListener(v -> new TimePickerDialog(this, (p, h, m) -> { hour = h; minute = m; updateButtons(); }, hour, minute, true).show());
        root.addView(timeBtn, lp(dp(48)));

        hint = new TextView(this);
        hint.setTextSize(13);
        hint.setTextColor(MUTED);
        hint.setPadding(0, dp(8), 0, 0);
        root.addView(hint);

        err = new TextView(this);
        err.setTextSize(14);
        err.setTextColor(DANGER);
        err.setPadding(0, dp(8), 0, 0);
        root.addView(err);

        Button save = button(existing == null ? "เพิ่มการเตือน" : "บันทึก", ACCENT, Color.WHITE, false);
        save.setOnClickListener(v -> save());
        root.addView(save, lp(dp(50)));

        if (existing != null) {
            Button del = button("ลบรายการนี้", 0, DANGER, true);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("ลบรายการนี้?")
                    .setMessage(existing.title)
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("ลบ", (d, w) -> {
                        Alarms.cancel(this, existing);
                        Notifier.cancel(this, existing.id);
                        Store.delete(this, existing.id);
                        finish();
                    }).show());
            root.addView(del, lp(dp(50)));
        }
        Button cancel = button("ยกเลิก", 0, MUTED, true);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, lp(dp(50)));

        setContentView(scroll);

        if (existing != null) {
            titleIn.setText(existing.title);
            noteIn.setText(existing.note);
            String[] hm = existing.time.split(":");
            hour = Integer.parseInt(hm[0]); minute = Integer.parseInt(hm[1]);
            if (Reminder.ONCE.equals(existing.type) && existing.date.length() == 10) {
                String[] d = existing.date.split("-");
                year = Integer.parseInt(d[0]); month = Integer.parseInt(d[1]) - 1; dom = Integer.parseInt(d[2]);
            }
            daySp.setSelection(Math.max(0, Math.min(30, existing.day - 1)));
            for (int i = 0; i < 7; i++) sel[i] = (existing.weekdays & (1 << i)) != 0;
            if (Reminder.DAILY.equals(existing.type)) rbDaily.setChecked(true);
            else if (Reminder.MONTHLY.equals(existing.type)) rbMonthly.setChecked(true);
            else rbOnce.setChecked(true);
        } else {
            daySp.setSelection(Math.max(0, dom - 1));
            rbOnce.setChecked(true);
        }
        for (int i = 0; i < 7; i++) styleChip(i);
        updateButtons();
        syncFields();
    }

    private void styleChip(int i) {
        TextView t = chips[i];
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(8));
        if (sel[i]) { g.setColor(ACCENT); t.setTextColor(Color.WHITE); }
        else { g.setColor(Color.WHITE); g.setStroke(dp(1), MUTED); t.setTextColor(INK); }
        t.setBackground(g);
    }

    private String currentType() {
        return rbDaily.isChecked() ? Reminder.DAILY : rbMonthly.isChecked() ? Reminder.MONTHLY : Reminder.ONCE;
    }

    private void updateButtons() {
        dateBtn.setText(String.format(Locale.US, "%02d/%02d/%d", dom, month + 1, year + 543));
        timeBtn.setText(String.format(Locale.US, "%02d:%02d น.", hour, minute));
    }

    private void syncFields() {
        String t = currentType();
        dateBox.setVisibility(Reminder.ONCE.equals(t) ? View.VISIBLE : View.GONE);
        dayBox.setVisibility(Reminder.MONTHLY.equals(t) ? View.VISIBLE : View.GONE);
        weekBox.setVisibility(Reminder.DAILY.equals(t) ? View.VISIBLE : View.GONE);
        hint.setText(Reminder.DAILY.equals(t) ? "เลือกครบทุกวันจะเตือนทุกวัน หรือเลือกเฉพาะบางวันก็ได้"
                : Reminder.MONTHLY.equals(t) ? "เดือนที่มีวันน้อยกว่าที่เลือก จะเตือนวันสุดท้ายของเดือนนั้น" : "");
    }

    private void save() {
        long now = System.currentTimeMillis();
        Reminder r = existing != null ? existing : new Reminder();
        r.title = titleIn.getText().toString().trim();
        r.note = noteIn.getText().toString().trim();
        r.type = currentType();
        r.time = String.format(Locale.US, "%02d:%02d", hour, minute);
        r.date = Reminder.ONCE.equals(r.type) ? String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dom) : "";
        r.day = Reminder.MONTHLY.equals(r.type) ? daySp.getSelectedItemPosition() + 1 : 1;
        int mask = 0;
        for (int i = 0; i < 7; i++) if (sel[i]) mask |= 1 << i;
        r.weekdays = Reminder.DAILY.equals(r.type) ? mask : 127;
        r.enabled = true;
        r.done = false;
        r.snoozeUntil = 0;
        r.cursor = now;
        String e = Schedule.validate(r, now);
        if (e != null) { err.setText(e); return; }
        Store.upsert(this, r);
        Notifier.cancel(this, r.id);
        Alarms.schedule(this, r);
        finish();
    }
}
