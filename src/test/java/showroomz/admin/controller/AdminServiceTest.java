package showroomz.admin.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import showroomz.Market.entity.Market;
import showroomz.admin.DTO.AdminLoginRequest;
import showroomz.admin.DTO.AdminSignUpRequest;
import showroomz.admin.entity.Admin;
import showroomz.admin.refreshToken.AdminRefreshToken;
import showroomz.admin.refreshToken.AdminRefreshTokenRepository;
import showroomz.admin.repository.AdminRepository;
import showroomz.admin.service.AdminService;
import showroomz.auth.DTO.RefreshTokenRequest;
import showroomz.auth.DTO.TokenResponse;
import showroomz.auth.entity.RoleType;
import showroomz.auth.exception.BusinessException;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.config.properties.AppProperties;
import showroomz.global.error.exception.ErrorCode;
import showroomz.user.repository.MarketRepository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Admin Service 단위 테스트")
class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenProvider tokenProvider;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.Auth authProperties;

    @Mock
    private AdminRefreshTokenRepository adminRefreshTokenRepository;

    @Nested
    @DisplayName("관리자 회원가입")
    class RegisterAdmin {

        @Test
        @DisplayName("성공: 정상적인 정보로 가입 요청 시 Admin과 Market이 저장된다")
        void success() {
            // given
            AdminSignUpRequest request = createSignUpRequest();
            given(adminRepository.existsByEmail(request.getEmail())).willReturn(false);
            given(marketRepository.existsByMarketName(request.getMarketName())).willReturn(false);
            given(passwordEncoder.encode(request.getPassword())).willReturn("encodedPassword");
            
            Admin savedAdmin = createAdmin();
            given(adminRepository.save(any(Admin.class))).willReturn(savedAdmin);

            // when
            adminService.registerAdmin(request);

            // then
            verify(adminRepository).save(any(Admin.class));
            verify(marketRepository).save(any(Market.class));
        }

        @Test
        @DisplayName("실패: 비밀번호와 비밀번호 확인이 일치하지 않으면 예외 발생")
        void fail_password_mismatch() {
            // given
            AdminSignUpRequest request = createSignUpRequest();
            request.setPasswordConfirm("DifferentPassword");

            // when & then
            assertThatThrownBy(() -> adminService.registerAdmin(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        }

        @Test
        @DisplayName("실패: 이미 존재하는 이메일이면 예외 발생")
        void fail_duplicate_email() {
            // given
            AdminSignUpRequest request = createSignUpRequest();
            given(adminRepository.existsByEmail(request.getEmail())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> adminService.registerAdmin(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL_SIGNUP);
        }

        @Test
        @DisplayName("실패: 이미 존재하는 마켓명이면 예외 발생")
        void fail_duplicate_market_name() {
            // given
            AdminSignUpRequest request = createSignUpRequest();
            given(adminRepository.existsByEmail(request.getEmail())).willReturn(false);
            given(marketRepository.existsByMarketName(request.getMarketName())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> adminService.registerAdmin(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_MARKET_NAME);
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공: 올바른 정보로 로그인 시 토큰이 발급된다")
        void success() {
            // given
            AdminLoginRequest request = new AdminLoginRequest();
            request.setEmail("admin@test.com");
            request.setPassword("password");

            Admin admin = createAdmin();
            given(adminRepository.findByEmail(request.getEmail())).willReturn(Optional.of(admin));
            given(passwordEncoder.matches(request.getPassword(), admin.getPassword())).willReturn(true);

            // Mocking Token Properties
            given(appProperties.getAuth()).willReturn(authProperties);
            given(authProperties.getTokenExpiry()).willReturn(3600000L);
            given(authProperties.getRefreshTokenExpiry()).willReturn(1209600000L);

            // Mocking Token Creation
            AuthToken accessToken = mock(AuthToken.class);
            AuthToken refreshToken = mock(AuthToken.class);
            given(accessToken.getToken()).willReturn("accessToken");
            given(refreshToken.getToken()).willReturn("refreshToken");

            given(tokenProvider.createAuthToken(eq(admin.getEmail()), eq(RoleType.ADMIN.getCode()), eq(admin.getAdminId()), any(Date.class)))
                    .willReturn(accessToken);
            given(tokenProvider.createAuthToken(eq(admin.getEmail()), any(Date.class)))
                    .willReturn(refreshToken);

            // when
            TokenResponse response = adminService.login(request);

            // then
            assertThat(response.getAccessToken()).isEqualTo("accessToken");
            assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
            verify(adminRefreshTokenRepository).saveAndFlush(any(AdminRefreshToken.class));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 이메일이면 예외 발생")
        void fail_user_not_found() {
            // given
            AdminLoginRequest request = new AdminLoginRequest();
            request.setEmail("unknown@test.com");
            request.setPassword("password");

            given(adminRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("실패: 비밀번호가 일치하지 않으면 예외 발생")
        void fail_invalid_password() {
            // given
            AdminLoginRequest request = new AdminLoginRequest();
            request.setEmail("admin@test.com");
            request.setPassword("wrongPassword");

            Admin admin = createAdmin();
            given(adminRepository.findByEmail(request.getEmail())).willReturn(Optional.of(admin));
            given(passwordEncoder.matches(request.getPassword(), admin.getPassword())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> adminService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class RefreshToken {

        @Test
        @DisplayName("성공: 유효한 Refresh Token으로 요청 시 Access Token이 재발급된다")
        void success() {
            // given
            String refreshTokenStr = "validRefreshToken";
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(refreshTokenStr);

            AuthToken authRefreshToken = mock(AuthToken.class);
            Claims claims = mock(Claims.class);

            given(tokenProvider.convertAuthToken(refreshTokenStr)).willReturn(authRefreshToken);
            given(authRefreshToken.validate()).willReturn(true);
            given(authRefreshToken.getTokenClaims()).willReturn(claims);
            
            // 만료되지 않음 (3일보다 충분히 크게 설정하여 갱신이 일어나지 않도록)
            given(claims.getExpiration()).willReturn(new Date(System.currentTimeMillis() + 260200000L)); // 3일 + 약 1일
            given(claims.getSubject()).willReturn("admin@test.com");

            // DB 토큰 확인
            AdminRefreshToken dbToken = new AdminRefreshToken("admin@test.com", refreshTokenStr);
            given(adminRefreshTokenRepository.findByAdminEmail("admin@test.com")).willReturn(dbToken);

            Admin admin = createAdmin();
            given(adminRepository.findByEmail("admin@test.com")).willReturn(Optional.of(admin));

            // 새 토큰 생성 Mocking
            given(appProperties.getAuth()).willReturn(authProperties);
            given(authProperties.getTokenExpiry()).willReturn(3600000L);
            given(authProperties.getRefreshTokenExpiry()).willReturn(1209600000L);

            AuthToken newAccessToken = mock(AuthToken.class);
            given(newAccessToken.getToken()).willReturn("newAccessToken");
            given(tokenProvider.createAuthToken(eq(admin.getEmail()), eq(RoleType.ADMIN.getCode()), eq(admin.getAdminId()), any(Date.class)))
                    .willReturn(newAccessToken);

            // when
            TokenResponse response = adminService.refreshToken(request);

            // then
            assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
            // 갱신 기간이 아니므로 Refresh Token은 그대로
            assertThat(response.getRefreshToken()).isEqualTo(refreshTokenStr);
        }

        @Test
        @DisplayName("실패: DB에 저장된 Refresh Token과 다르면 예외 발생")
        void fail_token_mismatch() {
            // given
            String refreshTokenStr = "inputToken";
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(refreshTokenStr);

            AuthToken authRefreshToken = mock(AuthToken.class);
            Claims claims = mock(Claims.class);

            given(tokenProvider.convertAuthToken(refreshTokenStr)).willReturn(authRefreshToken);
            given(authRefreshToken.validate()).willReturn(true);
            given(authRefreshToken.getTokenClaims()).willReturn(claims);
            given(claims.getExpiration()).willReturn(new Date(System.currentTimeMillis() + 100000));
            given(claims.getSubject()).willReturn("admin@test.com");

            // DB에는 다른 토큰이 저장되어 있음
            AdminRefreshToken dbToken = new AdminRefreshToken("admin@test.com", "differentToken");
            given(adminRefreshTokenRepository.findByAdminEmail("admin@test.com")).willReturn(dbToken);

            // when & then
            assertThatThrownBy(() -> adminService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("실패: 만료된 Refresh Token이면 예외 발생")
        void fail_expired_token() {
            // given
            String refreshTokenStr = "expiredToken";
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(refreshTokenStr);

            AuthToken authRefreshToken = mock(AuthToken.class);
            Claims claims = mock(Claims.class);

            given(tokenProvider.convertAuthToken(refreshTokenStr)).willReturn(authRefreshToken);
            given(authRefreshToken.validate()).willReturn(true);
            given(authRefreshToken.getTokenClaims()).willReturn(claims);
            
            // 이미 만료된 시간 설정
            given(claims.getExpiration()).willReturn(new Date(System.currentTimeMillis() - 1000));

            // when & then
            assertThatThrownBy(() -> adminService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("성공: 로그아웃 시 Refresh Token이 삭제된다")
        void success() {
            // given
            String accessTokenStr = "validAccessToken";
            String refreshTokenStr = "validRefreshToken";

            AuthToken accessToken = mock(AuthToken.class);
            Claims claims = mock(Claims.class);

            given(tokenProvider.convertAuthToken(accessTokenStr)).willReturn(accessToken);
            given(accessToken.validate()).willReturn(true);
            given(accessToken.getTokenClaims()).willReturn(claims);
            given(claims.getSubject()).willReturn("admin@test.com");

            AdminRefreshToken dbToken = new AdminRefreshToken("admin@test.com", refreshTokenStr);
            given(adminRefreshTokenRepository.findByAdminEmail("admin@test.com")).willReturn(dbToken);

            // when
            adminService.logout(accessTokenStr, refreshTokenStr);

            // then
            verify(adminRefreshTokenRepository).delete(dbToken);
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("성공: 탈퇴 시 Admin, Market, Refresh Token이 모두 삭제된다")
        void success() {
            // given
            String accessTokenStr = "validAccessToken";
            AuthToken accessToken = mock(AuthToken.class);
            Claims claims = mock(Claims.class);

            given(tokenProvider.convertAuthToken(accessTokenStr)).willReturn(accessToken);
            given(accessToken.validate()).willReturn(true);
            given(accessToken.getTokenClaims()).willReturn(claims);
            given(claims.getSubject()).willReturn("admin@test.com");

            Admin admin = createAdmin();
            given(adminRepository.findByEmail("admin@test.com")).willReturn(Optional.of(admin));

            AdminRefreshToken dbToken = new AdminRefreshToken("admin@test.com", "token");
            given(adminRefreshTokenRepository.findByAdminEmail("admin@test.com")).willReturn(dbToken);

            Market market = new Market(admin, "TestMarket", "010-0000-0000");
            given(marketRepository.findByAdmin(admin)).willReturn(Optional.of(market));

            // when
            adminService.withdraw(accessTokenStr);

            // then
            verify(adminRefreshTokenRepository).delete(dbToken);
            verify(marketRepository).delete(market);
            verify(adminRepository).delete(admin);
        }
    }

    private AdminSignUpRequest createSignUpRequest() {
        AdminSignUpRequest request = new AdminSignUpRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password");
        request.setPasswordConfirm("password");
        request.setMarketName("TestMarket");
        request.setSellerName("Seller");
        request.setSellerContact("010-1234-5678");
        request.setCsNumber("02-1234-5678");
        return request;
    }

    private Admin createAdmin() {
        return new Admin(
                "admin@test.com",
                "encodedPassword",
                "Seller",
                "010-1234-5678",
                LocalDateTime.now()
        );
    }
}