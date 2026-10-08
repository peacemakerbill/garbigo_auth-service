package com.garbigo.auth.service;

import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public final class UploadedFileRules {

    private UploadedFileRules() {
    }

    public record ValidatedFile(byte[] bytes, String mimeType, String extension, String displayName) {
    }

    public static ValidatedFile validate(MultipartFile file, DocumentType type, int maxMb) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("Please choose a file to upload.");
        }
        long maxBytes = maxMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new CustomException("That file is too large. Please upload a file smaller than " + maxMb
                    + " MB. Tip: take the photo again in lower quality, or save the document as a smaller PDF.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new CustomException("We couldn't read that file. Please try uploading it again.");
        }

        String mime = detectMime(bytes);
        if (mime == null) {
            throw new CustomException("Please upload a PDF, JPG or PNG file. If your phone saved the photo in HEIC "
                    + "format, change the camera setting to 'Most compatible' and take the photo again.");
        }
        if (type.imageOnly() && mime.equals("application/pdf")) {
            throw new CustomException(type.label() + " must be a photo (JPG or PNG), not a PDF.");
        }

        String ext = switch (mime) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            default -> "jpg";
        };
        return new ValidatedFile(bytes, mime, ext, displayName(file.getOriginalFilename(), ext));
    }

    private static String detectMime(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    private static String displayName(String original, String ext) {
        String base = original == null ? "document" : original;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^A-Za-z0-9 _.-]", "").trim();
        if (base.isEmpty()) {
            base = "document";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return base + "." + ext;
    }
}