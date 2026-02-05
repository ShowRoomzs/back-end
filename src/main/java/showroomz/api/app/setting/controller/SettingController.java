package showroomz.api.app.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

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
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        // 2. 서비스 호출
        NotificationSettingResponse response = settingService.getNotificationSettings(springUser.getUsername());
        
        return ResponseEntity.ok(response);
    }

    @Override
    /**
     * 알림 설정 변경 API
     * (스위치 토글 시 호출)
     */
    @PatchMapping("/notifications")
    public ResponseEntity<Void> updateNotificationSettings(@RequestBody NotificationSettingRequest request) {
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        // 2. 서비스 호출
        settingService.updateNotificationSettings(springUser.getUsername(), request);

        return ResponseEntity.noContent().build();
    }
}
