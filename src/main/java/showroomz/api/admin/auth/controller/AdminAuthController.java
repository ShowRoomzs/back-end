package showroomz.api.admin.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.DTO.RefreshTokenRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.seller.auth.DTO.SellerLoginRequest;
import showroomz.api.seller.auth.service.SellerService;
import showroomz.global.utils.HeaderUtil;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final SellerService sellerService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody SellerLoginRequest request) {
        // [핵심] 관리자 전용 로그인 메서드 호출
        TokenResponse tokenResponse = sellerService.loginAdmin(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        // Refresh 로직은 공유
        TokenResponse tokenResponse = sellerService.refreshToken(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, @RequestBody RefreshTokenRequest refreshTokenRequest) {
        String accessToken = HeaderUtil.getAccessToken(request);
        // Logout 로직은 공유
        sellerService.logout(accessToken, refreshTokenRequest.getRefreshToken());
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "관리자 로그아웃이 완료되었습니다."));
    }
}
