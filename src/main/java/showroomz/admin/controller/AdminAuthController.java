package showroomz.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.admin.DTO.AdminLoginRequest;
import showroomz.admin.DTO.AdminSignUpRequest;
import showroomz.admin.service.AdminService;
import showroomz.auth.DTO.TokenResponse;
import showroomz.swaggerDocs.AdminControllerDocs;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController implements AdminControllerDocs {

    private final AdminService adminService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody AdminSignUpRequest request) {
        adminService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "관리자 회원가입이 완료되었습니다."));
    }

    @Override
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(@RequestParam String email) {
        // 중복이면 true, 사용 가능하면 false 반환
        return ResponseEntity.ok(adminService.checkEmailDuplicate(email));
    }

    @Override
    @GetMapping("/check-market-name")
    public ResponseEntity<Boolean> checkMarketName(@RequestParam String marketName) {
        return ResponseEntity.ok(adminService.checkMarketNameDuplicate(marketName));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        TokenResponse tokenResponse = adminService.login(request);
        return ResponseEntity.ok(tokenResponse);
    }
}