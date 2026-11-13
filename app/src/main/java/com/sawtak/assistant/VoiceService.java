package com.sawtak.assistant;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import android.net.Uri;
import java.util.*;

public class VoiceService extends Service {
    private SpeechRecognizer recognizer;
    private Intent speechIntent;
    private boolean active = false, awaitingCommand = false, torchOn = false;
    private AudioManager audio;
    private CameraManager camera;
    private String cameraId;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        camera = (CameraManager)getSystemService(CAMERA_SERVICE);
        try { for (String id : camera.getCameraIdList()) if (camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) != null && Boolean.TRUE.equals(camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE))) { cameraId=id; break; } } catch(Exception ignored) {}
        createChannel();
        Notification n = new Notification.Builder(this, "voice")
            .setContentTitle("صوتك يعمل").setContentText("قل: يا مساعد لبدء الأمر")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
        startForeground(7, n);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("voice", "المساعد الصوتي", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY; }
        active = true;
        beginRecognition();
        return START_STICKY;
    }

    private void beginRecognition() {
        if (!active) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { speak("خدمة التعرف على الكلام غير متاحة على الجهاز"); return; }
        if (recognizer != null) { try { recognizer.destroy(); } catch(Exception ignored){} }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float v) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() {}
            public void onError(int e) { restart(); }
            public void onResults(Bundle b) { handleResults(b); restart(); }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
        });
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try { recognizer.startListening(speechIntent); } catch(Exception e) { restart(); }
    }

    private void restart() {
        if (active) handler.postDelayed(this::beginRecognition, 650);
    }

    private void handleResults(Bundle b) {
        if (b == null) return;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return;
        String s = list.get(0).toLowerCase(Locale.ROOT).trim();
        s = s.replace("،", " ").replace("؟", "");
        if (s.contains("يا مساعد") || s.contains("يامساعد")) {
            String command = s.replace("يا مساعد", "").replace("يامساعد", "").trim();
            if (command.isEmpty()) { awaitingCommand = true; speak("أيوه، سامعك. قول عايز إيه"); }
            else { awaitingCommand = false; execute(command); }
        } else if (awaitingCommand && !s.isEmpty()) {
            awaitingCommand = false; execute(s);
        }
    }

    private void execute(String c) {
        if (c.contains("افتح") || c.startsWith("شغل") || c.startsWith("شغّل")) {
            if (c.contains("واتساب")) openApp("com.whatsapp", "واتساب");
            else if (c.contains("يوتيوب")) openApp("com.google.android.youtube", "يوتيوب");
            else if (c.contains("كاميرا")) launch(new Intent("android.media.action.IMAGE_CAPTURE"), "الكاميرا");
            else if (c.contains("الإعدادات") || c.contains("الاعدادات")) launch(new Intent(android.provider.Settings.ACTION_SETTINGS), "الإعدادات");
            else if (c.contains("البلوتوث")) launch(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), "إعدادات البلوتوث");
            else if (c.contains("الواي فاي") || c.contains("واي فاي")) launch(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS), "إعدادات الواي فاي");
            else if (c.contains("الكشاف") || c.contains("الفلاش")) toggleTorch(true);
            else speak("لسه مش عارف التطبيق ده. قول اسم تطبيق موجود على الموبايل");
        } else if (c.contains("اقفل") || c.contains("أوقف") || c.contains("وقف")) {
            if (c.contains("الكشاف") || c.contains("الفلاش")) toggleTorch(false);
            else speak("أندرويد مش بيسمحلي أقفل التطبيقات التانية بالقوة، تقدر ترجع للشاشة الرئيسية بنفسك");
        } else if (c.contains("ارفع الصوت") || c.contains("علي الصوت") || c.contains("زوّد الصوت")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); speak("عليت الصوت");
        } else if (c.contains("وطي الصوت") || c.contains("اخفض الصوت") || c.contains("قلل الصوت")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); speak("وطيّت الصوت");
        } else if (c.contains("اكتم الصوت") || c.contains("صامت")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); speak("كتمت صوت الميديا");
        } else if (c.contains("البطارية") || c.contains("الشحن")) {
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = bi == null ? -1 : bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            speak(level >= 0 ? "نسبة البطارية " + level + " في المية" : "مش قادر أقرأ البطارية");
        } else if (c.contains("الوقت") || c.contains("الساعة")) {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("h:mm a", new Locale("ar","EG"));
            speak("الساعة دلوقتي " + df.format(new Date()));
        } else if (c.contains("اتصل")) {
            String number = c.replaceAll("[^0-9+]", "");
            if (number.length() >= 6) {
                Intent call = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
                call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(call);
                speak("فتحت شاشة الاتصال، راجع الرقم واضغط اتصال");
            } else speak("قول رقم التليفون بعد كلمة اتصل");
        } else if (c.contains("ابحث") || c.contains("دور على")) {
            String q = c.replace("ابحث عن", "").replace("ابحث", "").replace("دور على", "").trim();
            Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
            search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(search); speak("دي نتائج البحث");
        } else if (c.contains("اقفل المساعد")) {
            speak("حاضر، هوقف الاستماع"); stopSelf();
        } else {
            speak("مفهمتش الأمر. جرب تقول افتح واتساب أو ارفع الصوت أو شغل الكشاف");
        }
    }

    private void openApp(String pkg, String label) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { speak(label + " مش متثبت عندك"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); speak("بفتح " + label);
    }
    private void launch(Intent i, String label) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); speak("بفتح " + label); }
        catch(Exception e) { speak("مش قادر أفتح " + label); }
    }
    private void toggleTorch(boolean on) {
        if (cameraId == null) { speak("الكشاف مش متاح"); return; }
        try { camera.setTorchMode(cameraId, on); torchOn=on; speak(on ? "شغلت الكشاف" : "طفيت الكشاف"); }
        catch(Exception e) { speak("مش قادر أتحكم في الكشاف دلوقتي"); }
    }
    private void speak(String t) {
        Intent i = new Intent(this, SpeechOutputService.class);
        i.putExtra("text", t);
        startService(i);
    }

    @Override public void onDestroy() {
        active=false; handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { try { recognizer.cancel(); recognizer.destroy(); } catch(Exception ignored){} }
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent i) { return null; }
}
