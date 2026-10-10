package org.fcitx.fcitx5.android.pis.voice;

import android.content.Context;
import android.util.Log;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Native ASR hooks plus serialized local post-processing. No audio second pass. */
public final class VoicePipeline {
    public static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(4),new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"local-voice-post");t.setDaemon(true);return t;}});
    static Context app;
    static VoicePostProcessor.Store store;
    static Set<String> tokens;
    static Map<Character,Set<String>> readings;
    static List<VoicePostProcessor.Entry> common;
    static volatile int commonCount;
    static OfflinePunctuation punctuation;
    static boolean punctuationTried;
    static volatile String status="";
    public static synchronized VoicePostProcessor.Store store(Context context) {
        if(store==null){app=context.getApplicationContext();store=new VoicePostProcessor.Store(new File(app.getFilesDir(),"local_voice_lexicon.json"));}
        return store;
    }
    public static String status(){return status;}
    public static int commonWordCount(){return commonCount;}
    public static void prepare(final Context context) {

        store(context);
        try{WORK.execute(new Runnable(){public void run(){try{resources();initPunctuation();}catch(Throwable e){status="标点准备失败，继续使用原始语音结果";Log.w("LocalVoice",status,e);}}});}
        catch(RejectedExecutionException e){Log.w("LocalVoice","Preparation queue busy");}
    }
    static synchronized void resources(){
        try {
        if(tokens!=null && readings!=null && common!=null)return;
        Set<String> ts=new HashSet<String>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(app.getAssets().open("voice/tokens.txt"),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){int p=line.lastIndexOf(' ');if(p>0)ts.add(line.substring(0,p));}}
        Map<Character,Set<String>> rs=new HashMap<Character,Set<String>>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(app.getAssets().open("voice/local-pinyin.tsv"),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){String[] f=line.split("\t");if(f.length==2&&f[0].length()==1)rs.put(f[0].charAt(0),new HashSet<String>(Arrays.asList(f[1].split(","))));}}
        tokens=Collections.unmodifiableSet(ts);readings=Collections.unmodifiableMap(rs);
        try(Reader r=new InputStreamReader(app.getAssets().open("voice/common-hotwords.tsv"),StandardCharsets.UTF_8)){
            common=VoicePostProcessor.readCommon(r);commonCount=common.size();
        }catch(IOException e){common=Collections.emptyList();commonCount=0;Log.w("LocalVoice","Common word bank unavailable; using personal dictionary",e);}
        store.get();
    
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
    public static VoicePostProcessor.Snapshot snapshot(Context context){return store(context).get();}
    public static OnlineStream createStream(OnlineRecognizer recognizer,Context context,VoicePostProcessor.Snapshot snapshot) {
        String h="";
        try{store(context);resources();h=VoicePostProcessor.hotwords(VoicePostProcessor.mergeCommon(snapshot==null?store.get():snapshot,common),tokens);}
        catch(Exception e){Log.w("LocalVoice","Hotword snapshot unavailable",e);}
        try{return recognizer.createStream(h);}
        catch(RuntimeException e){Log.w("LocalVoice","Hotword stream rejected; using unbiased stream",e);return recognizer.createStream("");}
    }
    static void initPunctuation() {
        if(punctuationTried)return;punctuationTried=true;

        try{punctuation=new OfflinePunctuation(app.getAssets(),new OfflinePunctuationConfig(new OfflinePunctuationModelConfig("voice/punctuation/model.int8.onnx",2,false,"cpu")));status="本地标点就绪";}
        catch(Throwable e){status="标点模型不可用，保留原文";Log.w("LocalVoice",status,e);}
    }
    public static List<VoicePostProcessor.Candidate> process(String raw,VoicePostProcessor.Snapshot snap) {
        Map<Character,Set<String>> rs;
        try{resources();rs=readings;}catch(Exception e){rs=Collections.emptyMap();status="读音词典不可用，仍可使用自定义修正";}
        List<VoicePostProcessor.Candidate> list=VoicePostProcessor.rank(raw,VoicePostProcessor.mergeCommon(snap,common==null?Collections.<VoicePostProcessor.Entry>emptyList():common),rs);
        if(snap.punctuation && raw.length()<=VoicePostProcessor.MAX_TEXT){
            initPunctuation();if(punctuation!=null){
                List<VoicePostProcessor.Candidate> processed=new ArrayList<VoicePostProcessor.Candidate>();
                for(VoicePostProcessor.Candidate c:list){
                    String text=c.text;try{text=VoicePostProcessor.guardedPunctuation(text,punctuation.addPunctuation(text));}
                    catch(Throwable e){status="本次标点失败，保留原文";Log.w("LocalVoice",status,e);}
                    processed.add(new VoicePostProcessor.Candidate(text,c.source,c.rank));
                }
                return processed;
            }
        }
        return list;
    }
    public static void release() {
        try{WORK.execute(new Runnable(){public void run(){if(punctuation!=null){punctuation.release();punctuation=null;}punctuationTried=false;}});}
        catch(RejectedExecutionException e){Log.w("LocalVoice","Model release deferred until process exit");}
    }
}

