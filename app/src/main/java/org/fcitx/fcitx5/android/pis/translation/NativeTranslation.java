package org.fcitx.fcitx5.android.pis.translation;

/** Loaded only inside the dedicated translation service process. */
final class NativeTranslation {
    static { System.loadLibrary("pis_translation"); }
    static native long create(byte[] path,int contextTokens,int threads);
    static native byte[] translate(long handle,byte[] text,byte[] targetLanguage);
    static native void cancel(long handle);
    static native void reset(long handle);
    static native void close(long handle);
}

