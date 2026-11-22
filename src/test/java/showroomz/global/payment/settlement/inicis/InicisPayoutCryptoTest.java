package showroomz.global.payment.settlement.inicis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** 이니시스 ENC · userHash(60 전환 계획서 9-1) — 규칙은 가정(Q4)이라 대상 문자열 순서 · 날짜 형식 · 길이를 못 박아 둔다. */
@DisplayName("이니시스 지급대행 ENC · userHash — 6필드 왕복 · 대상 순서(날짜 + MID + 평문계좌 + 키) · KST 날짜 · 64자")
class InicisPayoutCryptoTest {

    @Test
    @DisplayName("ENC 6필드(대표자명 · 예금주 · 계좌 · 휴대폰 · 이메일 · 생년월일) 왕복 — 한글 UTF-8 · null 은 null")
    void encRoundTrip() {
        for (String plain : new String[]{"김대표", "(주)코코브라운", "110123456789", "01012345678", "tax@brand.example", "19850314"}) {
            String encoded = InicisPayoutCrypto.enc(plain);
            assertThat(encoded).isNotEqualTo(plain).matches("^[A-Za-z0-9+/=]+$");
            assertThat(InicisPayoutCrypto.dec(encoded)).isEqualTo(plain);
        }
        assertThat(InicisPayoutCrypto.enc(null)).isNull();
        assertThat(InicisPayoutCrypto.dec(null)).isNull();
        assertThat(InicisPayoutCrypto.dec("%%%")).isNull();
    }

    @Test
    @DisplayName("userHash = SHA-256 hex(yyyyMMdd + idMerchant + 평문 계좌번호 + X-API-KEY) · 64자 소문자 · 날짜가 바뀌면 값이 바뀐다")
    void userHashTargetOrder() throws Exception {
        LocalDate kstToday = LocalDate.of(2026, 11, 16);
        String hash = InicisPayoutCrypto.userHash(kstToday, "INIpayTest", "110123456789", "api-key");

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("20261116INIpayTest110123456789api-key".getBytes(StandardCharsets.UTF_8)));
        assertThat(hash).isEqualTo(expected).hasSize(InicisPayoutCrypto.USER_HASH_LENGTH).matches("^[0-9a-f]{64}$");
        assertThat(InicisPayoutCrypto.userHash(kstToday.plusDays(1), "INIpayTest", "110123456789", "api-key"))
                .isNotEqualTo(hash);
        // 순서가 다르면 다른 값 — 대상 문자열을 섞지 않는다.
        assertThat(InicisPayoutCrypto.userHash(kstToday, "110123456789", "INIpayTest", "api-key")).isNotEqualTo(hash);
    }
}
