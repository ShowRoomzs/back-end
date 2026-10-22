package showroomz.api.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.service.NotificationDeadlineNoticeService;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구매확정 D-1(1017 설계서 1-3 C11 · 2-4) — 자동 구매확정 예정일이 내일인 배송완료 하위주문에만 · 같은 날 두 번 돌아도 한 건 ·
 * 반품 · 교환으로 확정이 멈춘 건은 빠지고 · 재개 시각이 있으면 그 시각 기준. 광고성이 아니라 토글과 무관하다.
 */
@DisplayName("1017 구매확정 D-1 알림")
class NotificationDeadlineIntegrationTest extends SettlementTestSupport {

    @Autowired private NotificationDeadlineNoticeService deadlineNoticeService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrderProperties orderProperties;

    @Test
    @DisplayName("CF-01 예정일이 내일인 건만 · 두 번 돌아도 한 건 · 주문 상세로 이동")
    void onlyTomorrowAndOnce() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        int days = orderProperties.getPurchaseConfirmDays();
        OrderDeliveryGroup dueTomorrow = deliveredFrom(paidGroup(), tomorrow.minusDays(days).atTime(12, 0));
        deliveredFrom(paidGroup(), tomorrow.minusDays(days - 1).atTime(12, 0)); // 모레 확정

        assertThat(deadlineNoticeService.noticePurchaseConfirm(now)).isEqualTo(1);
        deadlineNoticeService.noticePurchaseConfirm(now);

        List<Notification> rows = rows();
        assertThat(rows).singleElement().satisfies(n -> {
            assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.ORDER_GROUP);
            assertThat(n.getTargetId()).isEqualTo(dueTomorrow.getId());
            assertThat(n.isAdvertising()).isFalse();
        });
    }

    @Test
    @DisplayName("CF-02 확정이 멈춘 건(반품 · 교환 진행 중)은 빠지고 · 재개 시각이 있으면 그 시각으로 예정일을 센다")
    void pausedExcludedAndRestartCounts() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        int days = orderProperties.getPurchaseConfirmDays();
        OrderDeliveryGroup paused = deliveredFrom(paidGroup(), tomorrow.minusDays(days).atTime(12, 0));
        jdbc.update("UPDATE order_delivery_group SET confirm_paused_at = ? WHERE delivery_group_id = ?", now.minusDays(1),
                paused.getId());
        OrderDeliveryGroup restarted = deliveredFrom(paidGroup(), now.minusDays(30));
        jdbc.update("UPDATE order_delivery_group SET confirm_restart_at = ? WHERE delivery_group_id = ?",
                tomorrow.minusDays(days).atTime(9, 0), restarted.getId());

        deadlineNoticeService.noticePurchaseConfirm(now);

        assertThat(rows()).extracting(Notification::getTargetId).containsExactly(restarted.getId());
    }

    private List<Notification> rows() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == NotificationRecipientType.USER
                        && n.getType() == NotificationType.ORDER_CONFIRM_D1)
                .toList();
    }
}
