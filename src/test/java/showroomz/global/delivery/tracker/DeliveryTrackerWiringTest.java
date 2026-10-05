package showroomz.global.delivery.tracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.sweettracker.SweetTrackerClient;
import showroomz.global.delivery.tracker.sweettracker.SweetTrackerDeliveryTracker;
import showroomz.global.delivery.tracker.sweettracker.SweetTrackerUsageMonitor;
import showroomz.global.scheduler.OrderDeliveryTrackingScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 연동 스위치 {@code delivery.tracker.enabled}의 배선(택배 추적 설계서 3-1 · 4절) — 포트 구현은 항상 하나이고,
 * 켜면 스마트택배 어댑터 · 감시 배치 · 사용량 감시가 함께 뜬다. 키 없이 켜면 기동이 실패한다.
 *
 * <p>전체 컨텍스트 대신 관련 빈만 올린다 — 통합 테스트 컨텍스트는 연동을 꺼 두므로 켠 상태는 여기서만 본다.
 */
@DisplayName("배송 추적 연동 스위치 — 빈 배선")
class DeliveryTrackerWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DeliveryTrackerProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, NoopDeliveryTracker.class, SweetTrackerClient.class,
                    SweetTrackerDeliveryTracker.class, SweetTrackerUsageMonitor.class,
                    OrderDeliveryTrackingScheduler.class)
            .withBean(OrderFulfillmentService.class, () -> mock(OrderFulfillmentService.class));

    @Test
    @DisplayName("설정이 없으면(기본) Noop 스텁 — 스마트택배 빈 · 감시 배치 · 사용량 감시가 뜨지 않는다")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(DeliveryTrackerPort.class).isInstanceOf(NoopDeliveryTracker.class);
            assertThat(context).doesNotHaveBean(SweetTrackerClient.class);
            assertThat(context).doesNotHaveBean(SweetTrackerDeliveryTracker.class);
            assertThat(context).doesNotHaveBean(SweetTrackerUsageMonitor.class);
            assertThat(context).doesNotHaveBean(OrderDeliveryTrackingScheduler.class);
        });
    }

    @Test
    @DisplayName("enabled=false 명시 — 키가 있어도 Noop 이다")
    void explicitlyDisabled() {
        runner.withPropertyValues("delivery.tracker.enabled=false", "delivery.tracker.api-key=some-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(DeliveryTrackerPort.class);
                    assertThat(context).getBean(DeliveryTrackerPort.class).isInstanceOf(NoopDeliveryTracker.class);
                    assertThat(context).doesNotHaveBean(OrderDeliveryTrackingScheduler.class);
                });
    }

    @Test
    @DisplayName("enabled=true + 키 — 포트는 스마트택배 어댑터 하나뿐 · 감시 배치 · 사용량 감시가 뜬다 · Noop 은 없다")
    void enabledWithKey() {
        runner.withPropertyValues("delivery.tracker.enabled=true", "delivery.tracker.api-key=some-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DeliveryTrackerPort.class);
                    assertThat(context).getBean(DeliveryTrackerPort.class)
                            .isInstanceOf(SweetTrackerDeliveryTracker.class);
                    assertThat(context).doesNotHaveBean(NoopDeliveryTracker.class);
                    assertThat(context).hasSingleBean(SweetTrackerUsageMonitor.class);
                    assertThat(context).hasSingleBean(OrderDeliveryTrackingScheduler.class);
                });
    }

    @Test
    @DisplayName("enabled=true 인데 키가 비면 기동 실패 — 조용히 스텁처럼 돌지 않는다 · 메시지가 설정 키를 가리킨다")
    void enabledWithoutKeyFailsStartup() {
        runner.withPropertyValues("delivery.tracker.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("delivery.tracker.api-key"));
        runner.withPropertyValues("delivery.tracker.enabled=true", "delivery.tracker.api-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("설정 기본값 — 02~06시 제외 2시간 cron · 페이지 300 · 간격 100ms · 24시간 · 7일 · 검증 끔 · 경고 20%")
    void propertyDefaults() {
        runner.run(context -> {
            DeliveryTrackerProperties properties = context.getBean(DeliveryTrackerProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getPollCron()).isEqualTo("0 0 0,6-22/2 * * *");
            assertThat(properties.getBatchSize()).isEqualTo(300);
            assertThat(properties.getCallGapMs()).isEqualTo(100L);
            assertThat(properties.getPickupAlertHours()).isEqualTo(24);
            assertThat(properties.getStallAlertDays()).isEqualTo(7);
            assertThat(properties.getApiUrl()).isEqualTo("https://info.sweettracker.co.kr");
            assertThat(properties.getApiKey()).isNull();
            assertThat(properties.isValidationEnabled()).isFalse();
            assertThat(properties.getUsageAlertRatio()).isEqualTo(0.2);
        });
    }

    @Test
    @DisplayName("설정 키(kebab-case)가 바인딩된다 — application.yml 의 이름 그대로")
    void propertyBinding() {
        runner.withPropertyValues(
                        "delivery.tracker.poll-cron=0 0 * * * *",
                        "delivery.tracker.batch-size=50",
                        "delivery.tracker.call-gap-ms=0",
                        "delivery.tracker.pickup-alert-hours=12",
                        "delivery.tracker.stall-alert-days=3",
                        "delivery.tracker.api-url=https://example.test",
                        "delivery.tracker.api-key=k",
                        "delivery.tracker.validation-enabled=true",
                        "delivery.tracker.usage-alert-ratio=0.35")
                .run(context -> {
                    DeliveryTrackerProperties properties = context.getBean(DeliveryTrackerProperties.class);
                    assertThat(properties.getPollCron()).isEqualTo("0 0 * * * *");
                    assertThat(properties.getBatchSize()).isEqualTo(50);
                    assertThat(properties.getCallGapMs()).isZero();
                    assertThat(properties.getPickupAlertHours()).isEqualTo(12);
                    assertThat(properties.getStallAlertDays()).isEqualTo(3);
                    assertThat(properties.getApiUrl()).isEqualTo("https://example.test");
                    assertThat(properties.getApiKey()).isEqualTo("k");
                    assertThat(properties.isValidationEnabled()).isTrue();
                    assertThat(properties.getUsageAlertRatio()).isEqualTo(0.35);
                });
    }
}
