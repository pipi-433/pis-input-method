package org.fcitx.fcitx5.android.pis.translation;

import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Blocking facade for existing background translation workers, never the UI thread. */
public final class LocalTranslationClient {
    private static final AtomicLong ids=new AtomicLong();
    private static final AtomicReference<Job> active=new AtomicReference<>();
    private static volatile Bundle lastMetrics=new Bundle();
    public static Bundle diagnostics(){return new Bundle(lastMetrics);}
    private static final Object sessionLock=new Object();
    private static Session session;
    public static boolean prepared(){synchronized(sessionLock){return session!=null&&!session.closed&&session.prepared;}}
    private static Session acquire(Context context){
        Session result;boolean start=false;
        synchronized(sessionLock){if(session==null||session.closed){session=new Session(context.getApplicationContext());start=true;}result=session;}
        if(start)result.open();return result;
    }
    public static void prewarm(Context context){if(!ModelStore.present(context)&&!ModelStore.bundled(context))return;try{acquire(context);}catch(RuntimeException error){android.util.Log.w("LocalTranslation","prewarm connection failed");}}
    public static void release(){cancel();Session previous;synchronized(sessionLock){previous=session;session=null;}if(previous!=null)previous.close();}
    public static String translate(Context context,String text,String target)throws Exception{
        return translateInternal(context,text,target,false);
    }
    public static String translateToEnglish(Context context,String text)throws Exception{
        return translateInternal(context,text,"en",true);
    }
    private static String translateInternal(Context context,String text,String target,boolean exactTarget)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("翻译必须在后台线程运行");
        if(!ModelStore.present(context)&&!ModelStore.bundled(context))throw new IllegalStateException("没有离线模型，请安装完整版本");
        String from=TranslationLanguages.languageOf(text);
        String destination=exactTarget?target:TranslationLanguages.targetFor(from,target);
        String language;
        switch(destination){case "en":language="英语";break;case "zh":language="中文";break;case "ja":language="日语";break;case "ko":language="韩语";break;case "fr":language="法语";break;case "de":language="德语";break;case "es":language="西班牙语";break;case "ru":language="俄语";break;default:throw new IllegalArgumentException("此离线版本尚未配置该目标语言");}
        Job job=new Job(context.getApplicationContext(),text,language);Job previous=active.getAndSet(job);if(previous!=null)previous.cancel();
        try{
            Session connected=acquire(context);job.session=connected;
            if(!connected.ready.await(30,TimeUnit.SECONDS)||connected.closed||connected.remote==null)throw new IllegalStateException("无法连接离线翻译服务，请重试");
            if(job.canceled.get())throw new IllegalStateException("翻译已取消");
            job.remote=connected.remote;job.send();
            if(!job.done.await(150,TimeUnit.SECONDS)){job.cancel();throw new TimeoutException("翻译超时，请缩短段落再重试");}
            if(job.error!=null)throw new IllegalStateException(job.error);
            if(job.result==null || job.result.trim().isEmpty())throw new IllegalStateException("翻译没有返回结果");
            return job.result;
        }finally{active.compareAndSet(job,null);if(job.canceled.get())job.sendCancel();}
    }
    public static void cancel(){Job job=active.getAndSet(null);if(job!=null)job.cancel();}
    private static final class Session {
        final Context context;final CountDownLatch ready=new CountDownLatch(1);volatile boolean closed,bound,prepared;volatile Messenger remote;
        final Messenger warmReplies=new Messenger(new Handler(Looper.getMainLooper()){
            public void handleMessage(Message message){if(closed||message.what!=TranslationService.RESULT)return;Bundle data=message.getData();prepared=data.getBoolean("prepared");Bundle metrics=data.getBundle("metrics");if(prepared&&metrics!=null)lastMetrics=new Bundle(metrics);}
        });
        final ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;remote=new Messenger(binder);try{Message warm=Message.obtain(null,TranslationService.WARM);warm.replyTo=warmReplies;remote.send(warm);}catch(RemoteException error){close();}finally{ready.countDown();}}
            public void onServiceDisconnected(ComponentName name){disconnect();}
            public void onBindingDied(ComponentName name){disconnect();}
            public void onNullBinding(ComponentName name){disconnect();}
        };
        Session(Context context){this.context=context;}
        void open(){try{bound=context.bindService(new Intent(context,TranslationService.class),connection,Context.BIND_AUTO_CREATE);}catch(RuntimeException error){closed=true;ready.countDown();throw error;}if(!bound){closed=true;ready.countDown();}if(closed&&bound)close();}
        void disconnect(){closed=true;remote=null;ready.countDown();Job job=active.get();if(job!=null&&job.session==this)job.failed("离线服务已退出，请重试");close();}
        void close(){closed=true;ready.countDown();if(bound){bound=false;try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}}remote=null;}
    }
    private static final class Job {
        final Context context;final long id=ids.incrementAndGet();final String text,language;final CountDownLatch done=new CountDownLatch(1);final AtomicBoolean canceled=new AtomicBoolean();
        volatile Messenger remote;volatile Session session;volatile String error,result;
        final Messenger replies=new Messenger(new Handler(Looper.getMainLooper()){
            public void handleMessage(Message message){Bundle data=message.getData();if(data.getLong("id")!=id || canceled.get())return;Bundle metrics=data.getBundle("metrics");if(metrics!=null)lastMetrics=new Bundle(metrics);result=data.getString("text");error=data.getString("error");done.countDown();}
        });
        Job(Context context,String text,String language){this.context=context;this.text=text;this.language=language;}
        void send(){Message request=Message.obtain(null,TranslationService.TRANSLATE);Bundle args=new Bundle();args.putLong("id",id);args.putLong("requestedAt",SystemClock.elapsedRealtime());args.putString("text",text);args.putString("language",language);request.setData(args);request.replyTo=replies;try{remote.send(request);}catch(RemoteException failure){failed("离线服务连接中断，请重试");}}
        void failed(String message){error=message;done.countDown();}
        void sendCancel(){Messenger service=remote;if(service!=null)try{Message message=Message.obtain(null,TranslationService.CANCEL);Bundle args=new Bundle();args.putLong("id",id);message.setData(args);service.send(message);}catch(RemoteException ignored){}}
        void cancel(){canceled.set(true);error="翻译已取消";sendCancel();done.countDown();}
    }
}

