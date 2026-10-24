package showroomz.api.app.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.notification.docs.UserNotificationControllerDocs;
import showroomz.api.app.notification.service.UserNotificationService;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.api.creator.common.CreatorResolver;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/user/notifications")
@RequiredArgsConstructor
public class UserNotificationController implements UserNotificationControllerDocs {

    private final UserNotificationService notificationService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<NotificationItem>> getNotifications(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.list(CreatorResolver.currentUserId(), page, size));
    }

    @Override
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(CreatorResolver.currentUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/badge/seen")
    public ResponseEntity<Void> markBadgeSeen() {
        notificationService.markBadgeSeen(CreatorResolver.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
