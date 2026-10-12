/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.pis.voiceprobe;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.*;

public final class CandidateEnglishProbe extends Instrumentation {
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            // Instrumentation starts before Application.onCreate finishes; wait for its main-loop setup.
            runOnMainSync(() -> {});
            Context context = getTargetContext();
            Object mapping = context.getClassLoader().loadClass("org.fcitx.fcitx5.android.pis.PisCandidateProbe")
                .getMethod("run", Context.class).invoke(null, context);
            result.putString("native", mapping.toString());
            runOnMainSync(() -> {
                try { result.putString("gestures", check(context)); }
                catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); }
            });
            finish(result.containsKey("error") ? 0 : -1, result);
        } catch (Throwable error) {
            result.putString("error", android.util.Log.getStackTraceString(error)); finish(0, result);
        }
    }

    private String check(Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> function = loader.loadClass("kotlin.jvm.functions.Function0");
        Object unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
        Class<?> type = loader.loadClass("org.fcitx.fcitx5.android.pis.PisCandidateSwipeView");
        View view = (View)type.getConstructor(Context.class).newInstance(context);
        int[] translations = {0}, clicks = {0};
        Object action = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (p, m, a) -> {
            if (m.getName().equals("invoke")) translations[0]++;
            return unit;
        });
        Object factory = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (p, m, a) -> action);
        Object missing = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (p, m, a) -> null);
        Method bind = type.getMethod("setUpActionFactory", function);
        bind.invoke(view, factory);
        view.setOnClickListener(v -> clicks[0]++);
        float density = context.getResources().getDisplayMetrics().density;
        view.layout(0, 0, (int)(140*density), (int)(64*density));
        float x = 70*density, y = 40*density, distance = 36*density;
        gesture(view, x, y, x+2, y-distance, MotionEvent.ACTION_UP);
        require(translations[0] == 1 && clicks[0] == 0, "upward emits English once without selecting Chinese");
        gesture(view, x, y, x, y, MotionEvent.ACTION_UP);
        require(translations[0] == 1 && clicks[0] == 1, "tap still selects normal candidate");
        gesture(view, x, y, x+distance, y, MotionEvent.ACTION_UP);
        gesture(view, x, y, x, y+distance, MotionEvent.ACTION_UP);
        gesture(view, x, y, x+distance, y-distance, MotionEvent.ACTION_UP);
        gesture(view, x, y, x, y-distance, MotionEvent.ACTION_CANCEL);
        require(translations[0] == 1 && clicks[0] == 1, "horizontal/down/diagonal/cancel do not submit");
        bind.invoke(view, missing);
        gesture(view, x, y, x, y-distance, MotionEvent.ACTION_UP);
        require(translations[0] == 1 && clicks[0] == 1, "missing translation leaves candidate untouched");
        bind.invoke(view, factory);
        event(view, MotionEvent.ACTION_DOWN, x, y);
        bind.invoke(view, missing);
        event(view, MotionEvent.ACTION_MOVE, x, y-distance);
        event(view, MotionEvent.ACTION_UP, x, y-distance);
        require(translations[0] == 1 && clicks[0] == 1, "rebound candidate cannot finish old swipe");
        bind.invoke(view, factory);
        event(view, MotionEvent.ACTION_DOWN, x, y);
        view.setOnLongClickListener(v -> true);
        require(view.performLongClick(), "long press still opens meaning");
        event(view, MotionEvent.ACTION_MOVE, x, y-distance);
        event(view, MotionEvent.ACTION_UP, x, y-distance);
        require(translations[0] == 1 && clicks[0] == 1, "long press cannot also output English");
        return "actual MotionEvents: up once, ordinary tap, missing mapping, horizontal/down/diagonal, cancel, rebind and long press PASS";
    }

    private void gesture(View view, float x, float y, float endX, float endY, int finish) {
        event(view, MotionEvent.ACTION_DOWN, x, y);
        event(view, MotionEvent.ACTION_MOVE, endX, endY);
        event(view, finish, endX, endY);
    }
    private void event(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
        try { view.onTouchEvent(event); } finally { event.recycle(); }
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
