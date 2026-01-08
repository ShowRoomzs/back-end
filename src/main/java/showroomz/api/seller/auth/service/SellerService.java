package showroomz.api.seller.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.auth.DTO.RefreshTokenRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.DTO.SellerLoginRequest;
import showroomz.api.seller.auth.DTO.SellerSignUpRequest;
import showroomz.api.seller.auth.refreshToken.SellerRefreshToken;
import showroomz.api.seller.auth.refreshToken.SellerRefreshTokenRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.api.seller.market.service.MarketService;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.config.properties.AppProperties;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository adminRepository;
    private final MarketRepository marketRepository;
    private final MarketService marketService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider tokenProvider;
    private final AppProperties appProperties;
    private final SellerRefreshTokenRepository adminRefreshTokenRepository;

    private final static long THREE_DAYS_MSEC = 259200000;

    @Transactional
    public Map<String, String> registerAdmin(SellerSignUpRequest request) {
        // 1. 비밀번호 일치 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 관리자 테이블에서 이메일 중복 체크
        if (adminRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL_SIGNUP);
        }
        
        // 3. 마켓명 중복 체크
        if (marketRepository.existsByMarketName(request.getMarketName())) {
             throw new BusinessException(ErrorCode.DUPLICATE_MARKET_NAME);
        }

        // 4. Admins 엔티티 생성 (생성자에서 Status = PENDING으로 설정됨)
        LocalDateTime now = LocalDateTime.now();
        Seller admin = new Seller(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getSellerName(),
                request.getSellerContact(),
                now
        );
        
        Seller savedAdmin = adminRepository.save(admin);

        // 5. Market 엔티티 생성 및 URL 자동 할당
        marketService.createMarket(savedAdmin, request.getMarketName(), request.getCsNumber());

        // 토큰을 발급하지 않고 승인 대기 메시지 리턴
        return Map.of("message", "회원가입 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다.");
    }

    // 읽기 전용 트랜잭션으로 설정하여 성능 최적화
    @Transactional(readOnly = true)
    public SellerDto.CheckEmailResponse checkEmailDuplicate(String email) {
        if (adminRepository.existsByEmail(email)) {
            return new SellerDto.CheckEmailResponse(false, "DUPLICATE", "이미 사용 중인 이메일입니다.");
        }
        return new SellerDto.CheckEmailResponse(true, "AVAILABLE", "사용 가능한 이메일입니다.");
    }

    @Transactional
    public TokenResponse login(SellerLoginRequest request) {
        // 1. 관리자 계정 조회
        Seller admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 계정 승인 상태 검증
        if (admin.getStatus() == SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_APPROVED);
        }
        if (admin.getStatus() == SellerStatus.REJECTED) {
            // 반려된 계정일 경우, 반려 사유 유무에 따라 다른 ErrorCode 사용
            String rejectionReason = admin.getRejectionReason();
            log.info("🔍 반려된 계정 로그인 시도 - 이메일: {}, 반려 사유: '{}'", admin.getEmail(), rejectionReason);
            if (rejectionReason != null && !rejectionReason.isBlank()) {
                log.info("✅ 반려 사유 포함하여 예외 발생 - ACCOUNT_REJECTED_WITH_REASON");
                throw new BusinessException(ErrorCode.ACCOUNT_REJECTED_WITH_REASON, rejectionReason);
            }
            log.info("⚠️ 반려 사유 없음 - ACCOUNT_REJECTED 사용");
            throw new BusinessException(ErrorCode.ACCOUNT_REJECTED);
        }

        // 4. 토큰 생성 및 반환 (공통 메서드 사용)
        return issueTokenResponse(admin);
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
        SellerRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(email);
        
        if (adminRefreshToken == null || !adminRefreshToken.getRefreshToken().equals(refreshTokenStr)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 5. Admin 정보 조회
        Seller admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 6. 새로운 Access Token 생성 (공통 메서드 사용)
        AuthToken newAccessToken = createAccessToken(admin, now);
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();

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

        // 8. 응답 반환 (마지막 인자에 role 추가)
        return new TokenResponse(
                newAccessToken.getToken(),
                refreshTokenStr,
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                false,
                admin.getRoleType().toString() // "ADMIN" 또는 "SELLER" 문자열 반환
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
        SellerRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(email);
        if (adminRefreshToken != null) {
            adminRefreshTokenRepository.delete(adminRefreshToken);
        }
    }

    @Transactional
    public void withdraw(String accessTokenStr) {
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

        // 3. 관리자 정보 조회
        Seller admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. Refresh Token 삭제
        SellerRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(email);
        if (adminRefreshToken != null) {
            adminRefreshTokenRepository.delete(adminRefreshToken);
        }

        // 5. Market 삭제
        marketRepository.findBySeller(admin).ifPresent(marketRepository::delete);

        // 6. Admin 삭제
        adminRepository.delete(admin);
    }

    /**
     * 공통 메서드: Access Token 생성
     * 슈퍼 관리자(super) 계정만 ADMIN 권한을 유지하고, 나머지는 모두 SELLER 권한으로 설정
     */
    private AuthToken createAccessToken(Seller admin, Date now) {
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();
        
        // 슈퍼 관리자 계정만 ADMIN 권한 유지, 나머지는 모두 SELLER로 설정
        String roleCode;
        if ("super".equals(admin.getEmail()) && admin.getRoleType() == RoleType.ADMIN) {
            roleCode = RoleType.ADMIN.getCode();
        } else {
            roleCode = RoleType.SELLER.getCode();
        }
        
        return tokenProvider.createAuthToken(
                admin.getEmail(),
                roleCode,
                admin.getId(),
                new Date(now.getTime() + accessTokenExpiry)
        );
    }

    /**
     * 공통 메서드: 토큰 발급 및 저장 (로그인, 회원가입용)
     */
    private TokenResponse issueTokenResponse(Seller admin) {
        Date now = new Date();

        // 1. Access Token 생성
        AuthToken accessToken = createAccessToken(admin, now);
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();

        // 2. Refresh Token 생성
        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                admin.getEmail(),
                new Date(now.getTime() + refreshTokenExpiry)
        );

        // 3. Refresh Token DB 저장 (없으면 생성, 있으면 업데이트)
        SellerRefreshToken adminRefreshToken = adminRefreshTokenRepository.findByAdminEmail(admin.getEmail());
        if (adminRefreshToken == null) {
            adminRefreshToken = new SellerRefreshToken(admin.getEmail(), refreshToken.getToken());
            adminRefreshTokenRepository.saveAndFlush(adminRefreshToken);
        } else {
            adminRefreshToken.setRefreshToken(refreshToken.getToken());
            adminRefreshTokenRepository.saveAndFlush(adminRefreshToken);
        }

        // 4. 응답 생성 (마지막 인자에 role 추가)
        long accessTokenExpiresInSeconds = accessTokenExpiry / 1000;
        long refreshTokenExpiresInSeconds = refreshTokenExpiry / 1000;

        return new TokenResponse(
                accessToken.getToken(),
                refreshToken.getToken(),
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                false,
                admin.getRoleType().toString() // 여기서 권한을 넘겨줌
        );
    }

    /**
     * 관리자 승인 처리 메서드 (SuperAdminController에서 사용)
     */
}