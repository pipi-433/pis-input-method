package org.fcitx.fcitx5.android.pis.voice;

final class VoiceExceptions {
    private VoiceExceptions() {}
    static RuntimeException unchecked(Throwable error) {
        if(error instanceof RuntimeException)return (RuntimeException)error;
        if(error instanceof Error)throw (Error)error;
        return new IllegalStateException(error);
    }
}
