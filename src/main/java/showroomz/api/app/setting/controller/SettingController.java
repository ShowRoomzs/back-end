package showroomz.api.app.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.setting.DTO.AccountInfoResponse;
import showroomz.api.app.setting.DTO.IdentityReverifyRequest;
import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;
import showroomz.api.app.setting.docs.SettingControllerDocs;
import showroomz.api.app.setting.service.SettingService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user/settings")
@RequiredArgsConstructor
public class SettingController implements SettingControllerDocs {

    private final SettingService settingService;

    @Override
    /**
     * C15 알림 설정 조회 API
     */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationSettingResponse> getNotificationSettings() {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        NotificationSettingResponse response = settingService.getNotificationSettings(userPrincipal.getUsername());

        return ResponseEntity.ok(response);
    }

    @Override
    /**
     * C15 알림 설정 변경 API
     * (토글 시 호출)
     */
    @PatchMapping("/notifications")
    public ResponseEntity<Void> updateNotificationSettings(@RequestBody NotificationSettingRequest request) {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        settingService.updateNotificationSettings(userPrincipal.getUsername(), request);

        return ResponseEntity.noContent().build();
    }

    @Override
    /**
     * C15-2 회원정보 조회 API (조회 전용 · 마스킹)
     */
    @GetMapping("/account")
    public ResponseEntity<AccountInfoResponse> getAccountInfo() {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        AccountInfoResponse response = settingService.getAccountInfo(userPrincipal.getUsername());

        return ResponseEntity.ok(response);
    }

    @Override
    /**
     * C15-2 회원정보 변경 API — PASS 재인증으로 이름·생년월일·성별·휴대폰번호 갱신
     */
    @PostMapping("/account/verifications")
    public ResponseEntity<AccountInfoResponse> reverifyIdentity(@RequestBody IdentityReverifyRequest request) {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        AccountInfoResponse response = settingService.reverifyIdentity(userPrincipal.getUsername(), request);

        return ResponseEntity.ok(response);
    }

    private UserPrincipal getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return (UserPrincipal) principal;
    }
}
