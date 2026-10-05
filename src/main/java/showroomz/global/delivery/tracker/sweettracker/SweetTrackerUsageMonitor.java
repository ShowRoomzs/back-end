package showroomz.global.delivery.tracker.sweettracker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerUsageResponse;

/**
 * 이용권 사용량 사전 경고(택배 추적 설계서 3-4) — 한도를 넘으면 조회가 전부 막혀 배송완료 자동 전환이 멈춘다.
 * 막힌 뒤에 아는 것은 감시 배치의 회차 중단 로그이고, 여기는 막히기 전에 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "true")
public class SweetTrackerUsageMonitor {

    /** 사용량 판정 — {@code LOW}만 error 로그(Sentry)다. */
    enum UsageLevel {
        OK,
        LOW,
        /** 조회 실패 · 해석 불가 — 경고하지 않고 다음 날 다시 본다. */
        UNKNOWN
    }

    private final SweetTrackerClient client;
    private final DeliveryTrackerProperties properties;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void check() {
        inspect();
    }

    UsageLevel inspect() {
        SweetTrackerUsageResponse usage;
        try {
            usage = client.usage();
        } catch (RuntimeException e) {
            log.warn("스마트택배 사용량 조회 실패 - cause: {}", e.toString());
            return UsageLevel.UNKNOWN;
        }
        if (usage == null || usage.isError() || usage.totalAmount() == null || usage.leftAmount() == null
                || usage.totalAmount() <= 0) {
            log.warn("스마트택배 사용량 응답을 해석하지 못했습니다 - code: {}, msg: {}",
                    usage == null ? null : usage.code(), usage == null ? null : usage.msg());
            return UsageLevel.UNKNOWN;
        }
        double leftRatio = (double) usage.leftAmount() / usage.totalAmount();
        if (leftRatio < properties.getUsageAlertRatio()) {
            log.error("스마트택배 이용권 잔여 사용량 부족 - left: {}, total: {}, 이용권 종료: {}",
                    usage.leftAmount(), usage.totalAmount(), usage.endDate());
            return UsageLevel.LOW;
        }
        log.info("스마트택배 이용권 사용량 - left: {}, total: {}", usage.leftAmount(), usage.totalAmount());
        return UsageLevel.OK;
    }
}
