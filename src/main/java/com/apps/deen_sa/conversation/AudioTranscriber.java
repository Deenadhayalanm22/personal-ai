package com.apps.deen_sa.conversation;

/** Port for speech-to-text; channel code does not depend on a model vendor. */
public interface AudioTranscriber {
    String transcribe(byte[] audio, String mimeType);
}
