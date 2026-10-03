package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.NoopDeliveryTracker;
import showroomz.global.scheduler.OrderDeliveryTrackingScheduler;
import showroomz.global.scheduler.PurchaseConfirmScheduler;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배치 진입점 자체(보강 시나리오 TR-01 · TR-02 · TR-05) — 다른 테스트는 판정 로직({@code applyTracking} · {@code confirmPurchase})을
 * 직접 부르므로 「대상 수집 → 포트 호출 → 건별 반영 → 한 건 실패 격리」 경로는 여기서만 돈다.
 *
 * <p>통합 테스트 컨텍스트는 두 스케줄러 빈을 띄우지 않는다(추적 연동 off · 구매확정 배치 off). 새 컨텍스트를 만들지 않으려고
 * 실제 서비스 빈과 테스트용 포트·설정으로 스케줄러를 직접 조립해 {@code tick()}을 부른다.
 */
@IntegrationTest
class OrderFulfillmentSchedulerIntegrationTest extends SellerOrderTestSupport {

    @Autowired private ApplicationContext applicationContext;

    @Test
    @DisplayName("[TR-01] 추적 배치 — id 순으로 포트를 부르고 건별 반영 · 한 건이 터져도 나머지는 배송완료 · 터진 건은 배송중 그대로")
    void trackingTickIsolatesFailures() throws Exception {
        OrderDeliveryGroup first = shippingGroup("730010002000");
        OrderDeliveryGroup broken = shippingGroup("730010002001");
        OrderDeliveryGroup third = shippingGroup("730010002002");
        LocalDateTime deliveredAt = LocalDateTime.now().withNano(0).minusHours(1);
        StubTracker tracker = new StubTracker(trackingNumber -> {
            if (trackingNumber.equals("730010002001")) {
                throw new IllegalStateException("연동 업체 장애");
            }
            return Optional.of(new DeliveryTrackerPort.TrackSnapshot(deliveredAt, deliveredAt, false, false));
        });

        trackingScheduler(tracker, 300).tick();

        assertThat(tracker.calls).containsExactly("730010002000", "730010002001", "730010002002");
        assertThat(reload(first).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(reload(third).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(reload(broken).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
        assertThat(historyCount(broken, FulfillmentEventType.DELIVERED)).isZero();
    }

    @Test
    @DisplayName("[TR-01] 추적 배치 — 회차당 상한(batchSize)까지만 폴링한다 · 송장 없는 건·배송중 아닌 건은 대상이 아니다")
    void trackingTickRespectsBatchSize() throws Exception {
        preparingGroup();
        shippingGroup("730010002003");
        shippingGroup("730010002004");
        shippingGroup("730010002005");
        StubTracker tracker = new StubTracker(trackingNumber -> Optional.empty());

        trackingScheduler(tracker, 2).tick();

        assertThat(tracker.calls).containsExactly("730010002003", "730010002004");
    }

    @Test
    @DisplayName("[TR-02] 구매확정 배치 — 배송완료 + 설정 일수가 지난 건만 확정 · 설정을 3일로 바꾸면 그 기준으로 따라간다")
    void purchaseConfirmTickFollowsProperty() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        OrderDeliveryGroup eightDays = delivered(shippingGroup("740010002000"), now.minusDays(8));
        OrderDeliveryGroup nineDays = delivered(shippingGroup("740010002001"), now.minusDays(9));
        OrderDeliveryGroup fourDays = delivered(shippingGroup("740010002002"), now.minusDays(4));

        new PurchaseConfirmScheduler(fulfillmentService, new OrderProperties()).tick();

        assertThat(reload(eightDays).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        assertThat(reload(nineDays).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        assertThat(reload(fourDays).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);

        OrderProperties threeDays = new OrderProperties();
        threeDays.setPurchaseConfirmDays(3);
        new PurchaseConfirmScheduler(fulfillmentService, threeDays).tick();

        assertThat(reload(fourDays).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        assertThat(historyCount(eightDays, FulfillmentEventType.PURCHASE_CONFIRMED)).isEqualTo(1);
    }

    @Test
    @DisplayName("[TR-05] 연동 off(기본) — 추적 배치 빈이 뜨지 않고 포트는 Noop 스텁이다(판정 불가 · 조회 없음)")
    void trackerDisabledByDefault() {
        assertThat(applicationContext.getBeanProvider(OrderDeliveryTrackingScheduler.class).getIfAvailable()).isNull();
        DeliveryTrackerPort port = applicationContext.getBean(DeliveryTrackerPort.class);
        assertThat(port).isInstanceOf(NoopDeliveryTracker.class);
        assertThat(port.validateInvoice(DeliveryCarrier.CJ, "123412341234"))
                .isEqualTo(DeliveryTrackerPort.ValidationResult.UNAVAILABLE);
        assertThat(port.track(DeliveryCarrier.CJ, "123412341234")).isEmpty();
    }

    // ------------------------------------------------------------------ 보조

    private OrderDeliveryTrackingScheduler trackingScheduler(DeliveryTrackerPort tracker, int batchSize) {
        DeliveryTrackerProperties properties = new DeliveryTrackerProperties();
        properties.setBatchSize(batchSize);
        return new OrderDeliveryTrackingScheduler(fulfillmentService, tracker, properties);
    }

    /** 송장번호별 응답을 정하는 포트 — 호출 순서를 기록한다. */
    private static final class StubTracker implements DeliveryTrackerPort {

        private final Function<String, Optional<TrackSnapshot>> answer;
        private final List<String> calls = new ArrayList<>();

        private StubTracker(Function<String, Optional<TrackSnapshot>> answer) {
            this.answer = answer;
        }

        @Override
        public ValidationResult validateInvoice(DeliveryCarrier carrier, String trackingNumber) {
            return ValidationResult.UNAVAILABLE;
        }

        @Override
        public Optional<TrackSnapshot> track(DeliveryCarrier carrier, String trackingNumber) {
            calls.add(trackingNumber);
            return answer.apply(trackingNumber);
        }
    }
}
