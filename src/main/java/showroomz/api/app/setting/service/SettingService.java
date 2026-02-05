package showroomz.api.app.setting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final UserRepository userRepository;

    /**
     * 알림 설정 조회
     */
    @Transactional(readOnly = true)
    public NotificationSettingResponse getNotificationSettings(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // notificationSetting이 null인 경우 기본값으로 초기화
        if (user.getNotificationSetting() == null) {
            user.setNotificationSetting(new showroomz.domain.member.user.vo.NotificationSetting());
        }

        return new NotificationSettingResponse(
                user.getNotificationSetting().isSmsAgree(),
                user.getNotificationSetting().isNightPushAgree(),
                user.getNotificationSetting().isShowroomPushAgree(),
                user.getNotificationSetting().isMarketPushAgree()
        );
    }

    /**
     * 알림 설정 변경
     */
    @Transactional
    public void updateNotificationSettings(String username, NotificationSettingRequest request) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // notificationSetting이 null인 경우 기본값으로 초기화
        if (user.getNotificationSetting() == null) {
            user.setNotificationSetting(new showroomz.domain.member.user.vo.NotificationSetting());
        }

        // 엔티티의 업데이트 메서드 호출
        user.updateNotificationSettings(
                request.getSmsAgree(),
                request.getNightPushAgree(),
                request.getShowroomPushAgree(),
                request.getMarketPushAgree()
        );
        
        // 수정 시간 업데이트
        user.setModifiedAt(LocalDateTime.now());
    }
}
