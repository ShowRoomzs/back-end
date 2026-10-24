package showroomz.global.payment.settlement.toss;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 토스 지급대행 JWE(토스 설계서 1-2) — alg dir · enc A256GCM · iat · nonce · 64 hex 키. */
@DisplayName("토스 지급대행 JWE 암·복호화")
class TossJweTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("암호화 → 복호화 라운드트립 · 헤더 alg=dir · enc=A256GCM · iat(ISO 8601 ±hh:mm) · nonce")
    void roundTrip() {
        TossJwe jwe = new TossJwe(KEY);
        String json = "{\"refSellerId\":\"brand-12\",\"amount\":{\"currency\":\"KRW\",\"value\":35180}}";

        String compact = jwe.encrypt(json, OffsetDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        JWEHeader header = TossJwe.header(compact);

        assertThat(compact.split("\\.")).hasSize(5);
        assertThat(header.getAlgorithm()).isEqualTo(JWEAlgorithm.DIR);
        assertThat(header.getEncryptionMethod()).isEqualTo(EncryptionMethod.A256GCM);
        assertThat(header.getCustomParam("iat")).isEqualTo("2026-10-15T10:00:00+09:00");
        assertThat(String.valueOf(header.getCustomParam("nonce"))).hasSize(36);
        assertThat(jwe.decrypt(compact)).isEqualTo(json);
    }

    @Test
    @DisplayName("다른 키로는 복호화되지 않고(IllegalArgumentException) · JWE 가 아닌 문자열도 거절 · 호출자가 평문 JSON 인지 다시 본다")
    void wrongKeyRejected() {
        String compact = new TossJwe(KEY).encrypt("{}");
        TossJwe other = new TossJwe("f".repeat(64));

        assertThatThrownBy(() -> other.decrypt(compact)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> other.decrypt("{\"code\":\"UNAUTHORIZED_KEY\"}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보안 키 — 64자 hex 가 아니면 생성 실패(기동 검증이 잡는다)")
    void keyValidated() {
        assertThatThrownBy(() -> new TossJwe("abc")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64자");
        assertThatThrownBy(() -> new TossJwe("zz" + KEY.substring(2))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hex");
        assertThatThrownBy(() -> new TossJwe(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TossJwe.parseKey(" " + KEY + " ")).hasSize(32);
    }
}
