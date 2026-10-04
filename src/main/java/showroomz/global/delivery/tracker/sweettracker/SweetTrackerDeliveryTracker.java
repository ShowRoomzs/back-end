package showroomz.global.delivery.tracker.sweettracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerTrackingResponse;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerTrackingResponse.Detail;
import showroomz.global.utils.KstDates;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 스마트택배 조회 API 어댑터(택배 추적 설계서 3절) — 폴링 전용이다. 웹훅·추적 API(연 계약)를 쓰지 않는다.
 *
 * <p><b>반송은 감지하지 않는다</b> — 이 API 의 진행 단계(0~6)에 반송이 없다. {@code returnDetected}·{@code returnCompleted}는
 * 항상 false 이고, 반송 송장은 「추적 정지」 배지나 운영자 수동 처리로 받는다(3-5).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "true")
public class SweetTrackerDeliveryTracker implements DeliveryTrackerPort {

    private static final String ERROR_KEY_NOT_FOUND = "101";
    private static final String ERROR_KEY_EXPIRED = "102";
    private static final String ERROR_KEY_USAGE_EXCEEDED = "103";
    private static final String ERROR_INVALID_INVOICE = "104";
    private static final int LEVEL_DELIVERED = 6;

    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    /** 이 값 이상이면 밀리초로 본다 — 초 단위로는 서기 5138년이다. */
    private static final long EPOCH_MILLIS_FLOOR = 100_000_000_000L;

    private final SweetTrackerClient client;
    private final DeliveryTrackerProperties properties;

    public SweetTrackerDeliveryTracker(SweetTrackerClient client, DeliveryTrackerProperties properties) {
        this.client = client;
        this.properties = properties;
        List<String> unmapped = Arrays.stream(DeliveryCarrier.values())
                .filter(carrier -> carrier.getTrackerCode() == null)
                .map(DeliveryCarrier::getLabel)
                .toList();
        if (!unmapped.isEmpty()) {
            log.warn("택배사 코드가 없어 추적되지 않는 택배사: {}", unmapped);
        }
    }

    @Override
    public ValidationResult validateInvoice(DeliveryCarrier carrier, String trackingNumber) {
        if (!properties.isValidationEnabled() || carrier.getTrackerCode() == null) {
            return ValidationResult.UNAVAILABLE;
        }
        Optional<SweetTrackerTrackingResponse> response = call(carrier, trackingNumber);
        if (response.isEmpty()) {
            return ValidationResult.UNAVAILABLE;
        }
        if (!response.get().isError()) {
            return ValidationResult.VALID;
        }
        return ERROR_INVALID_INVOICE.equals(response.get().code())
                ? ValidationResult.INVALID
                : ValidationResult.UNAVAILABLE;
    }

    @Override
    public Optional<TrackSnapshot> track(DeliveryCarrier carrier, String trackingNumber) {
        if (carrier.getTrackerCode() == null) {
            return Optional.empty();
        }
        Optional<SweetTrackerTrackingResponse> called = call(carrier, trackingNumber);
        if (called.isEmpty()) {
            return Optional.empty();
        }
        SweetTrackerTrackingResponse response = called.get();
        if (response.isError()) {
            return onTrackError(response);
        }
        LocalDateTime lastEventAt = lastEventAt(response);
        boolean delivered = Boolean.TRUE.equals(response.complete())
                || (response.level() != null && response.level() == LEVEL_DELIVERED);
        return Optional.of(new TrackSnapshot(lastEventAt, delivered ? lastEventAt : null, false, false));
    }

    private Optional<TrackSnapshot> onTrackError(SweetTrackerTrackingResponse response) {
        String code = response.code();
        if (ERROR_INVALID_INVOICE.equals(code)) {
            // 집화 전 송장이 이 코드로 올 수 있다 — 「이벤트 없음」으로 돌려 24시간 뒤 집화 확인 필요로 이어지게 한다.
            return Optional.of(new TrackSnapshot(null, null, false, false));
        }
        if (ERROR_KEY_USAGE_EXCEEDED.equals(code) || ERROR_KEY_NOT_FOUND.equals(code)
                || ERROR_KEY_EXPIRED.equals(code)) {
            throw new DeliveryTrackerBlockedException("스마트택배 조회 차단 - code: " + code + ", msg: " + response.msg());
        }
        // 105(같은 송장 일 한도) · 106(조회 에러) — 판정하지 않고 다음 회차에 맡긴다.
        log.warn("스마트택배 조회 에러 - code: {}, msg: {}", code, response.msg());
        return Optional.empty();
    }

    /** 통신 오류·해석 불가 응답은 empty — 키는 폼 본문으로만 나가므로 예외 메시지에 섞이지 않는다. */
    private Optional<SweetTrackerTrackingResponse> call(DeliveryCarrier carrier, String trackingNumber) {
        try {
            return Optional.ofNullable(client.trackingInfo(carrier.getTrackerCode(), trackingNumber));
        } catch (RuntimeException e) {
            log.warn("스마트택배 조회 실패 - carrier: {}, cause: {}", carrier, e.toString());
            return Optional.empty();
        }
    }

    /** 이력 중 가장 늦은 시각 — 이력 순서를 믿지 않는다. 스캔 정보가 없으면(level 0) null. */
    private LocalDateTime lastEventAt(SweetTrackerTrackingResponse response) {
        Stream<Detail> details = response.trackingDetails() == null ? Stream.empty() : response.trackingDetails().stream();
        return Stream.concat(details, Stream.ofNullable(response.lastDetail()))
                .map(SweetTrackerDeliveryTracker::eventTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /** 업체 시각은 KST 다 — 다른 시각 컬럼과 같이 서버 시간대의 LocalDateTime 으로 맞춘다. */
    private static LocalDateTime eventTime(Detail detail) {
        if (detail.timeString() != null) {
            String text = detail.timeString().trim();
            for (DateTimeFormatter format : TIME_FORMATS) {
                try {
                    return LocalDateTime.parse(text, format).atZone(KstDates.KST)
                            .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    // 다음 형식으로
                }
            }
        }
        if (detail.time() == null || detail.time() <= 0) {
            return null;
        }
        Instant instant = detail.time() >= EPOCH_MILLIS_FLOOR
                ? Instant.ofEpochMilli(detail.time())
                : Instant.ofEpochSecond(detail.time());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
