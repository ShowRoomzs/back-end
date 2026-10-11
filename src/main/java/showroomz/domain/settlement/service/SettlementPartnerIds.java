package showroomz.domain.settlement.service;

import showroomz.domain.settlement.type.SettlementPayee;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * PG 파트너 · 정산건 식별자 규칙(44_포트원_파트너정산_연동_BE_설계서.md 3-3) — 우리가 정하므로 재시도 · 중복 호출이 멱등하다.
 * 접두는 모드가 정한다(테스트 {@code t-} · 운영 빈 문자열).
 */
public final class SettlementPartnerIds {

    private SettlementPartnerIds() {
    }

    /** {@code {prefix}brand-{marketId}} · {@code {prefix}creator-{creatorId}}. 플랫폼 몫은 파트너가 아니다. */
    public static String partnerId(String prefix, SettlementPayee payee, Long refId) {
        return switch (payee) {
            case BRAND -> prefix + "brand-" + refId;
            case CREATOR -> prefix + "creator-" + refId;
            case PLATFORM -> throw new IllegalArgumentException("플랫폼 몫은 PG 파트너로 등록하지 않는다");
        };
    }

    /** {@code {prefix}{settlementNumber}-{payee}-{attempt}} — 같은 행 재지시는 같은 id, 재분배(attempt + 1)는 새 id. */
    public static String transferId(String prefix, String settlementNumber, SettlementPayee payee, int attempt) {
        return prefix + settlementNumber + "-" + payee.name() + "-" + attempt;
    }

    /** 계좌 지문 — 포트원에 보낸 계좌의 변경 감지(평문 보존 없이). */
    public static String accountHash(String bankCode, String accountNumber, String holder) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String joined = nullToEmpty(bankCode) + "|" + nullToEmpty(accountNumber) + "|" + nullToEmpty(holder);
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
