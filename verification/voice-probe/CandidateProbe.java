/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.pis.voiceprobe;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;

public final class CandidateProbe extends Instrumentation {
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle out = new Bundle();
        try {
            Context context = getTargetContext();
            Object report = context.getClassLoader()
                .loadClass("org.fcitx.fcitx5.android.pis.PisCandidateProbe")
                .getMethod("run", Context.class).invoke(null, context);
            out.putString("report", report.toString());
            runOnMainSync(() -> {
                try {
                    out.putString("gestures", checkGestures(context));
                    out.putString("widths", checkCandidateWidths(context));
                }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            finish(-1, out);
        } catch (Throwable error) {
            out.putString("error", android.util.Log.getStackTraceString(error));
            finish(0, out);
        }
    }

    private String checkCandidateWidths(Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        String base = "org.fcitx.fcitx5.android.";
        Class<?> prefsClass = loader.loadClass(base + "data.prefs.AppPrefs");
        Object companion = prefsClass.getField("Companion").get(null);
        Object prefs = companion.getClass().getMethod("getInstance").invoke(companion);
        Object keyboard = prefsClass.getMethod("getKeyboard").invoke(prefs);
        Object limit = keyboard.getClass().getMethod("getCandidateMaxWidth").invoke(keyboard);
        Method get = limit.getClass().getMethod("getValue");
        Method set = limit.getClass().getMethod("setValue", int.class);
        Object previous = get.invoke(limit);
        float density = context.getResources().getDisplayMetrics().density;
        Class<?> presets = loader.loadClass(base + "data.theme.ThemePreset");
        Object theme = presets.getMethod("getPixelLight").invoke(presets.getField("INSTANCE").get(null));
        Class<?> uiClass = loader.loadClass(base + "input.candidates.CandidateItemUi");
        Class<?> wordClass = loader.loadClass(base + "core.CandidateWord");
        Constructor<?> word = wordClass.getConstructor(String.class, String.class, String.class);
        Method update = uiClass.getMethod("updateCandidate", wordClass);
        Method hint = uiClass.getDeclaredMethod("updateHint", String.class);
        hint.setAccessible(true);
        try {
            for (int cap : new int[]{80, 240, 480}) {
                set.invoke(limit, cap);
                if (!get.invoke(limit).equals(cap)) throw new AssertionError("Width setting not persisted");
                Object ui = uiClass.getConstructor(Context.class, loader.loadClass(base + "data.theme.Theme"))
                    .newInstance(context, theme);
                ViewGroup root = (ViewGroup) uiClass.getMethod("getRoot").invoke(ui);
                root.setPadding((int)(10*density), 0, (int)(10*density), 0);
                update.invoke(ui, word.newInstance("", "eh", ""));
                hint.invoke(ui, "hi");
                measure(root, density, 0);
                int shortWidth = root.getMeasuredWidth();
                hint.invoke(ui, "cardinal number");
                measure(root, density, 0);
                TextView hintView = (TextView)((ViewGroup)root.getChildAt(0)).getChildAt(0);
                if (root.getMeasuredWidth() <= shortWidth) throw new AssertionError("Hint did not widen candidate");
                if (root.getMeasuredWidth() > (int)(cap*density)) throw new AssertionError("Width limit exceeded");
                if (cap >= 240 && hintView.getLayout().getEllipsisCount(0) != 0)
                    throw new AssertionError("Fitting hint still ellipsized");
                hint.invoke(ui, "a very long explanation ".repeat(30));
                measure(root, density, 0);
                if (root.getMeasuredWidth() != (int)(cap*density) || hintView.getLayout().getEllipsisCount(0) == 0)
                    throw new AssertionError("Long hint did not ellipsize at configured cap");
                update.invoke(ui, word.newInstance("", "longcandidate".repeat(30), ""));
                measure(root, density, 0);
                TextView textView = (TextView)((ViewGroup)root.getChildAt(0)).getChildAt(1);
                if (root.getMeasuredWidth() > (int)(cap*density) || textView.getLayout().getEllipsisCount(0) == 0)
                    throw new AssertionError("Long primary word exceeded limit");
                update.invoke(ui, word.newInstance("", "zx", ""));
                measure(root, density, 0);
                if (hintView.length() != 0 || root.getMeasuredWidth() >= (int)(cap*density))
                    throw new AssertionError("Recycled candidate retained old hint/width");
                hint.invoke(ui, "cardinal number");
                measure(root, density, 60);
                View column = root.getChildAt(0);
                if (column.getRight() > root.getWidth() || hintView.getRight() > column.getWidth())
                    throw new AssertionError("Expanded grid candidate overflows its cell");
            }
            return "80/240/480dp: adaptive hints, cap ellipsis, long words, recycled rows, grid constraints PASS";
        } finally { set.invoke(limit, previous); }
    }

    private void measure(View root, float density, int exactDp) {
        root.measure(View.MeasureSpec.makeMeasureSpec((int)(exactDp*density),
            exactDp == 0 ? View.MeasureSpec.UNSPECIFIED : View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((int)(58*density), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private String checkGestures(Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        String base = "org.fcitx.fcitx5.android.";
        Class<?> presets = loader.loadClass(base + "data.theme.ThemePreset");
        Object theme = presets.getMethod("getPixelLight").invoke(presets.getField("INSTANCE").get(null));
        Class<?> keyboardClass = loader.loadClass(base + "input.keyboard.TextKeyboard");
        ViewGroup keyboard = (ViewGroup) keyboardClass.getConstructor(Context.class,
            loader.loadClass(base + "data.theme.Theme")).newInstance(context, theme);
        List<Object> actions = new ArrayList<>();
        Class<?> actionListener = loader.loadClass(base + "input.keyboard.KeyActionListener");
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{actionListener}, (proxy, method, args) -> {
            if (method.getName().equals("onKeyAction")) actions.add(args[0]);
            return null;
        });
        keyboardClass.getMethod("setKeyActionListener", actionListener).invoke(keyboard, listener);
        Class<?> type = loader.loadClass(base + "input.keyboard.CustomGestureView$GestureType");
        Object up = Arrays.stream(type.getEnumConstants()).filter(t -> t.toString().equals("Up")).findFirst().get();
        Class<?> eventClass = loader.loadClass(base + "input.keyboard.CustomGestureView$Event");
        Constructor<?> eventConstructor = eventClass.getConstructor(type, boolean.class, float.class,
            float.class, int.class, int.class, int.class, int.class);
        Class<?> gestureListener = loader.loadClass(base + "input.keyboard.CustomGestureView$OnGestureListener");
        Method dispatch = gestureListener.getMethod("onGesture", View.class, eventClass);
        Class<?> layout = loader.loadClass(base + "pis.PisKeyboardLayout");
        List<?> rows = (List<?>) layout.getMethod("getText").invoke(layout.getField("INSTANCE").get(null));
        int letters = 0;
        for (int row = 0; row < 3; row++) {
            ViewGroup views = (ViewGroup) keyboard.getChildAt(row);
            List<?> definitions = (List<?>) rows.get(row);
            for (int i = 0; i < definitions.size(); i++) {
                Object definition = definitions.get(i);
                if (!definition.getClass().getSimpleName().equals("AlphabetKey")) continue;
                String expected = (String) definition.getClass().getMethod("getPunctuation").invoke(definition);
                View key = views.getChildAt(i);
                Object handler = key.getClass().getMethod("getOnGestureListener").invoke(key);
                actions.clear();
                dispatch.invoke(handler, key, eventConstructor.newInstance(up, false, 0f, 0f, 0, -1, 0, -1));
                if (actions.size() != 1 || !expected.equals(actions.get(0).getClass().getMethod("getText").invoke(actions.get(0))))
                    throw new AssertionError("Up-swipe did not emit exactly one shortcut: " + expected);
                actions.clear();
                dispatch.invoke(handler, key, eventConstructor.newInstance(up, false, 0f, 0f, 0, 1, 0, 1));
                if (!actions.isEmpty()) throw new AssertionError("Down-swipe still emitted a shortcut: " + expected);
                if (!key.performLongClick() || actions.size() != 1 ||
                    !expected.equals(actions.get(0).getClass().getMethod("getText").invoke(actions.get(0))))
                    throw new AssertionError("Long press changed: " + expected);
                letters++;
            }
        }
        if (letters != 26) throw new AssertionError("Unexpected letter count: " + letters);
        return "26 actual key views: UP shortcut PASS; DOWN no shortcut PASS; long press PASS";
    }
}
