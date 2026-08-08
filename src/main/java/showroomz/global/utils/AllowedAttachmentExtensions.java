package showroomz.global.utils;

import showroomz.domain.message.type.AttachmentType;

import java.util.Map;
import java.util.Set;

/**
 * §2-1 첨부 확장자 정책 — 통상 업무 파일은 폭넓게 허용하고 실행·스크립트 계열만 차단한다.
 * 허용 목록(default-deny) 방식을 유지한다 — 차단 목록만 두면 아직 알려지지 않은 위험 확장자가
 * 나올 때마다 뚫린 채로 남기 때문이다. 목록 조정은 이 클래스만 고치면 끝난다.
 */
public final class AllowedAttachmentExtensions {

    /** §13-7 — 메시지 1건당 첨부 개수·총 용량 상한. 확장자 정책과 같은 곳에 두어 §2/§4의 정책 상수를 한 파일에서 관리한다. */
    public static final int MAX_ATTACHMENT_COUNT = 20;
    public static final long MAX_TOTAL_SIZE_BYTES = 500L * 1024 * 1024;

    private static final Set<String> IMAGE = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif");

    private static final Set<String> VIDEO = Set.of(
            "mp4", "mov", "avi", "wmv", "mkv", "webm", "m4v");

    private static final Set<String> DOCUMENT = Set.of(
            "pdf", "doc", "docx", "hwp", "hwpx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf",
            "zip", "rar", "7z");

    /** 허용 목록에 없어도 이중 방어 차원에서 별도로 명시 차단한다. */
    private static final Set<String> EXPLICITLY_BLOCKED = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "cpl", "jar", "js", "jse", "vbs", "vbe",
            "ws", "wsf", "ps1", "psm1", "sh", "apk", "app", "dmg", "deb", "rpm", "dll", "sys",
            "bin", "lnk", "reg", "iso", "html", "htm", "php", "jsp", "asp", "aspx");

    private static final Map<String, AttachmentType> ALL;

    static {
        ALL = new java.util.HashMap<>();
        IMAGE.forEach(ext -> ALL.put(ext, AttachmentType.IMAGE));
        VIDEO.forEach(ext -> ALL.put(ext, AttachmentType.VIDEO));
        DOCUMENT.forEach(ext -> ALL.put(ext, AttachmentType.DOCUMENT));
    }

    private AllowedAttachmentExtensions() {
    }

    /** 허용되지 않으면 null을 반환한다(호출부에서 ATTACHMENT_EXTENSION_NOT_ALLOWED로 처리). */
    public static AttachmentType resolve(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null || EXPLICITLY_BLOCKED.contains(ext)) {
            return null;
        }
        return ALL.get(ext);
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /** IMAGE/VIDEO는 Content-Type 계열(image/*, video/*)이 확장자 분류와 일치해야 한다.
     *  DOCUMENT는 MIME 종류가 워낙 다양해(pdf/office/zip 등) 계열 검사를 하지 않는다. */
    public static boolean isContentTypeConsistent(AttachmentType type, String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return switch (type) {
            case IMAGE -> lower.startsWith("image/");
            case VIDEO -> lower.startsWith("video/");
            case DOCUMENT -> true;
        };
    }

    static Set<String> imageExtensions() {
        return IMAGE;
    }

    static Set<String> videoExtensions() {
        return VIDEO;
    }

    static Set<String> documentExtensions() {
        return DOCUMENT;
    }
}
