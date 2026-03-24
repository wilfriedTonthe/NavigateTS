package ca.ets.navigatets.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * @author ank-tech
 */
public class ToSpeech {
    private TextToSpeech textToSpeech;

    public ToSpeech(Context context, Runnable onReady) {
        this.textToSpeech = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                try {
                    textToSpeech.setLanguage(Locale.FRENCH);
                    textToSpeech.setSpeechRate(0.85f);
                    textToSpeech.setPitch(1.0f);
                } catch (Throwable ignored) {}
                if (onReady != null) onReady.run();
            }
        });
    }

    public ToSpeech(Context context) {
        this(context, null);
    }

    public TextToSpeech getTextToSpeech() {
        return this.textToSpeech;
    }
    public void speakObject(String text) {
        if (textToSpeech != null && text != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_obj");
        }
    }
    public void destroy() {
        if (this.textToSpeech != null) {
            try { this.textToSpeech.stop(); } catch (Throwable ignored) {}
            try { this.textToSpeech.shutdown(); } catch (Throwable ignored) {}
        }
    }

}
