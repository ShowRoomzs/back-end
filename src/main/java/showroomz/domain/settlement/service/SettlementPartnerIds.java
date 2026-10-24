package showroomz.domain.settlement.service;

import showroomz.domain.settlement.type.SettlementPayee;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * PG 셀러 · 지급 참조 식별자 규칙(44_토스페이먼츠_지급대행_연동_BE_설계서.md 3-4) — 우리가 정하므로 재시도 · 중복 호출이 멱등하다.
 * 접두는 모드가 정한다(테스트 {@code t-} · 운영 빈 문자열). 길이 제약(토스)을 넘으면 번호 체계 버그라 즉시 터뜨린다.
 */
public final class SettlementPartnerIds {

    /** 토스 {@code refSellerId} 7 ~ 20자. */
    static final int PARTNER_REF_MIN = 7;
    static final int PARTNER_REF_MAX = 20;
    /** 토스 {@code refPayoutId} ≤50자. */
    static final int PAYOUT_REF_MAX = 50;

    private SettlementPartnerIds() {
    }

    /** {@code {prefix}brand-{marketId}} · {@code {prefix}creator-{creatorId}}. 플랫폼 몫은 셀러가 아니다. */
    public static String partnerRef(String prefix, SettlementPayee payee, Long refId) {
        String ref = switch (payee) {
            case BRAND -> prefix + "brand-" + refId;
            case CREATOR -> prefix + "creator-" + refId;
            case PLATFORM -> throw new IllegalArgumentException("플랫폼 몫은 PG 셀러로 등록하지 않는다");
        };
        if (ref.length() < PARTNER_REF_MIN || ref.length() > PARTNER_REF_MAX) {
            throw new IllegalStateException("refSellerId 길이 위반(7~20) — " + ref);
        }
        return ref;
    }

    /** {@code {prefix}{settlementNumber}-{payee}-{attempt}} — 같은 행 재지시는 같은 ref, 재분배(attempt + 1)는 새 ref. */
    public static String payoutRef(String prefix, String settlementNumber, SettlementPayee payee, int attempt) {
        String ref = prefix + settlementNumber + "-" + payee.name() + "-" + attempt;
        if (ref.length() > PAYOUT_REF_MAX) {
            throw new IllegalStateException("refPayoutId 길이 위반(≤50) — " + ref);
        }
        return ref;
    }

    /** {@code Idempotency-Key} — 참조 id 에서 결정적으로(0-6). 같은 행 재시도는 같은 키 · 재분배는 새 키. */
    public static String idempotencyKey(String ref) {
        return UUID.nameUUIDFromBytes(ref.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 계좌 지문 — PG 에 보낸 계좌의 변경 감지(평문 보존 없이). */
    public static String accountHash(String bankCode, String accountNumber, String holder) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String joined = nullToEmpty(bankCode) + "|" + nullToEmpty(accountNumber) + "|" + nullToEmpty(holder);
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 법인 판정(설계서 4-1 · D-14) — 사업자등록번호 가운데 2자리(4 ~ 5번째) 81 ~ 88 이면 법인(국세청 체계). 유형 문자열에 「법인」이 있으면
     * 그대로 법인. 틀리면 PG 가 {@code INVALID_BUSINESS_NUMBER} 로 거절하고 어댑터가 반대 유형으로 1회 재시도한다.
     */
    public static boolean isCorporate(String registrationNumberDigits, String businessTypeText) {
        if (businessTypeText != null && businessTypeText.contains("법인")) {
            return true;
        }
        if (registrationNumberDigits == null || registrationNumberDigits.length() != 10) {
            return false;
        }
        int middle = Integer.parseInt(registrationNumberDigits.substring(3, 5));
        return middle >= 81 && middle <= 88;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
