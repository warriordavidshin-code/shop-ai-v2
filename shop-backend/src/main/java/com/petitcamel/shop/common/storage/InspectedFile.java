package com.petitcamel.shop.common.storage;

/**
 * An upload whose real type was confirmed from its leading bytes.
 *
 * @param originalFileName sanitized client file name (display only, never used as a path)
 * @param contentType      detected MIME type
 * @param extension        extension matching the detected type, including the dot
 * @param sha256           lowercase hex SHA-256 of the content
 */
public record InspectedFile(
        String originalFileName,
        String contentType,
        String extension,
        byte[] data,
        String sha256
) {
    public long size() {
        return data.length;
    }
}
