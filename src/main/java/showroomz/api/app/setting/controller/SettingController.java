package showroomz.api.app.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.docs.SettingControllerDocs;
import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;
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
     * 알림 설정 조회 API
     */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationSettingResponse> getNotificationSettings() {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        NotificationSettingResponse response = settingService.getNotificationSettings(userPrincipal.getUsername());
        
        return ResponseEntity.ok(response);
    }

    @Override
    /**
     * 알림 설정 변경 API
     * (스위치 토글 시 호출)
     */
    @PatchMapping("/notifications")
    public ResponseEntity<Void> updateNotificationSettings(@RequestBody NotificationSettingRequest request) {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        settingService.updateNotificationSettings(userPrincipal.getUsername(), request);

        return ResponseEntity.noContent().build();
    }

    private UserPrincipal getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return (UserPrincipal) principal;
    }
}
