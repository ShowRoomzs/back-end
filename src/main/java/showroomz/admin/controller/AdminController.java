package showroomz.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.admin.service.AdminService;
import showroomz.auth.DTO.AdminSignUpRequest;

import java.util.Map;

@Tag(name = "Admin", description = "관리자(판매자) API")
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "관리자(판매자) 회원가입", description = "계정, 판매자, 마켓 정보를 입력받아 관리자 계정을 생성합니다.")
    @PostMapping("/signup")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody AdminSignUpRequest request) {
        adminService.registerAdmin(request);
        return ResponseEntity.ok(Map.of("message", "관리자 회원가입이 완료되었습니다."));
    }
}