package com.petitcamel.shop.ai.client;

/** A validated image or PDF sent to the model. {@code contentType} comes from magic-byte detection. */
public record AiInputFile(String fileName, String contentType, byte[] data) {

    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }
}
