package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** DI 해시(57 설계서 4절) — 같은 키면 결정적, 키가 다르면 다르고, 암호문과는 무관하다. */
@DisplayName("[57 CY] 고유식별정보 암호화 · DI 해시")
class PersonalDataCipherTest {

    private static final String SECRET_A = "unit-test-secret-a-long-enough-for-hs256-derivation";
    private static final String SECRET_B = "unit-test-secret-b-long-enough-for-hs256-derivation";

    @Test
    @DisplayName("CY-01 hmac 은 같은 입력 · 같은 키에서 항상 같은 64자 hex 다")
    void hmacIsDeterministic() {
        PersonalDataCipher cipher = new PersonalDataCipher("", SECRET_A);

        String first = cipher.hmac("DI-VALUE");
        String second = new PersonalDataCipher("", SECRET_A).hmac("DI-VALUE");

        assertThat(first).isEqualTo(second).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("CY-02 키가 다르면 해시도 다르다 — 키 교체 시 재계산이 필요하다")
    void hmacDependsOnKey() {
        assertThat(new PersonalDataCipher("", SECRET_A).hmac("DI-VALUE"))
                .isNotEqualTo(new PersonalDataCipher("", SECRET_B).hmac("DI-VALUE"));
    }

    @Test
    @DisplayName("CY-03 입력이 다르면 다르고 null 은 null")
    void hmacDistinguishesInputsAndKeepsNull() {
        PersonalDataCipher cipher = new PersonalDataCipher("", SECRET_A);

        assertThat(cipher.hmac("A")).isNotEqualTo(cipher.hmac("B"));
        assertThat(cipher.hmac(null)).isNull();
    }

    @Test
    @DisplayName("CY-04 암호문은 매번 달라도(IV) 복호화하면 원문이고, 해시는 암호문과 무관하다")
    void encryptionIsRandomizedButReversible() {
        PersonalDataCipher cipher = new PersonalDataCipher("", SECRET_A);

        String enc1 = cipher.encrypt("DI-VALUE");
        String enc2 = cipher.encrypt("DI-VALUE");

        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(cipher.decrypt(enc1)).isEqualTo("DI-VALUE");
        assertThat(cipher.decrypt(enc2)).isEqualTo("DI-VALUE");
        assertThat(cipher.hmac("DI-VALUE")).isNotEqualTo(enc1);
    }
}
