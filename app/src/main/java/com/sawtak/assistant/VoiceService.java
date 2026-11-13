package com.sawtak.assistant;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.speech.*;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;

public class VoiceService extends Service {
    private static final String CHANNEL = "voice";
    private static final String ACTION_SPEECH_DONE = "com.sawtak.assistant.SPEECH_DONE";
    private SpeechRecognizer recognizer;
    private AudioManager audio;
    private CameraManager camera;
    private String cameraId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active = false, awaitingCommand = false, speaking = false, listening = false, receiverRegistered = false;
    private int errorCount = 0;
    private final BroadcastReceiver speechReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            speaking = false;
            if (active) scheduleRestart(300);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        camera = (CameraManager)getSystemService(CAMERA_SERVICE);
        try {
            for (String id : camera.getCameraIdList()) {
                Boolean flash = camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { cameraId = id; break; }
            }
        } catch (Exception ignored) {}
        IntentFilter doneFilter = new IntentFilter(ACTION_SPEECH_DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(speechReceiver, doneFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(speechReceiver, doneFilter);
        receiverRegistered = true;
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
            .setContentTitle("صوتك — الاستماع الذكي")
            .setContentText("التعرف على الكلام المتصل مفعّل")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
        startForeground(7, n);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "المساعد الصوتي", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY;
        }
        active = true;
        beginRecognition();
        return START_STICKY;
    }

    private void beginRecognition() {
        if (!active || speaking || listening) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("خدمة التعرف الصوتي غير متاحة");
            scheduleRestart(5000);
            return;
        }
        try {
            releaseRecognizer();
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { errorCount = 0; updateNotification("جاهز — اتكلم بالمصري"); }
                @Override public void onBeginningOfSpeech() { updateNotification("بسمعك..."); }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { listening = false; }
                @Override public void onError(int error) {
                    listening = false;
                    if (!active || speaking) return;
                    errorCount++;
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        updateNotification("إذن الميكروفون غير متاح"); stopSelf(); return;
                    }
                    long delay = Math.min(5000, 700L + (errorCount * 450L));
                    scheduleRestart(delay);
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    errorCount = 0;
                    handleResults(results);
                    if (active && !speaking) scheduleRestart(250);
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    // Partial hypotheses are intentionally not executed: wait for a final result
                    // so a sentence is not cut off and an action is not triggered twice.
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG");
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 450L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
            listening = true;
            recognizer.startListening(intent);
        } catch (Exception e) {
            listening = false;
            scheduleRestart(1800);
        }
    }

    private void updateNotification(String message) {
        Notification n = new Notification.Builder(this, CHANNEL)
            .setContentTitle("صوتك — الاستماع الذكي")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.notify(7, n);
    }

    private void releaseRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        listening = false;
    }

    private void scheduleRestart(long delay) {
        handler.removeCallbacks(restartRunnable);
        if (active && !speaking) handler.postDelayed(restartRunnable, delay);
    }
    private final Runnable restartRunnable = new Runnable() {
        @Override public void run() { beginRecognition(); }
    };

    private String normalize(String value) {
        String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي');
        s = s.replace("ة","ه").replace("،"," ").replace("؟"," ").replace("ـ","");
        return s.toLowerCase(new Locale("ar","EG")).trim().replaceAll("\\s+"," ");
    }

    private void handleResults(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> candidates = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (candidates == null || candidates.isEmpty()) return;
        String chosen = null;
        for (String candidate : candidates) {
            String value = normalize(candidate);
            if (value.isEmpty()) continue;
            if (containsWakeWord(value) || isKnownCommand(value)) { chosen = value; break; }
            if (chosen == null) chosen = value;
        }
        if (chosen == null) return;
        boolean wake = containsWakeWord(chosen);
        if (wake) {
            String command = chosen.replace("يا مساعد", "").replace("يامساعد", "").trim();
            if (command.isEmpty()) {
                awaitingCommand = true;
                speak("ايوه، سامعك. قول اللي انت عايزه");
                return;
            }
            awaitingCommand = false;
            execute(command);
            return;
        }
        if (awaitingCommand) { awaitingCommand = false; execute(chosen); return; }
        if (isKnownCommand(chosen)) execute(chosen);
    }

    private boolean containsWakeWord(String value) {
        return value.contains("يا مساعد") || value.contains("يامساعد");
    }

    private boolean isKnownCommand(String c) {
        String[] words = {"واتساب","يوتيوب","كاميرا","اعدادات","الاعدادات","بلوتوث","واي فاي","وايفاي",
            "الكشاف","الفلاش","النور","الصوت","بطاري","الشحن","الوقت","الساعه","اتصل","كلم",
            "ابحث","دور على","افتح","شغل","اقفل","اطفي","طفي","اقفل المساعد","وقف المساعد",
            "انستجرام","فيسبوك","فيس بوك","خرائط","جوجل","الموقع"};
        for (String word : words) if (c.contains(word)) return true;
        return false;
    }

    private void execute(String raw) {
        String c = raw.replaceFirst("^(من فضلك |لو سمحت |ممكن |عايزك |عاوزك |عايز |عاوز |يا ريت )", "").trim();
        if (c.contains("اقفل المساعد") || c.contains("وقف المساعد") || c.contains("اسكت يا مساعد")) {
            speak("حاضر، هوقف الاستماع");
            handler.postDelayed(() -> stopSelf(), 700);
            return;
        }
        if (c.contains("واتساب")) { openApp("com.whatsapp", "واتساب"); return; }
        if (c.contains("يوتيوب")) { openApp("com.google.android.youtube", "يوتيوب"); return; }
        if (c.contains("انستجرام")) { openApp("com.instagram.android", "انستجرام"); return; }
        if (c.contains("فيسبوك") || c.contains("فيس بوك")) { openApp("com.facebook.katana", "فيسبوك"); return; }
        if (c.contains("كاميرا") || c.contains("صور")) { launch(new Intent("android.media.action.IMAGE_CAPTURE"), "الكاميرا"); return; }
        if (c.contains("اعدادات") || c.contains("الاعدادات")) { launch(new Intent(android.provider.Settings.ACTION_SETTINGS), "الإعدادات"); return; }
        if (c.contains("بلوتوث")) { launch(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), "البلوتوث"); return; }
        if (c.contains("واي فاي") || c.contains("وايفاي")) { launch(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS), "الواي فاي"); return; }
        if (c.contains("الكشاف") || c.contains("الفلاش") || c.contains("النور")) {
            boolean off = c.contains("اقفل") || c.contains("اطفي") || c.contains("طفي") || c.contains("اقطع");
            toggleTorch(!off); return;
        }
        if (c.contains("بطاري") || c.contains("الشحن")) {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            speak(level >= 0 ? "نسبة البطارية " + level + " في المية" : "مش قادر أقرأ البطارية"); return;
        }
        if (c.contains("الوقت") || c.contains("الساعه")) {
            SimpleDateFormat format = new SimpleDateFormat("h:mm a", new Locale("ar","EG"));
            speak("الساعة دلوقتي " + format.format(new Date())); return;
        }
        if (c.contains("ارفع الصوت") || c.contains("علي الصوت") || c.contains("زود الصوت") || c.contains("اعلى الصوت") || c.contains("وطي الصوت") || c.contains("وطي") || c.contains("اخفض الصوت") || c.contains("قلل الصوت") || c.contains("اكتم الصوت") || c.contains("صامت")) {
            if (c.contains("اكتم") || c.contains("صامت")) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                speak("كتمت الصوت");
            } else if (c.contains("وطي") || c.contains("اخفض") || c.contains("قلل")) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                speak("وطيّت الصوت");
            } else {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                speak("عليت الصوت");
            }
            return;
        }
        if (c.contains("اتصل") || c.contains("كلم")) {
            String number = c.replaceAll("[^0-9+]", "");
            if (number.length() >= 6) {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
                dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(dial);
                speak("راجع الرقم واضغط اتصال");
            } else speak("قول رقم التليفون بعد كلمة اتصل");
            return;
        }
        if (c.contains("ابحث") || c.contains("دور على") || c.startsWith("افتح موقع")) {
            String query = c.replace("ابحث عن","").replace("ابحث","").replace("دور على","").replace("افتح موقع","").trim();
            if (query.isEmpty()) { speak("عايز تبحث عن ايه؟"); return; }
            Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(search);
            speak("فتحتلك نتائج البحث"); return;
        }
        speak("مفهمتش قصدك. جرب تقول افتح واتساب، شغل الكشاف، أو كام البطارية");
    }

    private void openApp(String packageName, String label) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) { speak(label + " مش متثبت عندك"); return; }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); speak("بفتح " + label); }
        catch (Exception e) { speak("مش قادر أفتح " + label); }
    }

    private void launch(Intent intent, String label) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            speak("بفتح " + label);
        } catch (Exception e) { speak("مش قادر أفتح " + label); }
    }

    private void toggleTorch(boolean on) {
        if (cameraId == null) { speak("الكشاف مش متاح"); return; }
        try { camera.setTorchMode(cameraId, on); speak(on ? "شغلت الكشاف" : "طفيت الكشاف"); }
        catch (Exception e) { speak("مش قادر أتحكم في الكشاف"); }
    }

    private void speak(String message) {
        speaking = true;
        handler.removeCallbacks(restartRunnable);
        releaseRecognizer();
        Intent intent = new Intent(this, SpeechOutputService.class);
        intent.putExtra("text", message);
        try { startService(intent); }
        catch (Exception e) { speaking = false; scheduleRestart(500); }
    }

    @Override public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        releaseRecognizer();
        if (receiverRegistered) {
            try { unregisterReceiver(speechReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
