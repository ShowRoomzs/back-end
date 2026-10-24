package showroomz.api.app.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.api.common.notification.service.NotificationQueryService;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/** 소비자 알림함(C11) — 정지 계정도 열람한다(C20 허용 범위). 탈퇴 계정만 막는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserNotificationService {

    private final UserRepository userRepository;
    private final NotificationQueryService queryService;

    public PageResponse<NotificationItem> list(Long userId, int page, int size) {
        requireActive(userId);
        return queryService.list(NotificationRecipientType.USER, userId, false, page, size);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        requireActive(userId);
        queryService.markRead(NotificationRecipientType.USER, userId, notificationId);
    }

    @Transactional
    public void markBadgeSeen(Long userId) {
        requireActive(userId).markNotificationBadgeSeen(LocalDateTime.now());
    }

    /** 홈 요약 종 배지(C1). */
    public long badgeCount(Users user) {
        return queryService.userBadgeCount(user.getId(), user.getNotificationBadgeSeenAt());
    }

    private Users requireActive(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }
        return user;
    }
}
