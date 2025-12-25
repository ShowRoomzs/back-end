package showroomz.oauthlogin.auth;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import showroomz.config.properties.AppProperties;
import showroomz.oauthlogin.auth.DTO.AuthReqModel;
import showroomz.oauthlogin.auth.DTO.RefreshTokenRequest; // [추가]
import showroomz.oauthlogin.auth.DTO.SignUpRequest;
import showroomz.oauthlogin.auth.DTO.SocialLoginRequest;
import showroomz.oauthlogin.auth.DTO.TokenResponse;      // [추가]
import showroomz.oauthlogin.oauth.entity.ProviderType;
import showroomz.oauthlogin.oauth.entity.RoleType;
import showroomz.oauthlogin.oauth.entity.UserPrincipal;
import showroomz.oauthlogin.oauth.exception.BadRequestException;
import showroomz.oauthlogin.oauth.service.SocialLoginService;
import showroomz.oauthlogin.oauth.service.SocialLoginService.SocialLoginResult;
import showroomz.oauthlogin.oauth.token.AuthToken;
import showroomz.oauthlogin.oauth.token.AuthTokenProvider;
import showroomz.oauthlogin.user.User;
import showroomz.oauthlogin.utils.HeaderUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Auth API")
public class AuthController {

    private final AppProperties appProperties;
    private final AuthTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SocialLoginService socialLoginService;
    
    private final static long THREE_DAYS_MSEC = 259200000;

    @PostMapping("/{provider}/social")
    public TokenResponse socialLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable String provider,
            @RequestBody @Valid SocialLoginRequest socialLoginRequest
    ) {
        try {
            ProviderType providerType = convertToProviderType(provider);

            SocialLoginResult result = socialLoginService.loginOrSignup(
                    providerType,
                    socialLoginRequest.getAccessToken()
            );

            return generateTokens(result.getUser().getUserId(), result.getUser().getRoleType(), result.isNewMember());

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "소셜 로그인 실패: " + e.getMessage());
        }
    }
    
    /**
     * 경로 변수의 provider 문자열을 ProviderType enum으로 변환
     */
    private ProviderType convertToProviderType(String provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider는 필수입니다.");
        }
        
        String providerUpper = provider.toUpperCase();
        switch (providerUpper) {
            case "네이버":
            case "NAVER":
                return ProviderType.NAVER;
            case "구글":
            case "GOOGLE":
                return ProviderType.GOOGLE;
            case "카카오":
            case "KAKAO":
                return ProviderType.KAKAO;
            case "애플":
            case "APPLE":
                return ProviderType.APPLE;
            default:
                throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자입니다: " + provider);
        }
    }
    
    @PostMapping("/signup")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "해당 ID의 유저가 존재하지 않습니다."),
        })
    public Map<String, String> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) {
        if (userRepository.existsByUserId(signUpRequest.getUserId())) {
            throw new BadRequestException("이미 사용 중인 아이디입니다.");
        }

        User user = new User(
            signUpRequest.getUserId(),
            signUpRequest.getUsername(),
            signUpRequest.getEmail(),
            "N",
            "",
            ProviderType.LOCAL,
            RoleType.USER,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        userRepository.save(user);

        return Map.of("message", "회원가입이 완료되었습니다.");
    }
    
    @PostMapping("/login")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "해당 ID의 유저가 존재하지 않습니다."),
        })
    public TokenResponse login(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody AuthReqModel authReqModel
    ) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authReqModel.getId(),
                            authReqModel.getPassword()
                    )
            );
    
            String userId = authReqModel.getId();
            SecurityContextHolder.getContext().setAuthentication(authentication);
            RoleType roleType = ((UserPrincipal) authentication.getPrincipal()).getRoleType();
    
            // 토큰 생성 및 저장
            return generateTokens(userId, roleType, false);

        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 실패: 아이디 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "로그인 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * Access Token 재발급
     * - Refresh Token을 Body로 받아서 검증 후 새로운 Access Token (필요 시 Refresh Token도) 반환
     */
    @PostMapping("/refresh")
    public TokenResponse refreshToken(
            HttpServletRequest request, 
            @RequestBody RefreshTokenRequest refreshRequest
    ) {
        // 1. Access Token 확인 (Header)
        String accessToken = HeaderUtil.getAccessToken(request);
        AuthToken authToken = tokenProvider.convertAuthToken(accessToken);
        
        // Access Token 유효성 검사 (만료 여부 상관없이 구조적 유효성)
        if (!authToken.validate()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token.");
        }

        // 2. Access Token에서 유저 정보 추출
        Claims claims = authToken.getExpiredTokenClaims();
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not expired token yet.");
        }

        String userId = claims.getSubject();
        RoleType roleType = RoleType.of(claims.get("role", String.class));

        // 3. Refresh Token 확인 (Body)
        String refreshTokenStr = refreshRequest.getRefreshToken();
        AuthToken authRefreshToken = tokenProvider.convertAuthToken(refreshTokenStr);

        // Refresh Token 유효성 검사
        if (!authRefreshToken.validate()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        // 4. DB에서 User ID와 Refresh Token 일치 여부 확인
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByUserIdAndRefreshToken(userId, refreshTokenStr);
        if (userRefreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        // 5. 새로운 Access Token 생성
        Date now = new Date();
        AuthToken newAccessToken = tokenProvider.createAuthToken(
                userId,
                roleType.getCode(),
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        long validTime = authRefreshToken.getTokenClaims().getExpiration().getTime() - now.getTime();

        // 6. Refresh Token 갱신 로직 (만료 3일 전이면 갱신)
        if (validTime <= THREE_DAYS_MSEC) {
            long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();

            authRefreshToken = tokenProvider.createAuthToken(
                    appProperties.getAuth().getTokenSecret(),
                    new Date(now.getTime() + refreshTokenExpiry)
            );

            // DB 업데이트
            userRefreshToken.setRefreshToken(authRefreshToken.getToken());
            userRefreshTokenRepository.save(userRefreshToken);
            
            // 갱신된 Refresh Token 문자열 사용
            refreshTokenStr = authRefreshToken.getToken();
        }

        return new TokenResponse(newAccessToken.getToken(), refreshTokenStr, appProperties.getAuth().getTokenExpiry(), false);
    }
    
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody RefreshTokenRequest refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();

        if (refreshToken != null) {
            // DB에서 해당 Refresh Token 삭제
            userRefreshTokenRepository.deleteByRefreshToken(refreshToken);
        }

        // SecurityContext 초기화
        SecurityContextHolder.clearContext();

        return Map.of("message", "로그아웃이 완료되었습니다.");
    }
    
    // 토큰 생성 및 DB 저장 헬퍼 메소드
    private TokenResponse generateTokens(String userId, RoleType roleType, boolean isNewMember) {
        Date now = new Date();
        
        // Access Token 생성
        AuthToken accessToken = tokenProvider.createAuthToken(
                userId,
                roleType.getCode(),
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        // Refresh Token 생성
        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                appProperties.getAuth().getTokenSecret(),
                new Date(now.getTime() + refreshTokenExpiry)
        );

        // DB 저장/업데이트
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByUserId(userId);
        if (userRefreshToken == null) {
            userRefreshToken = new UserRefreshToken(userId, refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        } else {
            userRefreshToken.setRefreshToken(refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        }

        return new TokenResponse(accessToken.getToken(), refreshToken.getToken(), appProperties.getAuth().getTokenExpiry(), isNewMember);
    }
}