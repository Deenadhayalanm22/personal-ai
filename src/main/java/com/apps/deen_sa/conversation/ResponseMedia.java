package com.apps.deen_sa.conversation;

import java.util.Arrays;

/** Binary media produced by a capability for delivery by the active conversation channel. */
public record ResponseMedia(byte[] content, String contentType, String filename) {
    public ResponseMedia {
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("contentType is required");
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("filename is required");
    }

    @Override public byte[] content() { return Arrays.copyOf(content, content.length); }
}
