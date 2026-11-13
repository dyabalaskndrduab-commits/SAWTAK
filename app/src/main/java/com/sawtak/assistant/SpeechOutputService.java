package com.sawtak.assistant;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

public class SpeechOutputService extends Service implements TextToSpeech.OnInitListener {
    private static final String ACTION_SPEECH_DONE = "com.sawtak.assistant.SPEECH_DONE";
    private TextToSpeech tts;
    private String text = "";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        text = intent == null ? "" : intent.getStringExtra("text");
        if (tts == null) tts = new TextToSpeech(this, this);
        else speakNow();
        return START_NOT_STICKY;
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) { notifyDone(); stopSelf(); return; }
        int languageStatus = tts.setLanguage(new Locale("ar", "EG"));
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(new Locale("ar"));
        }
        tts.setSpeechRate(1.0f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { notifyDone(); stopSelf(); }
            @Override public void onError(String id) { notifyDone(); stopSelf(); }
        });
        speakNow();
    }

    private void speakNow() {
        if (tts != null && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sawtak_reply");
        } else {
            notifyDone(); stopSelf();
        }
    }

    private void notifyDone() {
        Intent done = new Intent(ACTION_SPEECH_DONE);
        done.setPackage(getPackageName());
        sendBroadcast(done);
    }

    @Override public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
