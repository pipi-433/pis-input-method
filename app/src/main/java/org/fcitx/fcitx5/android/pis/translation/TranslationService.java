package org.fcitx.fcitx5.android.pis.translation;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Non-exported, same-app Binder endpoint; owns native objects on one worker. */
public final class TranslationService extends Service {
    static final int TRANSLATE=1,CANCEL=2,RESULT=3,WARM=4;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final Object nativeLock=new Object();
    private long handle;
    private Handler main;
    private Messenger messenger;
    private long currentId;
    private static final AtomicInteger modelLoads=new AtomicInteger();
    private volatile boolean destroyed;
    private long initializedAt,lastPrepareMs,lastLoadMs;
    public void onCreate(){super.onCreate();main=new Handler(Looper.getMainLooper());messenger=new Messenger(new Handler(Looper.getMainLooper()){
        public void handleMessage(Message message){
            if(destroyed)return;
            if(message.what==WARM){Messenger reply=message.replyTo;worker.execute(()->{
                Bundle result=new Bundle();try{initialize();result.putBoolean("prepared",handle!=0&&!destroyed);Bundle metrics=new Bundle();metrics.putInt("pid",android.os.Process.myPid());metrics.putInt("modelLoads",modelLoads.get());metrics.putLong("prepareMs",lastPrepareMs);metrics.putLong("loadMs",lastLoadMs);result.putBundle("metrics",metrics);}
                catch(Throwable error){android.util.Log.w("LocalTranslation","prewarm failed: "+error.getClass().getSimpleName());result.putBoolean("prepared",false);}
                if(reply!=null&&!destroyed){Message response=Message.obtain(null,RESULT);response.setData(result);try{reply.send(response);}catch(RemoteException ignored){}}
            });return;}
            if(message.what==CANCEL){if(message.getData().getLong("id")==currentId)cancel();return;}
            if(message.what!=TRANSLATE || message.replyTo==null)return;
            int job=generation.incrementAndGet();cancelNative();Bundle args=message.getData();Messenger reply=message.replyTo;long id=args.getLong("id");currentId=id;String text=args.getString("text","");String language=args.getString("language","英语");
            long requestedAt=args.getLong("requestedAt",SystemClock.elapsedRealtime());worker.execute(()->run(job,id,text,language,reply,requestedAt));
        }
    });}
    public IBinder onBind(Intent intent){return messenger.getBinder();}
    private void cancelNative(){synchronized(nativeLock){if(handle!=0)NativeTranslation.cancel(handle);}}
    private void cancel(){generation.incrementAndGet();cancelNative();}
    private void initialize()throws Exception{
        if(handle!=0||destroyed)return;
        long start=SystemClock.elapsedRealtime();java.io.File model=ModelStore.ensureAvailable(this,()->destroyed,bytes->{});lastPrepareMs=SystemClock.elapsedRealtime()-start;if(destroyed)return;
        start=SystemClock.elapsedRealtime();long loaded=NativeTranslation.create(model.getAbsolutePath().getBytes(StandardCharsets.UTF_8),2048,4);lastLoadMs=SystemClock.elapsedRealtime()-start;
        synchronized(nativeLock){if(destroyed){NativeTranslation.close(loaded);return;}handle=loaded;initializedAt=SystemClock.elapsedRealtime();modelLoads.incrementAndGet();}
    }
    private void run(int job,long id,String text,String language,Messenger reply,long requestedAt){
        if(job!=generation.get())return;
        Bundle result=new Bundle();result.putLong("id",id);
        Bundle metrics=new Bundle();metrics.putInt("pid",android.os.Process.myPid());
        try {
            if(text.isEmpty() || text.length()>6000)throw new IllegalArgumentException("请选择一个长句或短段落（不超过6000字）");
            initialize();if(destroyed)return;
            boolean warm=initializedAt<=requestedAt;metrics.putBoolean("warm",warm);metrics.putLong("prepareMs",warm?0:lastPrepareMs);metrics.putLong("loadMs",warm?0:lastLoadMs);
            synchronized(nativeLock){if(handle!=0)NativeTranslation.reset(handle);}
            if(job!=generation.get())return;
            long inferenceStart=SystemClock.elapsedRealtime();byte[] translated=NativeTranslation.translate(handle,text.getBytes(StandardCharsets.UTF_8),language.getBytes(StandardCharsets.UTF_8));metrics.putLong("inferenceMs",SystemClock.elapsedRealtime()-inferenceStart);
            if(job!=generation.get())return;
            result.putString("text",new String(translated,StandardCharsets.UTF_8).trim());
        }catch(Throwable error){if(job!=generation.get())return;result.putString("error",error.getMessage()==null?"离线翻译失败，请重试":error.getMessage());}
        if(job!=generation.get())return;
        metrics.putInt("modelLoads",modelLoads.get());result.putBundle("metrics",metrics);Message response=Message.obtain(null,RESULT);response.setData(result);try{reply.send(response);}catch(RemoteException ignored){}
    }
    public void onDestroy(){destroyed=true;cancel();worker.execute(()->{synchronized(nativeLock){if(handle!=0){NativeTranslation.close(handle);handle=0;}}});worker.shutdown();super.onDestroy();}
}

