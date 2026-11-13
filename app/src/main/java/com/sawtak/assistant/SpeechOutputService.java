package com.sawtak.assistant;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

public class SpeechOutputService extends Service implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private String text="";
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        text=intent==null?"":intent.getStringExtra("text");
        if(tts==null)tts=new TextToSpeech(this,this);
        else speakNow();
        return START_NOT_STICKY;
    }
    @Override public void onInit(int status) {
        if(status==TextToSpeech.SUCCESS){
            tts.setLanguage(new Locale("ar","EG"));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){}
                @Override public void onDone(String id){stopSelf();}
                @Override public void onError(String id){stopSelf();}
            });
            speakNow();
        } else stopSelf();
    }
    private void speakNow(){
        if(tts!=null)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"sawtak_reply");
    }
    @Override public void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
