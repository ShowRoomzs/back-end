package showroomz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
public class AppleTokenParserTest {

    private final AppleTokenParser appleTokenParser = new AppleTokenParser(new ObjectMapper());

    @Test
    void 애플_토큰_헤더_파싱_테스트() throws Exception {
        // given
        Date now = new Date();
        
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        String appleToken = Jwts.builder()
                .setHeaderParam("kid", "kid 값")
                .claim("email", "test@test.com")
                .setIssuer("https://appleid.apple.com")
                .setIssuedAt(now)
                .setAudience("aud 값")
                .setSubject("sub_test")
                .setExpiration(new Date(now.getTime() + 60 * 60 * 1000))
                .signWith(privateKey, io.jsonwebtoken.SignatureAlgorithm.RS256)
                .compact();

        // when
        Map<String, String> headers = appleTokenParser.parseHeader(appleToken);

        // then
        assertThat(headers).containsKeys("alg", "kid");
    }

    @Test
    void 애플_클레임_파싱_테스트() throws Exception {
        // given
        Date now = new Date();
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        String appleToken = Jwts.builder()
                .setHeaderParam("kid", "kid 값")
                .claim("email", "test@test.com")
                .setIssuer("https://appleid.apple.com")
                .setIssuedAt(now)
                .setAudience("aud 값")
                .setSubject("sub_test")
                .setExpiration(new Date(now.getTime() + 60 * 60 * 1000))
                .signWith(privateKey, io.jsonwebtoken.SignatureAlgorithm.RS256)
                .compact();

        // when
        Claims claims = appleTokenParser.extractClaims(appleToken, publicKey);

        // then
        assertThat(claims.get("email")).isEqualTo("test@test.com");
    }

    /**
     * 테스트 전용 Apple 토큰 파서
     */
    static class AppleTokenParser {
        private final ObjectMapper objectMapper;

        AppleTokenParser(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        Map<String, String> parseHeader(String idToken) throws Exception {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("유효하지 않은 애플 토큰입니다.");
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            return objectMapper.readValue(headerJson, new TypeReference<Map<String, String>>() {});
        }

        Claims extractClaims(String idToken, PublicKey publicKey) {
            return Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer("https://appleid.apple.com")
                    .build()
                    .parseClaimsJws(idToken)
                    .getBody();
        }
    }
}

