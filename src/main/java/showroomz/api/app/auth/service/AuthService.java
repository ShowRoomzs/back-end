package showroomz.api.app.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.refreshToken.UserRefreshToken;
import showroomz.api.app.auth.refreshToken.UserRefreshTokenRepository;
import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.global.config.properties.AppProperties;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppProperties appProperties;
    private final AuthTokenProvider tokenProvider;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

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
        
        // Access Token 생성
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();
        AuthToken accessToken = tokenProvider.createAuthToken(
                username,
                roleType.getCode(),
                userPk,
                new Date(now.getTime() + accessTokenExpiry)
        );

        // Refresh Token 생성
        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                username,
                new Date(now.getTime() + refreshTokenExpiry)
        );

        // DB 저장/업데이트
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByUserId(username);
        if (userRefreshToken == null) {
            userRefreshToken = new UserRefreshToken(username, refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        } else {
            userRefreshToken.setRefreshToken(refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        }

        // 밀리초를 초로 변환
        long accessTokenExpiresInSeconds = accessTokenExpiry / 1000;
        long refreshTokenExpiresInSeconds = refreshTokenExpiry / 1000;

        return new TokenResponse(
                accessToken.getToken(),
                refreshToken.getToken(),
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                isNewMember,
                roleType.toString() // 권한 정보 추가
        );
    }
}

