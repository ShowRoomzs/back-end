package showroomz.api.creator.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.api.common.notification.dto.NotificationSummary;
import showroomz.api.common.notification.service.NotificationQueryService;
import showroomz.api.creator.common.CreatorResolver;
import showroomz.api.creator.notification.docs.CreatorNotificationControllerDocs;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/creator/notifications")
@RequiredArgsConstructor
public class CreatorNotificationController implements CreatorNotificationControllerDocs {

    private static final NotificationRecipientType CREATOR = NotificationRecipientType.CREATOR;

    private final NotificationQueryService queryService;
    private final CreatorResolver creatorResolver;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<NotificationItem>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(queryService.list(CREATOR, creatorId(), unreadOnly, page, size));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<NotificationSummary> getSummary() {
        return ResponseEntity.ok(queryService.summary(CREATOR, creatorId()));
    }

    @Override
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
        queryService.markRead(CREATOR, creatorId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        queryService.markAllRead(CREATOR, creatorId());
        return ResponseEntity.noContent().build();
    }

    private Long creatorId() {
        return creatorResolver.currentCreator().getId();
    }
}
