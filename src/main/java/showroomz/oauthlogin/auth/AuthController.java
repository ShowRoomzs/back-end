package showroomz.oauthlogin.auth;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import showroomz.oauthlogin.auth.DTO.ErrorResponse;
import showroomz.oauthlogin.auth.DTO.RegisterRequest;
import showroomz.oauthlogin.auth.DTO.RefreshTokenRequest;
import showroomz.oauthlogin.auth.DTO.SignUpRequest;
import showroomz.oauthlogin.auth.DTO.SocialLoginRequest;
import showroomz.oauthlogin.auth.DTO.TokenResponse;
import showroomz.oauthlogin.auth.DTO.ValidationErrorResponse;
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

    @PostMapping("/social/login")
    public ResponseEntity<?> socialLogin(@RequestBody @Valid SocialLoginRequest socialLoginRequest) {
        try {
            // 1. 필수 파라미터 검증
            if (socialLoginRequest.getToken() == null || socialLoginRequest.getToken().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("BAD_REQUEST", "token은 필수 입력값입니다."));
            }

            if (socialLoginRequest.getProviderType() == null || socialLoginRequest.getProviderType().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("BAD_REQUEST", "providerType은 필수 입력값입니다."));
            }

            // 2. ProviderType 변환
            ProviderType providerType;
            try {
                providerType = ProviderType.valueOf(socialLoginRequest.getProviderType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("INVALID_SOCIAL_PROVIDER", "지원하지 않는 소셜 공급자입니다."));
            }

            // 3. 소셜 로그인 처리 (애플의 경우 name 전달)
            SocialLoginResult result;
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

            // 4. 신규 회원인 경우 registerToken 반환 (5분 유효)
            if (result.isNewMember()) {
                Date now = new Date();
                long registerTokenExpiry = 5 * 60 * 1000; // 5분
                AuthToken registerToken = tokenProvider.createAuthToken(
                        result.getUser().getUserId(),
                        new Date(now.getTime() + registerTokenExpiry)
                );
                return ResponseEntity.ok(new TokenResponse(registerToken.getToken()));
            }

            // 5. 기존 회원인 경우 일반 토큰 반환
            return ResponseEntity.ok(generateTokens(
                    result.getUser().getUserId(),
                    result.getUser().getRoleType(),
                    false
            ));

        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message.contains("유효하지 않은") || message.contains("토큰") || message.contains("만료")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "유효하지 않은 액세스 토큰입니다."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", message));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(HttpServletRequest request, @RequestBody @Valid RegisterRequest registerRequest) {
        try {
            // 1. registerToken 검증
            String registerTokenStr = HeaderUtil.getAccessToken(request);
            if (registerTokenStr == null || registerTokenStr.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요."));
            }

            AuthToken registerToken = tokenProvider.convertAuthToken(registerTokenStr);
            if (!registerToken.validate()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요."));
            }

            Claims claims = registerToken.getTokenClaims();
            if (claims == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요."));
            }

            String userId = claims.getSubject();

            // 2. 닉네임 중복 체크
            if (userRepository.existsByNickname(registerRequest.getNickname())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."));
            }

            // 3. 닉네임 부적절한 단어 체크
            if (containsInappropriateWord(registerRequest.getNickname())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ValidationErrorResponse("INVALID_INPUT", "입력값이 올바르지 않습니다.",
                                java.util.List.of(new ValidationErrorResponse.FieldError("nickname", "부적절한 단어가 포함되어 있습니다."))));
            }

            // 4. 생년월일 형식 검증 (null이 아닐 때만)
            if (registerRequest.getBirthday() != null && !registerRequest.getBirthday().isEmpty()) {
                if (!registerRequest.getBirthday().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ValidationErrorResponse("INVALID_INPUT", "입력값이 올바르지 않습니다.",
                                    java.util.List.of(new ValidationErrorResponse.FieldError("birthday", "생년월일 형식이 올바르지 않습니다."))));
                }
            }

            // 5. User 조회 및 업데이트
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

            user.setNickname(registerRequest.getNickname());
            user.setGender(registerRequest.getGender());
            user.setBirthday(registerRequest.getBirthday());
            user.setModifiedAt(LocalDateTime.now());
            userRepository.save(user);

            // 6. 토큰 발급 및 반환
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(generateTokens(userId, user.getRoleType(), false));

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
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

    // 닉네임 부적절한 단어 체크
    private boolean containsInappropriateWord(String nickname) {
        // 부적절한 단어 목록 (실제로는 DB나 설정 파일에서 관리하는 것이 좋습니다)
        String[] inappropriateWords = {
            "관리자", "admin", "administrator", "운영자", "operator",
            "시스템", "system", "서버", "server", "테스트", "test"
        };
        
        String lowerNickname = nickname.toLowerCase();
        for (String word : inappropriateWords) {
            if (lowerNickname.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
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

        long accessTokenExpiresInSeconds = appProperties.getAuth().getTokenExpiry() / 1000;
        long refreshTokenExpiresInSeconds = appProperties.getAuth().getRefreshTokenExpiry() / 1000;
        return new TokenResponse(newAccessToken.getToken(), refreshTokenStr, accessTokenExpiresInSeconds, refreshTokenExpiresInSeconds, false);
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
        long accessTokenExpiry = appProperties.getAuth().getTokenExpiry();
        AuthToken accessToken = tokenProvider.createAuthToken(
                userId,
                roleType.getCode(),
                new Date(now.getTime() + accessTokenExpiry)
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

        // 밀리초를 초로 변환
        long accessTokenExpiresInSeconds = accessTokenExpiry / 1000;
        long refreshTokenExpiresInSeconds = refreshTokenExpiry / 1000;

        return new TokenResponse(
                accessToken.getToken(),
                refreshToken.getToken(),
                accessTokenExpiresInSeconds,
                refreshTokenExpiresInSeconds,
                isNewMember
        );
    }
}