/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.pis.voiceprobe;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
public class WordFrequencyProbe extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle out=new Bundle();try{Object report=getTargetContext().getClassLoader().loadClass("org.fcitx.fcitx5.android.pis.PisWordFrequencyProbe").getMethod("run",Context.class).invoke(null,getTargetContext());out.putString("report",report.toString());finish(-1,out);}catch(Throwable error){out.putString("error",android.util.Log.getStackTraceString(error));finish(0,out);}}
}
