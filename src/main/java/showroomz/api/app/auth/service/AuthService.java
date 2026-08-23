package showroomz.api.app.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.auth.DTO.SocialLoginRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.refreshToken.UserRefreshToken;
import showroomz.api.app.auth.refreshToken.UserRefreshTokenRepository;
import showroomz.api.app.auth.service.SocialLoginService.SocialLoginResult;
import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.repository.LoginHistoryRepository;
import showroomz.domain.history.type.LoginStatus;
import showroomz.domain.notification.service.DeviceTokenService;
import showroomz.global.config.properties.AppProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.GeoLocationService;
import showroomz.global.service.GeoLocationService.GeoLocation;

import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppProperties appProperties;
    private final AuthTokenProvider tokenProvider;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final UserRepository userRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final GeoLocationService geoLocationService;
    private final SocialLoginService socialLoginService;
    private final DeviceTokenService deviceTokenService;

    /**
     * 소셜 로그인 공통 진입점.
     *
     * @param request     소셜 로그인 요청
     * @param allowSignup true면 계정 없을 때 가입(GUEST 생성), false면 기존 계정만 조회
     */
    @Transactional
    public SocialLoginResult authenticateSocial(SocialLoginRequest request, boolean allowSignup) {
        if (request.getToken() == null || request.getToken().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_TOKEN);
        }
        if (request.getProviderType() == null || request.getProviderType().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_PROVIDER_TYPE);
        }

        ProviderType providerType;
        try {
            providerType = ProviderType.valueOf(request.getProviderType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_PROVIDER);
        }

        try {
            if (providerType == ProviderType.APPLE && request.getName() != null) {
                return allowSignup
                        ? socialLoginService.loginOrSignup(providerType, request.getToken(), request.getName())
                        : socialLoginService.loginOnly(providerType, request.getToken(), request.getName());
            }
            return allowSignup
                    ? socialLoginService.loginOrSignup(providerType, request.getToken())
                    : socialLoginService.loginOnly(providerType, request.getToken());
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null && message.contains("탈퇴")) {
                throw new BusinessException(ErrorCode.USER_WITHDRAWN);
            }
            if (message != null && message.contains("존재하지 않는 회원")) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            if (message != null && (message.contains("유효하지 않은") || message.contains("토큰") || message.contains("만료"))) {
                throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
            }
            if (message != null && message.contains("이미 다른 계정에서 사용 중인 이메일")) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 토큰 생성 및 DB 저장
     * @param username 사용자명
     * @param roleType 역할 타입
     * @param userPk 사용자 PK
     * @param isNewMember 신규 회원 여부
     * @return TokenResponse 토큰 응답
     */
    public TokenResponse generateTokens(String username, RoleType roleType, Long userPk, boolean isNewMember) {
        Date now = new Date();

        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });

        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();
        AuthToken accessToken = tokenProvider.createAuthToken(
                username,
                roleType.getCode(),
                userPk,
                new Date(now.getTime() + accessTokenExpiry)
        );

        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                username,
                new Date(now.getTime() + refreshTokenExpiry)
        );

        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByUserId(username);
        if (userRefreshToken == null) {
            userRefreshToken = new UserRefreshToken(username, refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        } else {
            userRefreshToken.setRefreshToken(refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        }

        long accessTokenExpiresInSeconds = accessTokenExpiry / 1000;
        long refreshTokenExpiresInSeconds = refreshTokenExpiry / 1000;

        return new TokenResponse(
                accessToken.getToken(),
                refreshToken.getToken(),
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                isNewMember,
                roleType.toString()
        );
    }

    /**
     * 로그인 이력 저장
     * @param userId 사용자 ID
     * @param ip 클라이언트 IP
     * @param userAgent User-Agent 정보
     */
    @Transactional
    public void saveLoginHistory(Long userId, String ip, String userAgent) {
        userRepository.findById(userId).ifPresent(user -> {
            GeoLocation location = geoLocationService.getLocation(ip);

            LoginHistory history = LoginHistory.builder()
                    .user(user)
                    .clientIp(ip)
                    .userAgent(userAgent)
                    .country(location.getCountry())
                    .city(location.getCity())
                    .status(LoginStatus.SUCCESS)
                    .build();

            loginHistoryRepository.save(history);
        });
    }

    /**
     * 로그인 기기의 FCM 토큰 등록 (선택).
     *
     * <p>지금까지 요청의 {@code fcmToken}은 받기만 하고 버려졌다 — 저장할 곳이 없었기 때문인데,
     * 그 상태로는 팔로워 신규 게시물 알림을 보낼 대상 자체가 없다.
     *
     * <p><b>실패해도 로그인을 깨뜨리지 않는다.</b> 알림을 못 받는 것은 불편이지만 로그인이
     * 안 되는 것은 장애다. 토큰이 없는 요청(웹 등)은 조용히 넘어간다.
     *
     * @param platform "ANDROID" / "IOS" / "WEB" — 없거나 모르는 값이면 UNKNOWN으로 남는다
     */
    @Transactional
    public void saveDeviceToken(Long userId, String fcmToken, String platform) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        userRepository.findById(userId)
                .ifPresent(user -> deviceTokenService.register(user, fcmToken, platform));
    }

    /**
     * 로그아웃한 기기의 FCM 토큰 해제 (선택).
     *
     * <p>회원의 토큰을 전부 지우지 않는다 — 폰과 태블릿에 같은 계정으로 로그인해 둔 사람이
     * 한쪽에서 로그아웃했다고 다른 쪽 알림까지 끊기면 안 된다.
     */
    @Transactional
    public void removeDeviceToken(String fcmToken) {
        deviceTokenService.unregister(fcmToken);
    }
}
