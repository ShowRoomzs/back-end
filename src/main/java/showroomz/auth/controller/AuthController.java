package showroomz.auth.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import showroomz.auth.DTO.*;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.exception.BusinessException;
import showroomz.auth.refreshToken.UserRefreshToken;
import showroomz.auth.refreshToken.UserRefreshTokenRepository;
import showroomz.auth.service.AuthService;
import showroomz.auth.service.SocialLoginService;
import showroomz.auth.service.SocialLoginService.SocialLoginResult;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.config.properties.AppProperties;
import showroomz.global.error.exception.ErrorCode;
import showroomz.swaggerDocs.AuthControllerDocs;
import showroomz.user.DTO.NicknameCheckResponse;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;
import showroomz.user.service.UserService;
import showroomz.utils.HeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/v1/user/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AppProperties appProperties;
    private final AuthTokenProvider tokenProvider;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    
    private final static long THREE_DAYS_MSEC = 259200000;

    @Override
    @PostMapping("/social/login")
    public ResponseEntity<?> socialLogin(@RequestBody @Valid SocialLoginRequest socialLoginRequest) {
        // 1. 필수 파라미터 검증
        if (socialLoginRequest.getToken() == null || socialLoginRequest.getToken().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_TOKEN);
        }

        if (socialLoginRequest.getProviderType() == null || socialLoginRequest.getProviderType().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_PROVIDER_TYPE);
        }

        // 2. ProviderType 변환
        ProviderType providerType;
        try {
            providerType = ProviderType.valueOf(socialLoginRequest.getProviderType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_PROVIDER);
        }

        // 3. 소셜 로그인 처리 (애플의 경우 name 전달)
        SocialLoginResult result;
        try {
            if (providerType == ProviderType.APPLE && socialLoginRequest.getName() != null) {
                result = socialLoginService.loginOrSignup(
                        providerType,
                        socialLoginRequest.getToken(),
                        socialLoginRequest.getName()
                );
            } else {
                result = socialLoginService.loginOrSignup(
                        providerType,
                        socialLoginRequest.getToken()
                );
            }
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message.contains("유효하지 않은") || message.contains("토큰") || message.contains("만료")) {
                throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
            }
            if (message.contains("이미 다른 계정에서 사용 중인 이메일")) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 4. 신규 회원인 경우 registerToken 반환 (5분 유효)
        if (result.isNewMember()) {
            Date now = new Date();
            long registerTokenExpiry = 5 * 60 * 1000; // 5분
            AuthToken registerToken = tokenProvider.createAuthToken(
                    result.getUser().getUsername(),
                    new Date(now.getTime() + registerTokenExpiry)
            );
            return ResponseEntity.ok(new TokenResponse(registerToken.getToken()));
        }

        // 5. 기존 회원인 경우 일반 토큰 반환
        return ResponseEntity.ok(authService.generateTokens(
                result.getUser().getUsername(),
                result.getUser().getRoleType(),
                result.getUser().getUserId(),
                false
        ));
    }
    
    @Override
    @PostMapping("/social/signup")
    public ResponseEntity<?> register(
            HttpServletRequest request,
            @RequestBody @Valid RegisterRequest registerRequest) {
        // 1. registerToken 검증
        String registerTokenStr = HeaderUtil.getAccessToken(request);
        if (registerTokenStr == null || registerTokenStr.isEmpty()) {
            throw new BusinessException(ErrorCode.REGISTER_EXPIRED);
        }

        AuthToken registerToken = tokenProvider.convertAuthToken(registerTokenStr);
        if (!registerToken.validate()) {
            throw new BusinessException(ErrorCode.REGISTER_EXPIRED);
        }

        Claims claims = registerToken.getTokenClaims();
        if (claims == null) {
            throw new BusinessException(ErrorCode.REGISTER_EXPIRED);
        }

        String username = claims.getSubject();

        // 2. 닉네임 검증 (형식, 금칙어, 중복 체크)
        NicknameCheckResponse nicknameCheck = userService.checkNickname(registerRequest.getNickname());
        if (!nicknameCheck.getIsAvailable()) {
            if ("INVALID_FORMAT".equals(nicknameCheck.getCode()) || "INVALID_LENGTH".equals(nicknameCheck.getCode())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            } else if ("PROFANITY".equals(nicknameCheck.getCode())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            } else if ("DUPLICATE".equals(nicknameCheck.getCode())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
        }

        // 4. 생년월일 형식 검증 (null이 아닐 때만)
        if (registerRequest.getBirthday() != null && !registerRequest.getBirthday().isEmpty()) {
            if (!registerRequest.getBirthday().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // 5. Users 조회 및 업데이트
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 회원가입이 완료된 사용자(GUEST가 아닌 경우)는 재가입 불가
        if (user.getRoleType() != RoleType.GUEST) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        user.setNickname(registerRequest.getNickname());
        user.setGender(registerRequest.getGender());
        user.setBirthday(registerRequest.getBirthday());
        
        // 동의 항목 저장
        user.setServiceAgree(registerRequest.isServiceAgree());
        user.setPrivacyAgree(registerRequest.isPrivacyAgree());
        user.setMarketingAgree(registerRequest.getMarketingAgree() != null && registerRequest.getMarketingAgree());
        
        // 회원가입 완료: GUEST -> USER로 권한 변경
        user.setRoleType(RoleType.USER);
        
        user.setModifiedAt(LocalDateTime.now());
        userRepository.save(user);

        // 6. 토큰 발급 및 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.generateTokens(username, user.getRoleType(), user.getUserId(), false));
    }

    @Override
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest refreshRequest) {
        // 1. Refresh Token 확인 (Body)
        String refreshTokenStr = refreshRequest.getRefreshToken();
        if (refreshTokenStr == null || refreshTokenStr.isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_REFRESH_TOKEN);
        }

        AuthToken authRefreshToken = tokenProvider.convertAuthToken(refreshTokenStr);

        // 2. Refresh Token 유효성 검사
        if (!authRefreshToken.validate()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 3. Refresh Token 만료 여부 확인
        Claims refreshClaims = authRefreshToken.getTokenClaims();
        if (refreshClaims == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Date expiration = refreshClaims.getExpiration();
        Date now = new Date();
        if (expiration.before(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 4. DB에서 Refresh Token으로 User ID 조회
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByRefreshToken(refreshTokenStr);
        if (userRefreshToken == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String username = userRefreshToken.getUserId();
        
        // 5. Users 조회하여 RoleType 가져오기
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        RoleType roleType = user.getRoleType();

        // 6. 새로운 Access Token 생성
        AuthToken newAccessToken = tokenProvider.createAuthToken(
                username,
                roleType.getCode(),
                user.getUserId(),
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        long validTime = expiration.getTime() - now.getTime();

        // 7. Refresh Token 갱신 로직 (만료 3일 전이면 갱신)
        if (validTime <= THREE_DAYS_MSEC) {
            long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();

            authRefreshToken = tokenProvider.createAuthToken(
                    username,
                    new Date(now.getTime() + refreshTokenExpiry)
            );

            // DB 업데이트
            userRefreshToken.setRefreshToken(authRefreshToken.getToken());
            userRefreshTokenRepository.save(userRefreshToken);
            
            // 갱신된 Refresh Token 문자열 사용
            refreshTokenStr = authRefreshToken.getToken();
        }

        // 8. 응답 반환 (isNewMember 제외)
        long accessTokenExpiresInSeconds = appProperties.getAuth().getTokenExpiry() / 1000;
        long refreshTokenExpiresInSeconds = appProperties.getAuth().getRefreshTokenExpiry() / 1000;
        
        TokenResponse response = new TokenResponse();
        response.setTokenType("Bearer");
        response.setAccessToken(newAccessToken.getToken());
        response.setRefreshToken(refreshTokenStr);
        response.setAccessTokenExpiresIn(accessTokenExpiresInSeconds);
        response.setRefreshTokenExpiresIn(refreshTokenExpiresInSeconds);
        response.setRole(roleType.toString()); // 권한 정보 추가
        // isNewMember는 null로 유지하여 응답에서 제외
        
        return ResponseEntity.ok(response);
    }
    
    @Override
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request,
            @RequestBody RefreshTokenRequest refreshRequest
    ) {
        // 1. Authorization 헤더에서 Access Token 확인
        String accessToken = HeaderUtil.getAccessToken(request);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        AuthToken authToken = tokenProvider.convertAuthToken(accessToken);
        
        // Access Token 유효성 검사
        if (!authToken.validate()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 2. Body에 Refresh Token 확인
        String refreshToken = refreshRequest.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_REFRESH_TOKEN_LOGOUT);
        }

        // 3. DB에서 해당 Refresh Token 삭제
        userRefreshTokenRepository.deleteByRefreshToken(refreshToken);

        // 4. SecurityContext 초기화
        SecurityContextHolder.clearContext();

        // 5. 성공 응답 반환
        return ResponseEntity.ok(Map.of("message", "로그아웃이 완료되었습니다."));
    }

    @Override
    @DeleteMapping("/withdraw")
    @Transactional
    public ResponseEntity<?> withdraw(HttpServletRequest request) {
        // 1. Authorization 헤더에서 Access Token 확인
        String accessToken = HeaderUtil.getAccessToken(request);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        AuthToken authToken = tokenProvider.convertAuthToken(accessToken);
        
        // Access Token 유효성 검사
        if (!authToken.validate()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 2. 토큰에서 사용자명 추출
        Claims claims = authToken.getTokenClaims();
        if (claims == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        String username = claims.getSubject();

        // 3. 사용자 조회
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 사용자 관련 리프레시 토큰 삭제
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByUserId(username);
        if (userRefreshToken != null) {
            userRefreshTokenRepository.delete(userRefreshToken);
        }

        // 5. 사용자 삭제
        userRepository.delete(user);

        // 6. SecurityContext 초기화
        SecurityContextHolder.clearContext();

        // 7. 성공 응답 반환
        return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }
}
