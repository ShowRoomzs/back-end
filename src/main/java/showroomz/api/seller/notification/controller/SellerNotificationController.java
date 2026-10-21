package showroomz.api.seller.notification.controller;

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
import showroomz.api.seller.common.SellerMarketResolver;
import showroomz.api.seller.notification.docs.SellerNotificationControllerDocs;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/seller/notifications")
@RequiredArgsConstructor
public class SellerNotificationController implements SellerNotificationControllerDocs {

    private static final NotificationRecipientType SELLER = NotificationRecipientType.SELLER;

    private final NotificationQueryService queryService;
    private final SellerMarketResolver marketResolver;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<NotificationItem>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(queryService.list(SELLER, marketId(), unreadOnly, page, size));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<NotificationSummary> getSummary() {
        return ResponseEntity.ok(queryService.summary(SELLER, marketId()));
    }

    @Override
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
        queryService.markRead(SELLER, marketId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        queryService.markAllRead(SELLER, marketId());
        return ResponseEntity.noContent().build();
    }

    private Long marketId() {
        return marketResolver.currentMarket().getId();
    }
}
