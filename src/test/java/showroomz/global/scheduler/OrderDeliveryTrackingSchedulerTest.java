package showroomz.global.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 감시 배치의 흐름(택배 추적 설계서 4절) — 대상 수집 · 포트 호출 · 반영의 순서와 멈춤 조건만 본다. DB 없이 돈다.
 * 판정·전이는 {@code OrderFulfillmentTrackingIntegrationTest}, 업체 응답→전이는 {@code SweetTrackerTrackingIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("배송 추적 감시 배치 — 흐름")
class OrderDeliveryTrackingSchedulerTest {

    @Mock private OrderFulfillmentService fulfillmentService;
    @Mock private DeliveryTrackerPort tracker;

    private DeliveryTrackerProperties properties;
    private OrderDeliveryTrackingScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new DeliveryTrackerProperties();
        properties.setCallGapMs(0);
        scheduler = new OrderDeliveryTrackingScheduler(fulfillmentService, tracker, properties);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("id 커서로 페이지를 넘긴다 — 직전 페이지의 마지막 id 를 넘기고, 빈 페이지에서 끝난다")
    void pagesByIdCursor() {
        properties.setBatchSize(2);
        when(fulfillmentService.findTrackingTargets(0L, 2)).thenReturn(groups(10, 11));
        when(fulfillmentService.findTrackingTargets(11L, 2)).thenReturn(groups(15, 20));
        when(fulfillmentService.findTrackingTargets(20L, 2)).thenReturn(groups(21));
        when(fulfillmentService.findTrackingTargets(21L, 2)).thenReturn(List.of());
        when(tracker.track(any(), any())).thenReturn(Optional.empty());

        scheduler.tick();

        verify(tracker, times(5)).track(eq(DeliveryCarrier.CJ), any());
        verify(fulfillmentService).findTrackingTargets(21L, 2);
    }

    @Test
    @DisplayName("건마다 송장(택배사·번호)으로 조회하고, 결과를 설정값(24시간 · 7일)과 함께 반영한다 · 한 회차의 기준 시각은 하나")
    void appliesEachResultWithSettings() {
        properties.setPickupAlertHours(12);
        properties.setStallAlertDays(3);
        OrderDeliveryGroup first = group(1, DeliveryCarrier.HANJIN, "111");
        OrderDeliveryGroup second = group(2, DeliveryCarrier.LOTTE, "222");
        when(fulfillmentService.findTrackingTargets(0L, 300)).thenReturn(List.of(first, second));
        when(fulfillmentService.findTrackingTargets(2L, 300)).thenReturn(List.of());
        TrackSnapshot snapshot = new TrackSnapshot(LocalDateTime.now().minusHours(1), null, false, false);
        when(tracker.track(DeliveryCarrier.HANJIN, "111")).thenReturn(Optional.of(snapshot));
        when(tracker.track(DeliveryCarrier.LOTTE, "222")).thenReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now();
        scheduler.tick();

        ArgumentCaptor<LocalDateTime> nows = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fulfillmentService).applyTracking(eq(first), eq(Optional.of(snapshot)), nows.capture(), eq(12), eq(3));
        verify(fulfillmentService).applyTracking(eq(second), eq(Optional.empty()), nows.capture(), eq(12), eq(3));
        assertThat(nows.getAllValues()).hasSize(2).allMatch(now -> !now.isBefore(before))
                .containsOnly(nows.getAllValues().get(0));
    }

    @Test
    @DisplayName("한 건의 포트 예외·반영 예외는 그 건만 건너뛴다 — 나머지는 계속")
    void isolatesFailures() {
        OrderDeliveryGroup first = group(1, DeliveryCarrier.CJ, "111");
        OrderDeliveryGroup second = group(2, DeliveryCarrier.CJ, "222");
        OrderDeliveryGroup third = group(3, DeliveryCarrier.CJ, "333");
        when(fulfillmentService.findTrackingTargets(0L, 300)).thenReturn(List.of(first, second, third));
        when(fulfillmentService.findTrackingTargets(3L, 300)).thenReturn(List.of());
        when(tracker.track(DeliveryCarrier.CJ, "111")).thenThrow(new IllegalStateException("연동 장애"));
        when(tracker.track(DeliveryCarrier.CJ, "222")).thenReturn(Optional.empty());
        when(tracker.track(DeliveryCarrier.CJ, "333")).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("DB 장애")).when(fulfillmentService)
                .applyTracking(eq(second), any(), any(), anyInt(), anyInt());

        scheduler.tick();

        verify(fulfillmentService, never()).applyTracking(eq(first), any(), any(), anyInt(), anyInt());
        verify(fulfillmentService).applyTracking(eq(third), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("키 단위 차단이면 회차를 즉시 멈춘다 — 그 건은 반영하지 않고, 남은 건·다음 페이지를 부르지 않는다")
    void stopsOnBlocked() {
        properties.setBatchSize(2);
        OrderDeliveryGroup first = group(1, DeliveryCarrier.CJ, "111");
        OrderDeliveryGroup blocked = group(2, DeliveryCarrier.CJ, "222");
        when(fulfillmentService.findTrackingTargets(0L, 2)).thenReturn(List.of(first, blocked));
        when(tracker.track(DeliveryCarrier.CJ, "111")).thenReturn(Optional.empty());
        when(tracker.track(DeliveryCarrier.CJ, "222")).thenThrow(new DeliveryTrackerBlockedException("103"));

        scheduler.tick();

        verify(fulfillmentService).applyTracking(eq(first), any(), any(), anyInt(), anyInt());
        verify(fulfillmentService, never()).applyTracking(eq(blocked), any(), any(), anyInt(), anyInt());
        verify(fulfillmentService, never()).findTrackingTargets(eq(2L), anyInt());
    }

    @Test
    @DisplayName("건 사이 대기 중 인터럽트(종료)면 회차를 접는다 · 인터럽트 표시는 되살린다")
    void stopsOnInterrupt() {
        properties.setCallGapMs(60_000);
        OrderDeliveryGroup first = group(1, DeliveryCarrier.CJ, "111");
        OrderDeliveryGroup second = group(2, DeliveryCarrier.CJ, "222");
        when(fulfillmentService.findTrackingTargets(0L, 300)).thenReturn(List.of(first, second));
        when(tracker.track(DeliveryCarrier.CJ, "111")).thenAnswer(invocation -> {
            Thread.currentThread().interrupt(); // 종료 신호가 첫 건 처리 중에 온다
            return Optional.empty();
        });

        long started = System.nanoTime();
        scheduler.tick();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(System.nanoTime() - started).isLessThan(10_000_000_000L); // 60초를 기다리지 않는다
        verify(tracker, never()).track(DeliveryCarrier.CJ, "222");
        verify(fulfillmentService, never()).findTrackingTargets(eq(2L), anyInt());
    }

    @Test
    @DisplayName("건 사이 간격을 둔다 — 3건이면 간격 3번(마지막 건 뒤 포함)")
    void waitsBetweenCalls() {
        properties.setCallGapMs(30);
        when(fulfillmentService.findTrackingTargets(0L, 300)).thenReturn(groups(1, 2, 3));
        when(fulfillmentService.findTrackingTargets(3L, 300)).thenReturn(List.of());
        when(tracker.track(any(), any())).thenReturn(Optional.empty());

        long started = System.nanoTime();
        scheduler.tick();

        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(90);
    }

    @Test
    @DisplayName("기동은 cron(설정값) · KST — fixedDelay 가 아니다(재배포·회차 소요로 횟수가 밀리지 않게)")
    void scheduledByCronInKst() throws Exception {
        Scheduled scheduled = OrderDeliveryTrackingScheduler.class.getMethod("tick").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${delivery.tracker.poll-cron:0 0 0,6-22/2 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        assertThat(scheduled.fixedDelay()).isEqualTo(-1);
        assertThat(scheduled.fixedDelayString()).isEmpty();
    }

    @Test
    @DisplayName("기본 cron — KST 하루 10회(00 · 06~22 짝수 시) · 02~05시 없음 · 서버가 UTC 여도 KST 기준")
    void defaultCronFiresTenTimesInKst() {
        CronExpression cron = CronExpression.parse(properties.getPollCron());
        ZonedDateTime cursor = ZonedDateTime.of(LocalDate.of(2026, 10, 4).atTime(23, 59, 59), ZoneId.of("Asia/Seoul"));
        List<Integer> hours = new ArrayList<>();
        while (true) {
            cursor = cron.next(cursor);
            if (cursor == null || !cursor.toLocalDate().equals(LocalDate.of(2026, 10, 5))) {
                break;
            }
            hours.add(cursor.getHour());
        }

        assertThat(hours).containsExactly(0, 6, 8, 10, 12, 14, 16, 18, 20, 22);
        assertThat(hours).doesNotContain(2, 3, 4, 5);
    }

    // ------------------------------------------------------------------ 보조

    private static List<OrderDeliveryGroup> groups(long... ids) {
        return LongStream.of(ids).mapToObj(id -> group(id, DeliveryCarrier.CJ, "9000" + id)).toList();
    }

    private static OrderDeliveryGroup group(long id, DeliveryCarrier carrier, String trackingNumber) {
        OrderDeliveryGroup group = OrderDeliveryGroup.builder().productTotal(0).deliveryFee(0).build();
        ReflectionTestUtils.setField(group, "id", id);
        ReflectionTestUtils.setField(group, "carrier", carrier);
        ReflectionTestUtils.setField(group, "trackingNumber", trackingNumber);
        return group;
    }
}
