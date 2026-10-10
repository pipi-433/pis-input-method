package org.fcitx.fcitx5.android.pis.translation;

/** Local target selection without account or network services. */
public final class TranslationLanguages {
    private TranslationLanguages(){}
    public static String languageOf(String text){
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c>=12352&&c<12544)return "ja";}
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if((c>=44032&&c<55216)||(c>=4352&&c<4608))return "ko";}
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c>=13312&&c<40960)return "zh";}
        return "en";
    }
    public static String targetFor(String from,String target){return from.equals(target)?(from.equals("zh")?"en":"zh"):target;}
}

