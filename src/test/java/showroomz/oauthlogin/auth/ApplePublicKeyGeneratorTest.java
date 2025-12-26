package showroomz.oauthlogin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import showroomz.oauthlogin.auth.UserRepository;
import showroomz.oauthlogin.oauth.service.SocialLoginService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ApplePublicKeyGeneratorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestTemplate restTemplate;

    /**
     * SocialLoginService#loadApplePublicKey(kid, alg)는 애플 JWK 목록에서
     * kid/alg가 일치하는 키의 n,e를 사용해 RSA PublicKey를 생성한다.
     */
    @Test
    @DisplayName("헤더와 일치하는 키로 퍼블릭 키 생성")
    void 헤더와_일치하는_키로_퍼블릭_키_생성() throws Exception {
        // given
        String kid = "W6WcOKB";
        String alg = "RS256";

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();

        // Apple JWK 포맷에 맞게 n, e를 Base64 URL 인코딩
        String n = base64UrlEncode(rsaPublicKey.getModulus());
        String e = base64UrlEncode(rsaPublicKey.getPublicExponent());

        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("kid", kid);
        jwk.put("use", "sig");
        jwk.put("alg", alg);
        jwk.put("n", n);
        jwk.put("e", e);

        Map<String, Object> jwkResponse = new HashMap<>();
        jwkResponse.put("keys", List.of(jwk));

        when(restTemplate.getForObject(eq("https://appleid.apple.com/auth/keys"), any()))
                .thenReturn(jwkResponse);

        SocialLoginService service = new SocialLoginService(userRepository, restTemplate);

        // when (private 메소드 호출)
        RSAPublicKey generated = ReflectionTestUtils.invokeMethod(service, "loadApplePublicKey", kid, alg);

        // then
        assertThat(generated).isNotNull();
        assertThat(generated.getAlgorithm()).isEqualTo("RSA");
        assertThat(generated.getModulus()).isEqualTo(rsaPublicKey.getModulus());
        assertThat(generated.getPublicExponent()).isEqualTo(rsaPublicKey.getPublicExponent());
    }

    private String base64UrlEncode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // 음수 방지를 위한 선행 0 제거
        if (bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

