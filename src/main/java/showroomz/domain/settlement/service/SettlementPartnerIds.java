package showroomz.domain.settlement.service;

import showroomz.domain.settlement.type.SettlementPayee;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * PG 서브몰 식별자 · 계좌 지문 규칙(60_KG이니시스_지급대행_전환_계획서.md D-3) — 우리가 정하므로 재시도 · 중복 호출이 멱등하다. 접두는 모드가
 * 정한다(이니시스 테스트 {@code t} · 운영 빈 문자열). 길이 제약을 넘으면 번호 체계 버그라 즉시 터뜨린다. 어느 규칙을 쓰는지는
 * {@code SettlementPartnerGateway#partnerRef} 가 정한다(이니시스 {@link #subMallId} · 시뮬레이터 기본 {@link #partnerRef}).
 */
public final class SettlementPartnerIds {

    /** 시뮬레이터 기본 참조 id 7 ~ 20자(예전 토스 {@code refSellerId} 규칙을 그대로 둔다 — 시뮬레이터는 PG 에 등록하지 않는다). */
    static final int PARTNER_REF_MIN = 7;
    static final int PARTNER_REF_MAX = 20;
    /** 이니시스 {@code idMall} — 등록 명세 60자 · 조회 명세 10자 중 좁은 쪽(D-3 · Q10). */
    public static final int SUB_MALL_ID_MAX = 10;

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

    /**
     * 이니시스 서브몰 id — {@code {prefix}b{marketId}} · {@code {prefix}c{creatorId}}(운영 접두 없음 · {@code PG_TEST} 는 {@code t}). PG 발급 id 가
     * 없으므로 {@code pg_partner_id = pg_partner_ref = idMall} 이다(D-3).
     */
    public static String subMallId(String prefix, SettlementPayee payee, Long refId) {
        String id = switch (payee) {
            case BRAND -> prefix + "b" + refId;
            case CREATOR -> prefix + "c" + refId;
            case PLATFORM -> throw new IllegalArgumentException("플랫폼 몫은 PG 서브몰로 등록하지 않는다");
        };
        if (id.length() > SUB_MALL_ID_MAX) {
            throw new IllegalStateException("idMall 길이 위반(≤" + SUB_MALL_ID_MAX + ") — " + id);
        }
        return id;
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
