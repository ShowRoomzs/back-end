package showroomz.global.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 사업자등록번호 일방향 해시 유틸 (반려 신청서 적재용)
 */
public final class BusinessRegistrationNumberHasher {

    private BusinessRegistrationNumberHasher() {
    }

    /**
     * 하이픈/공백을 제거한 뒤 SHA-256 해시(hex)를 반환합니다.
     */
    public static String hash(String businessRegistrationNumber) {
        if (businessRegistrationNumber == null || businessRegistrationNumber.isBlank()) {
            return null;
        }

        String normalized = businessRegistrationNumber.replaceAll("[\\s-]", "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
