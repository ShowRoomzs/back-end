package showroomz.admin.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.Market.entity.Market;
import showroomz.user.repository.MarketRepository;
import showroomz.admin.DTO.AdminLoginRequest;
import showroomz.admin.DTO.AdminSignUpRequest;
import showroomz.admin.entity.Admin;
import showroomz.admin.repository.AdminRepository;
import showroomz.auth.DTO.RefreshTokenRequest;
import showroomz.auth.DTO.TokenResponse;
import showroomz.auth.exception.BusinessException;
import showroomz.admin.refreshToken.AdminRefreshToken;
import showroomz.admin.refreshToken.AdminRefreshTokenRepository;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.config.properties.AppProperties;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final MarketRepository marketRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider tokenProvider;
    private final AppProperties appProperties;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;

    private final static long THREE_DAYS_MSEC = 259200000;

    @Transactional
    public void registerAdmin(AdminSignUpRequest request) {
        // 1. 비밀번호 일치 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 관리자 테이블에서 이메일 중복 체크 (일반 유저 테이블은 신경 안 씀)
        if (adminRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL_SIGNUP);
        }
        
        // 3. 마켓명 중복 체크
        if (marketRepository.existsByMarketName(request.getMarketName())) {
             throw new BusinessException(ErrorCode.DUPLICATE_MARKET_NAME);
        }

        // 4. Admins 엔티티 생성
        LocalDateTime now = LocalDateTime.now();
        Admin admin = new Admin(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getSellerName(),
                request.getSellerContact(),
                now
        );
        
        Admin savedAdmin = adminRepository.save(admin);

        // 5. Market 엔티티 생성 (Admins 연결)
        Market market = new Market(
                savedAdmin,
                request.getMarketName(),
                request.getCsNumber()
        );

        marketRepository.save(market);
    }

    // 읽기 전용 트랜잭션으로 설정하여 성능 최적화
    @Transactional(readOnly = true)
    public boolean checkEmailDuplicate(String email) {
        return adminRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean checkMarketNameDuplicate(String marketName) {
        return marketRepository.existsByMarketName(marketName);
    }

    @Transactional
    public TokenResponse login(AdminLoginRequest request) {
        // 1. 관리자 계정 조회
        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 토큰 생성 (RoleType.ADMIN 권한 부여, userId 포함)
        Date now = new Date();
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();
        AuthToken accessToken = tokenProvider.createAuthToken(
                admin.getEmail(),
                admin.getRoleType().getCode(),
                admin.getAdminId(),
                new Date(now.getTime() + accessTokenExpiry)
        );

        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                admin.getEmail(),
                new Date(now.getTime() + refreshTokenExpiry)
        );

        // 4. Refresh Token DB 저장 (Admin 전용 테이블)
        AdminRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(admin.getEmail());
        if (adminRefreshToken == null) {
            adminRefreshToken = new AdminRefreshToken(admin.getEmail(), refreshToken.getToken());
            adminRefreshTokenRepository.saveAndFlush(adminRefreshToken);
        } else {
            adminRefreshToken.setRefreshToken(refreshToken.getToken());
            adminRefreshTokenRepository.saveAndFlush(adminRefreshToken);
        }

        // 밀리초를 초로 변환
        long accessTokenExpiresInSeconds = accessTokenExpiry / 1000;
        long refreshTokenExpiresInSeconds = refreshTokenExpiry / 1000;

        return new TokenResponse(
                accessToken.getToken(),
                refreshToken.getToken(),
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                false // 관리자는 항상 기존 회원 취급 (신규 여부 의미 없음)
        );
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        // 1. Refresh Token 값 확인
        String refreshTokenStr = request.getRefreshToken();
        if (refreshTokenStr == null || refreshTokenStr.isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_REFRESH_TOKEN);
        }

        AuthToken authRefreshToken = tokenProvider.convertAuthToken(refreshTokenStr);

        // 2. Refresh Token 유효성 검사
        if (!authRefreshToken.validate()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 3. 만료 여부 확인
        Claims claims = authRefreshToken.getTokenClaims();
        if (claims == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        
        Date expiration = claims.getExpiration();
        Date now = new Date();
        if (expiration.before(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 4. DB에서 Admin Email로 토큰 조회 및 일치 여부 확인
        String email = claims.getSubject();
        AdminRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(email);
        
        if (adminRefreshToken == null || !adminRefreshToken.getRefreshToken().equals(refreshTokenStr)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 5. Admin 정보 조회
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 6. 새로운 Access Token 생성
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();
        AuthToken newAccessToken = tokenProvider.createAuthToken(
                admin.getEmail(),
                admin.getRoleType().getCode(),
                admin.getAdminId(),
                new Date(now.getTime() + accessTokenExpiry)
        );

        // 7. Refresh Token 갱신 로직 (만료 3일 전이면 갱신)
        long validTime = expiration.getTime() - now.getTime();
        if (validTime <= THREE_DAYS_MSEC) {
            long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
            authRefreshToken = tokenProvider.createAuthToken(
                    admin.getEmail(),
                    new Date(now.getTime() + refreshTokenExpiry)
            );

            // DB 업데이트
            adminRefreshToken.setRefreshToken(authRefreshToken.getToken());
            adminRefreshTokenRepository.save(adminRefreshToken);
            
            refreshTokenStr = authRefreshToken.getToken();
        }

        long accessTokenExpiresInSeconds = accessTokenExpiry / 1000;
        long refreshTokenExpiresInSeconds = appProperties.getAuth().getRefreshTokenExpiry() / 1000;

        // 8. 응답 반환
        return new TokenResponse(
                newAccessToken.getToken(),
                refreshTokenStr,
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                false
        );
    }

    @Transactional
    public void logout(String accessTokenStr, String refreshTokenStr) {
        // 1. Access Token 유효성 검사
        if (accessTokenStr == null || accessTokenStr.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        AuthToken accessToken = tokenProvider.convertAuthToken(accessTokenStr);
        if (!accessToken.validate()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 2. 토큰에서 이메일 추출
        Claims claims = accessToken.getTokenClaims();
        if (claims == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        String email = claims.getSubject();

        // 3. DB에서 Refresh Token 삭제
        // Admin은 Email 당 하나의 Refresh Token만 유지하므로 Email로 찾아서 삭제
        AdminRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(email);
        if (adminRefreshToken != null) {
            adminRefreshTokenRepository.delete(adminRefreshToken);
        }
    }
}