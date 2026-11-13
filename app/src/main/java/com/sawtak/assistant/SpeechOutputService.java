package com.sawtak.assistant;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class SpeechOutputService extends Service implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private String text;
    @Override public int onStartCommand(Intent i, int f, int id) {
        text = i == null ? "" : i.getStringExtra("text");
        tts = new TextToSpeech(this, this);
        return START_NOT_STICKY;
    }
    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("ar","EG"));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply");
        }
        stopSelf();
    }
    @Override public void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
