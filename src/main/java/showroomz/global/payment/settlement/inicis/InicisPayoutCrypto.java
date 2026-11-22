package showroomz.global.payment.settlement.inicis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 이니시스 지급대행의 ENC · {@code userHash}(60 전환 계획서 1-4 · D-11) — 순수 함수만. 매뉴얼 팝업(ENC · HASH 규칙)을 확보하지 못해
 * <b>가정</b>으로 구현했다(Q4): ENC = UTF-8 바이트의 Base64, {@code userHash} = SHA-256 hex 소문자 64자 ·
 * 대상 {@code yyyyMMdd(KST) + idMerchant + 평문 계좌번호 + X-API-KEY}. 답이 오면 이 클래스만 바뀐다.
 *
 * <p>ENC 가 Base64 뿐이면 사실상 평문이다 — ENC 값이 든 요청 본문은 로그에 남기지 않는다.
 */
public final class InicisPayoutCrypto {

    /** {@code userHash} 의 「현재날짜」 형식(가정 · Q4). */
    static final DateTimeFormatter HASH_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    public static final int USER_HASH_LENGTH = 64;

    private InicisPayoutCrypto() {
    }

    /** ENC 대상 필드(대표자명 · 예금주 · 계좌번호 · 휴대전화 · 이메일 · 생년월일) 인코딩. null 은 null. */
    public static String enc(String plain) {
        return plain == null ? null : Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /** ENC 값 복원 — 송금 결과의 계좌 · 이름을 대조할 때만 쓴다. 해석할 수 없으면 null. */
    public static String dec(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 서브몰 등록 위변조 검증값. 「현재날짜」가 자정을 넘기면 어긋나므로 KST 날짜를 호출자가 넘긴다(자정 전후에 배치를 두지 않는다 · D-11).
     *
     * @param plainAccountNumber {@code base64Decode(noAcct)} — 숫자만의 평문 계좌번호
     */
    public static String userHash(LocalDate kstToday, String merchantId, String plainAccountNumber, String apiKey) {
        String target = kstToday.format(HASH_DATE) + nullToEmpty(merchantId) + nullToEmpty(plainAccountNumber)
                + nullToEmpty(apiKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(target.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
