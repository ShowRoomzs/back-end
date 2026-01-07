package showroomz.api.seller.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.auth.DTO.RefreshTokenRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.DTO.SellerLoginRequest;
import showroomz.api.seller.auth.DTO.SellerSignUpRequest;
import showroomz.api.seller.auth.service.SellerService;
import showroomz.api.seller.docs.SellerAuthControllerDocs;
import showroomz.global.utils.HeaderUtil;

import java.util.Map;

@RestController
@RequestMapping("/v1/seller/auth")
@RequiredArgsConstructor
public class SellerAuthController implements SellerAuthControllerDocs {

    private final SellerService adminService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody SellerSignUpRequest request) {
        Map<String, String> response = adminService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/check-email")
    public ResponseEntity<SellerDto.CheckEmailResponse> checkEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(adminService.checkEmailDuplicate(email));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody SellerLoginRequest request) {
        TokenResponse tokenResponse = adminService.login(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = adminService.refreshToken(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, @RequestBody RefreshTokenRequest refreshTokenRequest) {
        // Authorization 헤더에서 Access Token 추출
        String accessToken = HeaderUtil.getAccessToken(request);
        
        // 서비스 로그아웃 로직 수행
        adminService.logout(accessToken, refreshTokenRequest.getRefreshToken());
        
        // SecurityContext 초기화
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of("message", "로그아웃이 완료되었습니다."));
    }

    @Override
    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(HttpServletRequest request) {
        // 1. Authorization 헤더에서 Access Token 추출
        String accessToken = HeaderUtil.getAccessToken(request);
        
        // 2. 서비스 탈퇴 로직 수행
        adminService.withdraw(accessToken);
        
        // 3. SecurityContext 초기화 (로그인 상태 해제)
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of("message", "관리자 회원 탈퇴가 완료되었습니다."));
    }
}