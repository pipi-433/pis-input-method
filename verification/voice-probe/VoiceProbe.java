/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.pis.voiceprobe;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.view.inputmethod.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Independent real native speech probe. No mic permission or fabricated transcripts. */
public final class VoiceProbe extends Instrumentation {
    private ClassLoader loader;
    private JSONObject report = new JSONObject();
    private Class<?> cls(String name) throws Exception { return loader.loadClass("org.fcitx.fcitx5.android.pis."+name); }
    private void check(boolean valid, String label) { if (!valid) throw new AssertionError(label); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Object engine = null;
        try {
            Context app = getTargetContext(); loader = app.getClassLoader();
            Class<?> pipeline = cls("voice.VoicePipeline"), policy = cls("voice.VoicePostProcessor"), snapshot = cls("voice.VoicePostProcessor$Snapshot");
            pipeline.getMethod("prepare",Context.class).invoke(null,app);
            ThreadPoolExecutor work = (ThreadPoolExecutor)pipeline.getField("WORK").get(null);
            work.submit(() -> {}).get(90,TimeUnit.SECONDS);
            check((Integer)pipeline.getMethod("commonWordCount").invoke(null)>0,"common TSV parsed");
            String status=(String)pipeline.getMethod("status").invoke(null); check(status.contains("就绪"),"native punctuation loaded: "+status);
            report.put("punctuation",status).put("common_words",pipeline.getMethod("commonWordCount").invoke(null));
            Object empty=snapshot.getMethod("empty").invoke(null);
            Class<?> engineClass=cls("voice.PisVoiceEngine"); engine=engineClass.getConstructor(Context.class).newInstance(app);
            List<String> recognized=new ArrayList<>();
            for(String file:new String[]{"0.wav","1.wav"}) {
                float[] samples=wave(file); long began=android.os.SystemClock.elapsedRealtime();
                String text=(String)engineClass.getMethod("transcribe",float[].class,snapshot).invoke(engine,samples,empty);
                check(!text.trim().isEmpty(),"real sample recognized: "+file); recognized.add(text);
                Object list=work.submit(() -> pipeline.getMethod("process",String.class,snapshot).invoke(null,text,empty)).get(30,TimeUnit.SECONDS);
                String punctuated=(String)((List<?>)list).get(0).getClass().getField("text").get(((List<?>)list).get(0));
                JSONObject item=new JSONObject().put("sample",file).put("seconds",samples.length/16000.0).put("elapsed_ms",android.os.SystemClock.elapsedRealtime()-began).put("recognized",text).put("punctuated",punctuated);
                report.append("native_samples",item);
            }
            check(recognized.stream().anyMatch(t->t.matches(".*[一-龥].*")),"Chinese sample recognized");
            check(recognized.stream().anyMatch(t->t.matches(".*[a-zA-Z].*")),"English sample recognized");
            policyTests(policy,snapshot,empty,pipeline,work);
            editorTests();
            report.put("input_migration",new JSONObject(cls("PisMigrationProbe").getMethod("run",Context.class).invoke(null,app).toString()));
            report.put("microphone","not opened; public prerecorded samples only").put("outcome","PASS");
            result.putString("report",report.toString(2));
            finish(-1,result);
        } catch(Throwable error) {
            try { report.put("outcome","FAIL").put("error",error.toString()); } catch(Exception ignored) {}
            StringWriter trace=new StringWriter(); error.printStackTrace(new PrintWriter(trace)); result.putString("error",trace.toString()); result.putString("report",report.toString()); finish(0,result);
        } finally { if(engine!=null) try { engine.getClass().getMethod("close").invoke(engine); } catch(Exception ignored){} }
    }
    private float[] wave(String file)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=getContext().getAssets().open(file)){ byte[] block=new byte[8192];int n;while((n=in.read(block))>0)out.write(block,0,n); }
        byte[] data=out.toByteArray(); ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        check(b.getInt(0)==0x46464952,"RIFF");int start=-1,length=0;
        for(int p=12;p+8<=data.length;) { int tag=b.getInt(p),size=b.getInt(p+4);if(tag==0x20746d66){check(b.getShort(p+8)==1 && b.getShort(p+10)==1 && b.getInt(p+12)==16000 && b.getShort(p+22)==16,"PCM mono 16k");}if(tag==0x61746164){start=p+8;length=size;break;}p+=8+size+(size&1); }
        check(start>=0,"WAV data");float[] samples=new float[length/2];for(int i=0;i<samples.length;i++)samples[i]=b.getShort(start+i*2)/32768f;return samples;
    }
    private void policyTests(Class<?> policy,Class<?> snapshot,Object empty,Class<?> pipeline,ThreadPoolExecutor work)throws Exception {
        Method edited=policy.getMethod("edited",String.class,String.class,snapshot,boolean.class,boolean.class,boolean.class);
        Object custom=edited.invoke(null,"上海\n张三","商海=>上海",empty,false,true,true);
        Field readings=pipeline.getDeclaredField("readings");readings.setAccessible(true);
        Method rank=policy.getMethod("rank",String.class,snapshot,Map.class);
        List<?> choices=(List<?>)rank.invoke(null,"明天去商海",custom,readings.get(null));
        check(choices.size()>1,"alias or homophone candidate");
        check(choices.get(0).getClass().getField("text").get(choices.get(0)).equals("明天去商海"),"original first");
        Object phonetic=edited.invoke(null,"上海","",empty,false,true,true);
        List<?> phoneticChoices=(List<?>)rank.invoke(null,"明天去商海",phonetic,readings.get(null));
        check(phoneticChoices.size()>1,"local pinyin TSV produces homophone candidate");
        String numeric="电话13800138000金额12.50";
        String guarded=(String)policy.getMethod("guardedPunctuation",String.class,String.class).invoke(null,numeric,"电话13800138000，金额1250。");
        check(numeric.equals(guarded),"punctuation cannot alter numbers");
        boolean rejected=false;try{edited.invoke(null,"","123=>456",empty,true,true,true);}catch(InvocationTargetException e){rejected=true;}check(rejected,"numeric alias rejected");
        File file=new File(getTargetContext().getCacheDir(),"voice-probe-store.json");
        Class<?> storeClass=cls("voice.VoicePostProcessor$Store"); Object store=storeClass.getConstructor(File.class).newInstance(file);
        storeClass.getMethod("save",snapshot).invoke(store,custom);storeClass.getMethod("record",String.class).invoke(store,"我今天去上海这个完整句子不得保存");
        Object reopened=storeClass.getConstructor(File.class).newInstance(file);Object saved=storeClass.getMethod("get").invoke(reopened);
        Object word=((List<?>)snapshot.getField("words").get(saved)).get(0);
        check((Integer)word.getClass().getField("uses").get(word)==1,"word counts persist");
        check(!new String(Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains("完整句子"),"no full transcript stored");
        storeClass.getMethod("resetCounts").invoke(reopened); Object reset=storeClass.getMethod("get").invoke(reopened);
        check((Integer)((List<?>)snapshot.getField("words").get(reset)).get(0).getClass().getField("uses").get(((List<?>)snapshot.getField("words").get(reset)).get(0))==0,"reset counts");
        Files.deleteIfExists(file.toPath()); report.put("policy","original first; aliases; homophones; numeric protection; private word counts; reset PASS");
    }
    private static final class Editor extends BaseInputConnection {
        final Editable text=new SpannableStringBuilder("前缀旧文本后缀");
        Editor(Context context){super(new View(context),true);Selection.setSelection(text,2,5);}
        @Override public Editable getEditable(){return text;}
        @Override public ExtractedText getExtractedText(ExtractedTextRequest request,int flags){ExtractedText e=new ExtractedText();e.text=text.toString();e.selectionStart=Selection.getSelectionStart(text);e.selectionEnd=Selection.getSelectionEnd(text);e.startOffset=0;return e;}
    }
    private void editorTests()throws Exception {
        Class<?> snapshot=cls("translation.EditorSnapshot");Method capture=snapshot.getMethod("capture",InputConnection.class),matches=snapshot.getMethod("matches",InputConnection.class);
        runOnMainSync(()->{try{
            Editor editor=new Editor(getTargetContext());Object initial=capture.invoke(null,editor);
            editor.setComposingText("语音草稿",1);Object expected=capture.invoke(null,editor);
            check(!(Boolean)matches.invoke(initial,editor),"draft invalidates old editor snapshot");
            check((Boolean)matches.invoke(expected,editor),"current draft matches");
            editor.commitText("语音结果",1);check(editor.text.toString().equals("前缀语音结果后缀"),"commit preserves prefix/suffix");
            Object beforeSelection=capture.invoke(null,editor);editor.setSelection(0,0);check(!(Boolean)matches.invoke(beforeSelection,editor),"cursor change rejects stale candidates");
            editor.setSelection(2,6);editor.setComposingText("取消草稿",1);editor.setComposingText("语音结果",1);editor.finishComposingText();check(editor.text.toString().equals("前缀语音结果后缀"),"cancel restores selected text");
            Class<?> sweep=cls("PisBackspaceSession");Object companion=sweep.getField("Companion").get(null);
            editor.text.replace(0,editor.text.length(),"abcdef😀尾部");editor.setSelection(8,8);
            Object gesture=companion.getClass().getMethod("begin",InputConnection.class).invoke(companion,editor);
            check(gesture!=null,"sweep begin");
            sweep.getMethod("step",InputConnection.class).invoke(gesture,editor);
            check(editor.text.toString().equals("abcdef尾部"),"delete full surrogate pair");
            sweep.getMethod("move",float.class,float.class,InputConnection.class).invoke(gesture,-60f,1f,editor);
            check(editor.text.toString().endsWith("尾部"),"sweep preserves suffix");
            sweep.getMethod("move",float.class,float.class,InputConnection.class).invoke(gesture,60f,1f,editor);
            check(editor.text.toString().equals("abcdef😀尾部"),"reverse sweep restores all deleted text");
            editor.setSelection(0,0);
            check(!(Boolean)sweep.getMethod("step",InputConnection.class).invoke(gesture,editor),"stale cursor rejects sweep");
        }catch(Exception error){throw new RuntimeException(error);}});
        report.put("editor","composition replacement; stale selection; cancel restore; sweep undo; emoji surrogate; suffix protection PASS");
    }
}
