package showroomz.global.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import showroomz.api.app.claim.service.ClaimPaymentService;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 클레임 배치 3종의 스위치와 격리(보강 시나리오 TR-10 · TR-11). 통합 테스트 컨텍스트는 세 배치를 모두 꺼 두므로 켠 상태와
 * 설정 바인딩은 관련 빈만 올려 본다.
 */
@DisplayName("클레임 배치 — 스위치 · 격리 · 설정 바인딩")
class ClaimSchedulerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClaimInvoiceExpiryScheduler.class, ClaimPaymentReconcileScheduler.class,
                    ClaimTrackingScheduler.class)
            .withBean(OrderClaimService.class, () -> mock(OrderClaimService.class))
            .withBean(ClaimPaymentService.class, () -> mock(ClaimPaymentService.class))
            .withBean(DeliveryTrackerPort.class, () -> mock(DeliveryTrackerPort.class))
            .withBean(DeliveryTrackerProperties.class, DeliveryTrackerProperties::new);

    @Test
    @DisplayName("[TR-10] 설정이 없으면 자동 취소 · 결제 정리는 뜨고 클레임 추적은 뜨지 않는다(추적 연동 off)")
    void defaults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ClaimInvoiceExpiryScheduler.class);
            assertThat(context).hasSingleBean(ClaimPaymentReconcileScheduler.class);
            assertThat(context).doesNotHaveBean(ClaimTrackingScheduler.class);
        });
    }

    @Test
    @DisplayName("[TR-10] 스위치를 끄면 뜨지 않고, 추적 연동을 켜면 클레임 추적이 함께 뜬다")
    void switches() {
        runner.withPropertyValues("order.claim.invoice-expiry-scheduler-enabled=false",
                        "order.claim.payment-reconcile-scheduler-enabled=false", "delivery.tracker.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ClaimInvoiceExpiryScheduler.class);
                    assertThat(context).doesNotHaveBean(ClaimPaymentReconcileScheduler.class);
                    assertThat(context).hasSingleBean(ClaimTrackingScheduler.class);
                });
    }

    @Test
    @DisplayName("[TR-10] 자동 취소 배치 — 한 건이 터져도 나머지 요청은 닫는다")
    void expiryIsolatesFailures() {
        OrderClaimService claimService = mock(OrderClaimService.class);
        when(claimService.findCollectionIdsWithExpiredInvoice(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("잠금 실패")).when(claimService).expireInvoice(eq(2L), any());

        new ClaimInvoiceExpiryScheduler(claimService).tick();

        verify(claimService).expireInvoice(eq(1L), any());
        verify(claimService).expireInvoice(eq(3L), any());
    }

    @Test
    @DisplayName("[TR-10] 결제 정리 배치 — 정리가 터져도 예외가 스케줄러 밖으로 새지 않는다")
    void reconcileSwallowsFailure() {
        ClaimPaymentService paymentService = mock(ClaimPaymentService.class);
        doThrow(new IllegalStateException("PG 장애")).when(paymentService).reconcile(any());

        new ClaimPaymentReconcileScheduler(paymentService).tick();

        verify(paymentService).reconcile(any());
    }

    @Test
    @DisplayName("[TR-11] 도착 전 입고 확인 허용은 환경변수 ORDER_CLAIM_RECEIVE_BEFORE_ARRIVAL 로 끈다 — 없으면 켜져 있다")
    void receiveBeforeArrivalBinding() throws Exception {
        assertThat(bindClaim(Map.of()).isReceiveBeforeArrival()).isTrue();
        assertThat(bindClaim(Map.of("ORDER_CLAIM_RECEIVE_BEFORE_ARRIVAL", "false")).isReceiveBeforeArrival())
                .isFalse();
    }

    /** application.yml 을 그대로 읽고 환경변수를 얹어 order.* 를 바인딩한다. */
    private static OrderProperties.Claim bindClaim(Map<String, Object> env) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", env));
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return Binder.get(environment).bind("order", OrderProperties.class).get().getClaim();
    }
}
