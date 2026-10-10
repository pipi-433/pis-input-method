package org.fcitx.fcitx5.android.pis.voice;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Pure Java dictionary and candidate policy; no Android or network dependency. */
public final class VoicePostProcessor {
    public static final int MAX_WORDS = 256, MAX_TEXT = 512, MAX_CANDIDATES = 5;
    public static final class Entry {
        public final String word; public final float boost; public final int uses; public final long lastUsed;
        public final boolean builtIn;
        public Entry(String word, float boost, int uses, long lastUsed) {
            this(word,boost,uses,lastUsed,false);
        }
        public Entry(String word, float boost, int uses, long lastUsed,boolean builtIn) {
            this.word = normalizeWord(word); this.boost = Math.max(0.5f, Math.min(3f, boost));
            this.uses = Math.max(0, Math.min(100000, uses)); this.lastUsed = Math.max(0, lastUsed);
            this.builtIn=builtIn;
        }
    }
    public static final class Alias {
        public final String heard, word;
        public Alias(String heard, String word) { this.heard = normalizeWord(heard); this.word = normalizeWord(word); }
    }
    public static final class Snapshot {
        public final List<Entry> words; public final List<Alias> aliases;
        public final boolean punctuation, candidates, learn, common; public final int timeout;
        public Snapshot(List<Entry> words, List<Alias> aliases, boolean punctuation, boolean candidates, boolean learn, int timeout) {
            this(words,aliases,punctuation,candidates,learn,timeout,true);
        }
        public Snapshot(List<Entry> words, List<Alias> aliases, boolean punctuation, boolean candidates, boolean learn, int timeout,boolean common) {
            this.words = Collections.unmodifiableList(new ArrayList<Entry>(words));
            this.aliases = Collections.unmodifiableList(new ArrayList<Alias>(aliases));
            this.punctuation = punctuation; this.candidates = candidates; this.learn = learn;
            this.common=common;
            this.timeout = Math.max(3, Math.min(20, timeout));
        }
        public static Snapshot empty() { return new Snapshot(Collections.<Entry>emptyList(), Collections.<Alias>emptyList(), true, true, true, 8); }
    }
    public static final class Candidate {
        public final String text, source; public final double rank;
        public Candidate(String text, String source, double rank) { this.text=text; this.source=source; this.rank=rank; }
    }
    public static String normalizeWord(String s) {
        return Normalizer.normalize(s == null ? "" : s.trim(), Normalizer.Form.NFC);
    }
    public static boolean safeWord(String s) {
        return s != null && s.length() >= 1 && s.length() <= 32 && s.matches("[\\p{L}\\p{N} _'-]+") && !s.contains("=>");
    }
    public static boolean currentTransaction(long expectedToken,long token,Object expectedConnection,Object connection,boolean finished){
        return !finished && expectedConnection!=null && expectedToken==token && expectedConnection==connection;
    }
    static boolean chinese(String s) { return s.matches("[\\u4e00-\\u9fff]{2,12}"); }
    static List<String> numbers(String s) {
        List<String> out=new ArrayList<String>(); Matcher m=Pattern.compile("\\p{Nd}+(?:[.,:/-]\\p{Nd}+)*").matcher(s);
        while(m.find()) out.add(m.group()); return out;
    }
    static String content(String s) { return s.replaceAll("[\\p{P}\\p{Z}\\s]", ""); }
    public static String guardedPunctuation(String raw, String predicted) {
        if(predicted == null || !content(raw).equals(content(predicted)) || !numbers(raw).equals(numbers(predicted))) return raw;
        // A model may remove spoken/original punctuation. Only allow additions.
        String a=raw.replaceAll("\\s", ""), b=predicted.replaceAll("\\s", "");
        int i=0; for(int j=0;j<b.length()&&i<a.length();j++) if(a.charAt(i)==b.charAt(j)) i++;
        return i==a.length() ? predicted : raw;
    }
    public static List<Candidate> rank(String raw, Snapshot snap, Map<Character, Set<String>> readings) {
        LinkedHashMap<String,Candidate> out=new LinkedHashMap<String,Candidate>();
        if(snap.common && snap.candidates && raw.length()<=MAX_TEXT) {
            String contextual=RIGHT_CLICK_CONTEXT.matcher(raw).replaceAll("右键");
            if(!contextual.equals(raw) && numbers(raw).equals(numbers(contextual))) {
                out.put(contextual,new Candidate(contextual,"操作语境修正",1.0));
            }
        }
        out.put(raw,new Candidate(raw,"原文",0));
        if(raw.length()>MAX_TEXT || !snap.candidates) return new ArrayList<Candidate>(out.values());
        List<Candidate> alternatives=new ArrayList<Candidate>();
        for(Alias a:snap.aliases) {
            int pos=raw.indexOf(a.heard);
            if(pos>=0 && !a.heard.equals(a.word) && boundary(raw,pos,a.heard.length())) {
                String text=raw.substring(0,pos)+a.word+raw.substring(pos+a.heard.length());
                if(numbers(raw).equals(numbers(text))) alternatives.add(new Candidate(text,"自定义修正",5+usage(snap,a.word)));
            }
        }
        for(Entry e:snap.words) {
            if(!chinese(e.word)) continue;
            for(int pos=0;pos+e.word.length()<=raw.length();pos++) {
                String heard=raw.substring(pos,pos+e.word.length());
                if(!chinese(heard) || heard.equals(e.word)) continue;
                int changes=0; boolean match=true;
                for(int k=0;k<heard.length();k++) {
                    char a=heard.charAt(k),b=e.word.charAt(k); if(a==b) continue;
                    changes++; Set<String> ar=readings.get(a),br=readings.get(b);
                    if(ar==null||br==null||Collections.disjoint(ar,br)) { match=false;break; }
                }
                if(match && changes>0 && changes<=(e.builtIn?1:2)) {
                    String text=raw.substring(0,pos)+e.word+raw.substring(pos+e.word.length());
                    alternatives.add(new Candidate(text,e.builtIn?"通用同音候选":"词库同音候选",e.boost+usage(snap,e.word)-changes*0.25));
                }
            }
        }
        Collections.sort(alternatives,new Comparator<Candidate>() {
            public int compare(Candidate a,Candidate b) { int c=Double.compare(b.rank,a.rank);return c!=0?c:a.text.compareTo(b.text); }
        });
        for(Candidate c:alternatives) { if(out.size()>=MAX_CANDIDATES) break; if(!out.containsKey(c.text))out.put(c.text,c); }
        return new ArrayList<Candidate>(out.values());
    }
    private static final Pattern RIGHT_CLICK_CONTEXT=Pattern.compile(
        "(?:又见|右见|又键)(?=\\s*(?:删除|删掉|复制|粘贴|属性|菜单|打开|选择|点击|单击))");
    static boolean boundary(String s,int pos,int len) {
        if(pos>0 && asciiWord(s.charAt(pos-1)) && asciiWord(s.charAt(pos)))return false;
        return !(pos+len<s.length() && asciiWord(s.charAt(pos+len)) && asciiWord(s.charAt(pos+len-1)));
    }
    static boolean asciiWord(char c) { return (c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'; }
    static double usage(Snapshot s,String word) { for(Entry e:s.words)if(e.word.equals(word))return Math.min(2,Math.log1p(e.uses)*0.3);return 0; }
    public static Snapshot edited(String words,String aliases,Snapshot old,boolean p,boolean c,boolean l) {
        LinkedHashMap<String,Entry> w=new LinkedHashMap<String,Entry>();
        Map<String,Entry> previous=new HashMap<String,Entry>(); for(Entry e:old.words)previous.put(e.word,e);
        for(String line:words.split("\\r?\\n")) {
            if(line.trim().isEmpty()) continue; String[] fields=line.trim().split("\\t",-1);
            if(fields.length>2)throw new IllegalArgumentException("词条格式：词语，可选制表符和0.5–3.0权重");
            String word=normalizeWord(fields[0]); if(!safeWord(word))throw new IllegalArgumentException("词条限1–32字，不支持控制符、/或冒号："+word);
            float boost=fields.length>1?Float.parseFloat(fields[1]):1.5f;
            if(!Float.isFinite(boost)||boost<0.5f||boost>3f)throw new IllegalArgumentException("热词权重须为0.5–3.0");
            Entry before=previous.get(word); w.put(word,new Entry(word,boost,before==null?0:before.uses,before==null?0:before.lastUsed));
        }
        if(w.size()>MAX_WORDS)throw new IllegalArgumentException("最多256个个人词条");
        List<Alias> a=new ArrayList<Alias>(); Set<String> seen=new HashSet<String>();
        for(String line:aliases.split("\\r?\\n")) {
            if(line.trim().isEmpty())continue;String[] f=line.split("=>",-1);
            if(f.length!=2||!safeWord(normalizeWord(f[0]))||!safeWord(normalizeWord(f[1])))throw new IllegalArgumentException("修正格式：错词=>正确词");
            Alias v=new Alias(f[0],f[1]); if(!numbers(v.heard).equals(numbers(v.word)))throw new IllegalArgumentException("修正规则不得更改数字");
            if(seen.add(v.heard))a.add(v);else throw new IllegalArgumentException("同一个错词只能有一个修正");
        }
        if(a.size()>MAX_WORDS)throw new IllegalArgumentException("最多256个修正规则");
        return new Snapshot(new ArrayList<Entry>(w.values()),a,p,c,l,old.timeout,old.common);
    }
    public static List<Entry> readCommon(Reader input)throws IOException {
        try {
        try {
        try {
        BufferedReader reader=new BufferedReader(input);List<Entry> result=new ArrayList<Entry>();Set<String> seen=new HashSet<String>();
        String line;int bytes=0;
        while((line=reader.readLine())!=null){
            bytes+=line.length();if(bytes>65536)throw new IOException("Common dictionary too large");
            if(line.startsWith("#")||line.trim().isEmpty())continue;
            String[] fields=line.split("\\t",-1);
            if(fields.length!=3||!chinese(fields[0])||fields[1].trim().isEmpty()||!seen.add(fields[0]))throw new IOException("Invalid common dictionary row");
            float boost;try{boost=Float.parseFloat(fields[2]);}catch(NumberFormatException e){throw new IOException("Invalid common boost",e);}
            if(!Float.isFinite(boost)||boost<0.5f||boost>1.0f)throw new IOException("Common boost must be conservative");
            result.add(new Entry(fields[0],boost,0,0,true));if(result.size()>384)throw new IOException("Too many common words");
        }
        return Collections.unmodifiableList(result);
    
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
    public static Snapshot withCommon(Snapshot s,boolean enabled){return new Snapshot(s.words,s.aliases,s.punctuation,s.candidates,s.learn,s.timeout,enabled);}
    public static Snapshot mergeCommon(Snapshot personal,List<Entry> common){
        if(!personal.common||common.isEmpty())return personal;
        LinkedHashMap<String,Entry> merged=new LinkedHashMap<String,Entry>();
        for(Entry e:personal.words)merged.put(e.word,e);
        for(Entry e:common)if(!merged.containsKey(e.word))merged.put(e.word,e);
        return new Snapshot(new ArrayList<Entry>(merged.values()),personal.aliases,personal.punctuation,personal.candidates,personal.learn,personal.timeout,personal.common);
    }
    public static String hotwords(Snapshot snap,Set<String> tokens) {
        List<Entry> entries=new ArrayList<Entry>(snap.words);
        Collections.sort(entries,new Comparator<Entry>(){public int compare(Entry a,Entry b){
            if(a.builtIn!=b.builtIn)return a.builtIn?1:-1;
            int c=Integer.compare(b.uses,a.uses);if(c!=0)return c;
            if(a.builtIn)return Float.compare(b.boost,a.boost);return a.word.compareTo(b.word);
        }});
        StringBuilder out=new StringBuilder();int count=0;
        for(Entry e:entries){
            if(!chinese(e.word))continue;boolean ok=true;
            for(int i=0;i<e.word.length();i++)if(!tokens.contains(String.valueOf(e.word.charAt(i)))){ok=false;break;}
            if(!ok)continue;if(count++>=64)break;if(out.length()>0)out.append('/');
            out.append(e.word).append(" :").append(String.format(Locale.ROOT,"%.2f",e.boost));
        }
        return out.toString();
    }
    public static Snapshot learn(Snapshot snap,String confirmed,long now) {
        if(!snap.learn)return snap;List<Entry> entries=new ArrayList<Entry>();
        for(Entry e:snap.words){boolean used=!e.builtIn&&confirmed.contains(e.word);entries.add(new Entry(e.word,e.boost,e.uses+(used?1:0),used?now:e.lastUsed,e.builtIn));}
        return new Snapshot(entries,snap.aliases,snap.punctuation,snap.candidates,snap.learn,snap.timeout,snap.common);
    }
    public static JSONObject encode(Snapshot s){
        try {
        JSONObject j=new JSONObject().put("schema",1).put("punctuation",s.punctuation).put("candidates",s.candidates).put("learn",s.learn).put("timeout",s.timeout).put("common",s.common);
        JSONArray words=new JSONArray(),aliases=new JSONArray();
        for(Entry e:s.words)if(!e.builtIn)words.put(new JSONObject().put("word",e.word).put("boost",e.boost).put("uses",e.uses).put("lastUsed",e.lastUsed));
        for(Alias a:s.aliases)aliases.put(new JSONObject().put("heard",a.heard).put("word",a.word));return j.put("words",words).put("aliases",aliases);
    
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
    public static Snapshot decode(String data){
        try {
        JSONObject j=new JSONObject(data);if(j.getInt("schema")!=1)throw new JSONException("Unknown dictionary schema");
        JSONArray wa=j.getJSONArray("words"),aa=j.getJSONArray("aliases");if(wa.length()>MAX_WORDS||aa.length()>MAX_WORDS)throw new JSONException("Dictionary bounds exceeded");
        List<Entry>w=new ArrayList<Entry>(); List<Alias>a=new ArrayList<Alias>();Set<String> seen=new HashSet<String>();
        for(int i=0;i<wa.length();i++){JSONObject x=wa.getJSONObject(i);String word=x.getString("word");double boost=x.getDouble("boost");if(!safeWord(word)||!Double.isFinite(boost)||boost<0.5||boost>3||!seen.add(word))throw new JSONException("Invalid word");w.add(new Entry(word,(float)boost,x.optInt("uses"),x.optLong("lastUsed")));}
        seen.clear();for(int i=0;i<aa.length();i++){JSONObject x=aa.getJSONObject(i);Alias v=new Alias(x.getString("heard"),x.getString("word"));if(!safeWord(v.heard)||!safeWord(v.word)||!numbers(v.heard).equals(numbers(v.word))||!seen.add(v.heard))throw new JSONException("Invalid alias");a.add(v);}
        return new Snapshot(w,a,j.optBoolean("punctuation",true),j.optBoolean("candidates",true),j.optBoolean("learn",true),j.optInt("timeout",8),j.optBoolean("common",true));
    
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
    public static final class Store {
        final Path path; private volatile Snapshot cached; private volatile String error="";
        public Store(File file){path=file.toPath();}
        public synchronized Snapshot get(){
            if(cached!=null)return cached;
            try{if(Files.exists(path)){if(Files.size(path)>262144)throw new IOException("Dictionary too large");cached=decode(new String(Files.readAllBytes(path),StandardCharsets.UTF_8));}else cached=Snapshot.empty();}
            catch(Exception e){cached=Snapshot.empty();error="个人词库读取失败；已使用空词库，原文件保留";}
            return cached;
        }
        public String error(){return error;}
        public synchronized void save(Snapshot s){
        try {
            String json=encode(s).toString();Path tmp=path.resolveSibling(path.getFileName()+".tmp");
            if(!error.isEmpty()&&Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".corrupt.bak"),StandardCopyOption.REPLACE_EXISTING);
            try(FileOutputStream f=new FileOutputStream(tmp.toFile())){f.write(json.getBytes(StandardCharsets.UTF_8));f.getFD().sync();}
            try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
            cached=s;error="";
        
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
        public synchronized void record(String confirmed){
        try { Snapshot s=get();if(!error.isEmpty()||!s.learn)return;save(learn(s,confirmed,System.currentTimeMillis())); 
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
        public synchronized void resetCounts(){
        try {Snapshot s=get();List<Entry>w=new ArrayList<Entry>();for(Entry e:s.words)w.add(new Entry(e.word,e.boost,0,0));save(new Snapshot(w,s.aliases,s.punctuation,s.candidates,s.learn,s.timeout,s.common));
        } catch(Throwable recoveryError) { throw VoiceExceptions.unchecked(recoveryError); }
    }
    }
}

