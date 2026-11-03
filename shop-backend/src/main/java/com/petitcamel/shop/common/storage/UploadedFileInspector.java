package com.petitcamel.shop.common.storage;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks uploads by extension AND leading bytes (the browser-supplied Content-Type is ignored), so a renamed
 * executable or HTML file is rejected. Supports JPEG, PNG, WEBP and PDF.
 */
public final class UploadedFileInspector {

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> IMAGE_AND_PDF_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private static final Map<String, Set<String>> EXTENSIONS = Map.of(
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/png", Set.of(".png"),
            "image/webp", Set.of(".webp"),
            "application/pdf", Set.of(".pdf"));

    private UploadedFileInspector() {
    }

    public static InspectedFile inspect(MultipartFile file, Set<String> allowedTypes, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "빈 파일은 업로드할 수 없습니다.");
        }
        String name = sanitizeFileName(file.getOriginalFilename());
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    name + ": 파일이 너무 큽니다. (최대 " + (maxBytes / (1024 * 1024)) + "MB)");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, name + ": 파일을 읽을 수 없습니다.");
        }
        return inspect(name, data, allowedTypes, maxBytes);
    }

    public static InspectedFile inspect(String fileName, byte[] data, Set<String> allowedTypes, long maxBytes) {
        String name = sanitizeFileName(fileName);
        if (data == null || data.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, name + ": 빈 파일입니다.");
        }
        if (data.length > maxBytes) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    name + ": 파일이 너무 큽니다. (최대 " + (maxBytes / (1024 * 1024)) + "MB)");
        }
        String detected = detectType(data);
        if (detected == null || !allowedTypes.contains(detected)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    name + ": 지원하지 않는 파일 형식입니다. (" + describe(allowedTypes) + ")");
        }
        String ext = extensionOf(name);
        if (!EXTENSIONS.get(detected).contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    name + ": 파일 확장자와 실제 형식이 일치하지 않습니다.");
        }
        return new InspectedFile(name, detected, ext.equals(".jpeg") ? ".jpg" : ext, data, sha256(data));
    }

    /** Detects the type from magic bytes only. */
    public static String detectType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        if (d.length >= 5 && new String(d, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "application/pdf";
        }
        return null;
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Keeps only the last path segment and drops control / reserved characters. */
    public static String sanitizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String describe(Set<String> types) {
        return types.contains("application/pdf") ? "JPG, JPEG, PNG, WEBP, PDF" : "JPG, JPEG, PNG, WEBP";
    }
}
