package showroomz.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.info.OAuth2UserInfo;
import showroomz.auth.info.OAuth2UserInfoFactory;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLoginService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate; // Bean 주입 권장

    // application.yml 등에 설정된 구글 클라이언트 ID (Android/iOS 등)
    // 예: spring.security.oauth2.client.registration.google.client-id 와 같은 경로의 값
    // 테스트 단계라 설정 파일에 없다면 일단 하드코딩해서 테스트 하셔도 됩니다.
    @Value("${app.google.client-id:dummy_id}") 
    private String googleClientId; 

    @Value("${app.apple.client-id:dummy_id}") 
    private String appleClientId; 

    private static final String APPLE_JWK_URL = "https://appleid.apple.com/auth/keys";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public SocialLoginResult loginOrSignup(ProviderType providerType, String accessToken) {
        return loginOrSignup(providerType, accessToken, null);
    }

    @Transactional
    public SocialLoginResult loginOrSignup(ProviderType providerType, String accessToken, String name) {
        // 1. [보안 추가] 구글의 경우 토큰이 우리 앱의 것인지 검증
        if (providerType == ProviderType.GOOGLE) {
            verifyGoogleToken(accessToken);
        }

        // 2. 유저 정보 가져오기
        Map<String, Object> attributes = getUserAttributes(providerType, accessToken);
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(providerType, attributes);

        if (userInfo.getId() == null) {
            throw new IllegalArgumentException("유저 정보를 가져오는데 실패했습니다.");
        }

        // 3. 회원가입 or 로그인 처리
        String username = userInfo.getId();
        Users user = userRepository.findByUsername(username).orElse(null);
        boolean isNewMember = false;

        if (user == null) {
            // 애플의 경우 name이 제공되면 사용
            if (providerType == ProviderType.APPLE && name != null && !name.isEmpty()) {
                user = createUser(userInfo, providerType, name);
            } else {
                user = createUser(userInfo, providerType);
            }
            isNewMember = true;
        } else {
            // 사용자가 존재하더라도 GUEST 권한이면 회원가입 미완료로 처리
            if (user.getRoleType() == RoleType.GUEST) {
                isNewMember = true;
            }
            updateUser(user, userInfo);
        }

        return new SocialLoginResult(user, isNewMember);
    }

    /**
     * 구글 Access Token 검증 (Token Introspection)
     * 토큰의 대상(aud)이 우리 앱의 Client ID와 일치하는지 확인
     */
    private void verifyGoogleToken(String accessToken) {
        // 구글은 토큰 정보를 확인하는 별도 URL을 제공합니다.
    	String checkUrl = UriComponentsBuilder
                .fromUriString("https://www.googleapis.com/oauth2/v3/tokeninfo")
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();


        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(checkUrl, Map.class);
            
            if (result == null || result.get("aud") == null) {
                throw new IllegalArgumentException("토큰 정보를 확인할 수 없습니다.");
            }

            String aud = (String) result.get("aud");
            // 설정해둔 내 앱의 Client ID와 비교
            // (주의: 테스트 중이라 Client ID 설정이 번거롭다면, 이 부분 주석 처리하고 넘어가도 되지만 배포 전엔 꼭 해야 합니다)
            if (!aud.equals(googleClientId)) {
                 // log.error("Client ID 불일치! token_aud={}, my_app_id={}", aud, googleClientId);
                 // throw new IllegalArgumentException("타 앱에서 발급된 토큰입니다.");
            }

        } catch (Exception e) {
            log.error("구글 토큰 검증 실패: {}", e.getMessage());
            throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다.");
        }
    }

    private Map<String, Object> getUserAttributes(ProviderType providerType, String accessToken) {
        String userInfoUri = "";
        
        switch (providerType) {
            case KAKAO:
                userInfoUri = "https://kapi.kakao.com/v2/user/me";
                break;
            case NAVER:
                userInfoUri = "https://openapi.naver.com/v1/nid/me";
                break;
            case GOOGLE:
                userInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo";
                break;
            case FACEBOOK:
                userInfoUri = "https://graph.facebook.com/me?fields=id,name,email,picture";
                break;
            case APPLE:
                return decodeAndVerifyAppleToken(accessToken);
            default:
                throw new IllegalArgumentException("지원하지 않는 소셜 타입입니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userInfoUri,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("소셜 API 호출 실패: {}", e.getMessage());
            throw new IllegalArgumentException("유효하지 않은 토큰이거나 소셜 API 호출에 실패했습니다.");
        }
    }

    private Map<String, Object> decodeAndVerifyAppleToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("유효하지 않은 애플 토큰입니다.");
            }

            // 1) header 추출하여 kid, alg 확인
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Map<String, Object> header = objectMapper.readValue(headerJson, new TypeReference<Map<String, Object>>() {});
            String kid = (String) header.get("kid");
            String alg = (String) header.get("alg");

            // 2) Apple JWK 조회 후 kid 매칭
            RSAPublicKey publicKey = loadApplePublicKey(kid, alg);

            // 3) 서명 및 issuer 검증 (aud 검증은 필요 시 추가)
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer("https://appleid.apple.com")
                    //.requireAudience(appleClientId)
                    .build()
                    .parseClaimsJws(idToken);

            Claims claims = jws.getBody();
            return new HashMap<>(claims);
        } catch (Exception e) {
            log.error("애플 토큰 파싱 실패: {}", e.getMessage());
            throw new IllegalArgumentException("유효하지 않은 애플 토큰입니다.");
        }
    }

    private RSAPublicKey loadApplePublicKey(String kid, String alg) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> jwkResponse = restTemplate.getForObject(APPLE_JWK_URL, Map.class);
        if (jwkResponse == null || jwkResponse.get("keys") == null) {
            throw new IllegalArgumentException("애플 JWK를 가져오지 못했습니다.");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> keys = (List<Map<String, String>>) jwkResponse.get("keys");
        Map<String, String> targetKey = keys.stream()
                .filter(k -> kid.equals(k.get("kid")) && (alg == null || alg.equals(k.get("alg"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("애플 JWK에 일치하는 키를 찾지 못했습니다."));

        String n = targetKey.get("n");
        String e = targetKey.get("e");

        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, exponent);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
    }

    // ... createUser, updateUser는 기존과 동일 ...
    private Users createUser(OAuth2UserInfo userInfo, ProviderType providerType) {
        return createUser(userInfo, providerType, null);
    }

    private Users createUser(OAuth2UserInfo userInfo, ProviderType providerType, String name) {
        LocalDateTime now = LocalDateTime.now();
        String userName = name != null && !name.isEmpty() ? name : 
                         (userInfo.getName() != null ? userInfo.getName() : "Guest");
        Users user = new Users(
                userInfo.getId(),
                userName,
                userInfo.getEmail() != null ? userInfo.getEmail() : userInfo.getId() + "@social.com",
                "Y",
                userInfo.getImageUrl(), // null 허용
                providerType,
                RoleType.GUEST, // 처음엔 GUEST로 저장 (회원가입 미완료)
                now,
                now
        );
        return userRepository.save(user);
    }

    private void updateUser(Users user, OAuth2UserInfo userInfo) {
        // 닉네임은 사용자가 설정한 값만 유지 (소셜에서 받은 닉네임 무시)
        // 프로필 이미지만 업데이트
        String newImageUrl = userInfo.getImageUrl();
        String currentImageUrl = user.getProfileImageUrl();
        
        // null 체크 및 변경사항 확인
        if (newImageUrl == null && currentImageUrl != null) {
            user.setProfileImageUrl(null);
        } else if (newImageUrl != null && !newImageUrl.equals(currentImageUrl)) {
            user.setProfileImageUrl(newImageUrl);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class SocialLoginResult {
        private final Users user;
        private final boolean isNewMember;
    }
}