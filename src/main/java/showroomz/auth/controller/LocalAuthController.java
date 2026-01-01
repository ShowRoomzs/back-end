package showroomz.auth.controller;

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

import showroomz.auth.DTO.*;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.entity.UserPrincipal;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.auth.service.AuthService;
import showroomz.user.DTO.NicknameCheckResponse;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;
import showroomz.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/local")
@RequiredArgsConstructor
public class LocalAuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @PostMapping("/signup")
    public Map<String, String> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) {
        String loginId = signUpRequest.getUsername(); // username이 로그인 ID
        
        // 1. 아이디 중복 체크
        if (userRepository.existsByUsername(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        // 2. 이메일 중복 체크
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 3. 닉네임 검증
        String nickname = signUpRequest.getNickname();
        NicknameCheckResponse nicknameCheck = userService.checkNickname(nickname);
        if (!nicknameCheck.getIsAvailable()) {
            if ("DUPLICATE".equals(nicknameCheck.getCode())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            } else if ("INVALID_FORMAT".equals(nicknameCheck.getCode()) || 
                       "INVALID_LENGTH".equals(nicknameCheck.getCode())) {
                throw new BusinessException(ErrorCode.INVALID_NICKNAME_FORMAT);
            } else if ("PROFANITY".equals(nicknameCheck.getCode())) {
                throw new BusinessException(ErrorCode.PROFANITY_DETECTED);
            }
        }

        Users user = new Users(
            loginId,  // username (로그인 ID)
            nickname, // nickname
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
    
            String username = authReqModel.getId();
            SecurityContextHolder.getContext().setAuthentication(authentication);
            RoleType roleType = ((UserPrincipal) authentication.getPrincipal()).getRoleType();
    
            // userId 조회
            Users user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    
            // 토큰 생성 및 저장
            return authService.generateTokens(username, roleType, user.getUserId(), false);

        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 실패: 아이디 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "로그인 처리 중 오류가 발생했습니다.");
        }
    }

}

