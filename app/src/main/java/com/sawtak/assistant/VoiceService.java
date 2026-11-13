package com.sawtak.assistant;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import android.net.Uri;
import java.text.Normalizer;
import java.util.*;

public class VoiceService extends Service {
    private SpeechRecognizer recognizer;
    private boolean active=false, awaitingCommand=false;
    private AudioManager audio;
    private CameraManager camera;
    private String cameraId;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int errorCount=0;
    private static final String CHANNEL="voice";

    @Override public void onCreate() {
        super.onCreate();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        camera=(CameraManager)getSystemService(CAMERA_SERVICE);
        try {
            for(String id:camera.getCameraIdList()) {
                Boolean flash=camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if(Boolean.TRUE.equals(flash)){cameraId=id;break;}
            }
        } catch(Exception ignored) {}
        createChannel();
        Notification n=new Notification.Builder(this,CHANNEL)
            .setContentTitle("صوتك يعمل")
            .setContentText("استمع للأوامر مباشرة أو قل: يا مساعد")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
        startForeground(7,n);
    }

    private void createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel c=new NotificationChannel(CHANNEL,"المساعد الصوتي",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent i,int flags,int id) {
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        active=true; beginRecognition(); return START_STICKY;
    }

    private void beginRecognition() {
        if(!active)return;
        if(!SpeechRecognizer.isRecognitionAvailable(this)){speak("خدمة التعرف على الكلام غير متاحة. تأكد من تثبيت وتفعيل تطبيق التعرف على الكلام");scheduleRestart(2500);return;}
        try {
            if(recognizer!=null){recognizer.cancel();recognizer.destroy();}
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){errorCount=0;}
                public void onBeginningOfSpeech(){}
                public void onRmsChanged(float v){}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){}
                public void onError(int e){
                    errorCount++;
                    if(e==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){speak("إذن الميكروفون غير متاح");stopSelf();return;}
                    scheduleRestart(errorCount>3?1800:900);
                }
                public void onResults(Bundle b){errorCount=0;handleResults(b);scheduleRestart(700);}
                public void onPartialResults(Bundle b){}
                public void onEvent(int t,Bundle b){}
            });
            Intent in=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            in.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            in.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");
            in.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-EG");
            in.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            in.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            in.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
            recognizer.startListening(in);
        } catch(Exception e) { scheduleRestart(1500); }
    }

    private void scheduleRestart(long delay) {
        handler.removeCallbacksAndMessages(null);
        if(active)handler.postDelayed(this::beginRecognition,delay);
    }

    private String normalize(String value) {
        String s=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        s=s.replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي');
        return s.replace("،"," ").replace("؟","").toLowerCase(new Locale("ar","EG")).trim().replaceAll("\\s+"," ");
    }

    private void handleResults(Bundle b) {
        if(b==null)return;
        ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(list==null||list.isEmpty())return;
        String spoken=normalize(list.get(0));
        if(spoken.isEmpty())return;
        boolean wake=spoken.contains("يا مساعد")||spoken.contains("يامساعد")||spoken.contains("يا مساعِد");
        if(wake) {
            String command=spoken.replace("يا مساعد","").replace("يامساعد","").trim();
            if(command.isEmpty()){awaitingCommand=true;speak("ايوه، سامعك. قول الامر");return;}
            awaitingCommand=false;execute(command);return;
        }
        if(awaitingCommand){awaitingCommand=false;execute(spoken);return;}
        // Also accept a recognized supported command without requiring the wake phrase.
        if(isKnownCommand(spoken))execute(spoken);
    }

    private boolean isKnownCommand(String c) {
        return c.contains("واتساب")||c.contains("يوتيوب")||c.contains("كاميرا")||c.contains("اعدادات")||
            c.contains("بلوتوث")||c.contains("واي فاي")||c.contains("الكشاف")||c.contains("الفلاش")||
            c.contains("الصوت")||c.contains("بطاري")||c.contains("الشحن")||c.contains("الوقت")||
            c.contains("الساعه")||c.contains("اتصل")||c.contains("ابحث")||c.contains("دور على")||
            c.contains("اقفل المساعد")||c.contains("وقف المساعد");
    }

    private void execute(String c) {
        if(c.contains("اقفل المساعد")||c.contains("وقف المساعد")){speak("حاضر، هوقف الاستماع");handler.postDelayed(this::stopSelf,500);return;}
        if(c.contains("واتساب")&&(c.contains("افتح")||c.contains("شغل")||c.contains("واتساب"))){openApp("com.whatsapp","واتساب");return;}
        if(c.contains("يوتيوب")){openApp("com.google.android.youtube","يوتيوب");return;}
        if(c.contains("كاميرا")){launch(new Intent("android.media.action.IMAGE_CAPTURE"),"الكاميرا");return;}
        if(c.contains("اعدادات")){launch(new Intent(android.provider.Settings.ACTION_SETTINGS),"الإعدادات");return;}
        if(c.contains("بلوتوث")){launch(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),"إعدادات البلوتوث");return;}
        if(c.contains("واي فاي")){launch(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),"إعدادات الواي فاي");return;}
        if(c.contains("الكشاف")||c.contains("الفلاش")){toggleTorch(!(c.contains("اقفل")||c.contains("اطفي")||c.contains("طفي")));return;}
        if(c.contains("ارفع الصوت")||c.contains("علي الصوت")||c.contains("زود الصوت")||c.contains("اعلى الصوت")){
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI);speak("عليت الصوت");return;
        }
        if(c.contains("وطي الصوت")||c.contains("اخفض الصوت")||c.contains("قلل الصوت")){
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);speak("وطيّت الصوت");return;
        }
        if(c.contains("اكتم الصوت")||c.contains("صامت")){
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,AudioManager.FLAG_SHOW_UI);speak("كتمت الصوت");return;
        }
        if(c.contains("بطاري")||c.contains("الشحن")){
            Intent bi=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level=bi==null?-1:bi.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            speak(level>=0?"نسبة البطارية "+level+" في المية":"مش قادر أقرأ البطارية");return;
        }
        if(c.contains("الوقت")||c.contains("الساعه")){
            java.text.SimpleDateFormat df=new java.text.SimpleDateFormat("h:mm a",new Locale("ar","EG"));
            speak("الساعة دلوقتي "+df.format(new Date()));return;
        }
        if(c.contains("اتصل")){
            String number=c.replaceAll("[^0-9+]","");
            if(number.length()>=6){Intent dial=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+number));dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(dial);speak("راجِع الرقم واضغط اتصال");}
            else speak("قول رقم التليفون بعد كلمة اتصل");
            return;
        }
        if(c.contains("ابحث")||c.contains("دور على")){
            String q=c.replace("ابحث عن","").replace("ابحث","").replace("دور على","").trim();
            Intent search=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(q)));
            search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(search);speak("دي نتائج البحث");return;
        }
        speak("مفهمتش الأمر. جرب: افتح واتساب، شغل الكشاف، أو كم البطارية");
    }

    private void openApp(String pkg,String label) {
        Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null){speak(label+" مش متثبت عندك");return;}
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);speak("بفتح "+label);
    }
    private void launch(Intent i,String label) {
        try{i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);speak("بفتح "+label);}
        catch(Exception e){speak("مش قادر أفتح "+label);}
    }
    private void toggleTorch(boolean on) {
        if(cameraId==null){speak("الكشاف مش متاح");return;}
        try{camera.setTorchMode(cameraId,on);speak(on?"شغلت الكشاف":"طفيت الكشاف");}
        catch(Exception e){speak("مش قادر أتحكم في الكشاف");}
    }
    private void speak(String message) {
        Intent i=new Intent(this,SpeechOutputService.class);i.putExtra("text",message);startService(i);
    }
    @Override public void onDestroy() {
        active=false;handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
