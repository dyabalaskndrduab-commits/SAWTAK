package com.sawtak.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {
    private static final int REQ = 41;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(10, 14, 25));
        getWindow().setNavigationBarColor(Color.rgb(10, 14, 25));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(34, 48, 34, 28);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(Color.rgb(10, 14, 25));

        TextView title = new TextView(this);
        title.setText("صــوتــك");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        page.addView(title);

        TextView sub = new TextView(this);
        sub.setText("مساعدك الشخصي للتحكم في الهاتف بالصوت");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 14; sp.bottomMargin = 24;
        page.addView(sub, sp);

        TextView online = new TextView(this);
        online.setText("وضع التعرف المتصل — العربية المصرية\nيحتاج إنترنت وتفعيل خدمة التعرف الصوتي على الهاتف");
        online.setTextColor(Color.rgb(115, 220, 190));
        online.setTextSize(14);
        online.setGravity(Gravity.CENTER);
        page.addView(online);

        status = new TextView(this);
        status.setText("الحالة: متوقف\nقل «يا مساعد» بعد تشغيل الاستماع");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(-1, -2);
        st.topMargin = 22;
        page.addView(status, st);

        Button start = new Button(this);
        start.setText("تشغيل الاستماع الصوتي");
        start.setOnClickListener(v -> requestAndStart());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 28;
        page.addView(start, bp);

        Button stop = new Button(this);
        stop.setText("إيقاف المساعد");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, VoiceService.class));
            status.setText("الحالة: متوقف");
        });
        page.addView(stop, bp);

        TextView examples = new TextView(this);
        examples.setText("\nجرّب قول:\n• يا مساعد افتح واتساب\n• افتح يوتيوب\n• شغّل النور / اطفي الكشاف\n• علي الصوت / وطي الصوت\n• كام البطارية؟\n• الساعة كام؟\n• ابحث عن أخبار اليوم");
        examples.setTextColor(Color.WHITE);
        examples.setTextSize(15);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = 20;
        page.addView(examples, ep);
        setContentView(page);
    }

    private void requestAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS}, REQ);
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, REQ);
            }
            return;
        }
        startVoice();
    }

    private void startVoice() {
        Intent i = new Intent(this, VoiceService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("الحالة: يستمع — متصل بالإنترنت — قل «يا مساعد»");
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r == REQ) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice();
            else status.setText("يلزم السماح باستخدام الميكروفون لتشغيل المساعد.");
        }
    }
}
