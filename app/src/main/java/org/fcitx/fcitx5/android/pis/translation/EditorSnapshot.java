package org.fcitx.fcitx5.android.pis.translation;

import android.view.inputmethod.*;
import java.util.Objects;

/** Exact short editor window and selection used to reject stale output and writes. */
public final class EditorSnapshot {
    public final String text,selected,before,after;
    private final int selectionStart,selectionEnd,startOffset;
    private final boolean extracted;
    private EditorSnapshot(String text,String selected,String before,String after,int start,int end,int offset,boolean extracted){this.text=text;this.selected=selected;this.before=before;this.after=after;this.selectionStart=start;this.selectionEnd=end;this.startOffset=offset;this.extracted=extracted;}
    private static String string(CharSequence text){return text==null?null:text.toString();}
    public static EditorSnapshot capture(InputConnection connection){
        if(connection==null)return null;
        String selected=string(connection.getSelectedText(0));ExtractedText value=connection.getExtractedText(new ExtractedTextRequest(),0);
        if(value!=null && value.text!=null){return new EditorSnapshot(value.text.toString(),selected,null,null,value.selectionStart,value.selectionEnd,value.startOffset,true);}
        String before=string(connection.getTextBeforeCursor(6001,0)),after=string(connection.getTextAfterCursor(6001,0));
        if(before==null || after==null || before.length()>=6001 || after.length()>=6001)return null;
        return new EditorSnapshot(before+(selected==null?"":selected)+after,selected,before,after,-1,-1,0,false);
    }
    public String source(){String source=selected!=null&&!selected.isEmpty()?selected:text;if(source.length()>6000)throw new IllegalArgumentException("请选中一个较短段落（不超过6000字）");if(extracted && startOffset!=0 && (selected==null||selected.isEmpty()))throw new IllegalArgumentException("请选择需要翻译的文字");return source;}
    public boolean matches(InputConnection connection){EditorSnapshot current=capture(connection);return current!=null && extracted==current.extracted && selectionStart==current.selectionStart && selectionEnd==current.selectionEnd && startOffset==current.startOffset && Objects.equals(text,current.text) && Objects.equals(selected,current.selected) && Objects.equals(before,current.before) && Objects.equals(after,current.after);}
    public boolean selectedRange(){return selected!=null&&!selected.isEmpty();}
}

