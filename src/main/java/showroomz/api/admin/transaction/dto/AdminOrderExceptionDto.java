package showroomz.api.admin.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.dto.PageResponse;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 어드민 예외 관리(06d · 40 설계서) — 새 데이터 없이 주문 · 클레임에서 조건에 맞는 것을 모아 보여 주는 뷰. 06b · 06c DTO 와 한 파일에
 * 있던 것을 행 필드가 늘며 떼어 냈다(40 설계서 0-10). 탭 · 유형 enum 이름은 FE 호환을 위해 그대로다.
 */
public final class AdminOrderExceptionDto {

    private AdminOrderExceptionDto() {
    }

    public enum ExceptionTab {
        /** 처리 지연 — 당사자가 기한을 넘긴 건. 운영자가 개입할 수 있는 유일한 탭(기본 진입). */
        DELAY,
        /** 배송 예외 — 택배 · 추적 이상. 플랫폼 미개입(처리 주체 열이 CS 답변 문장). */
        DELIVERY
    }

    public enum ExceptionKind {
        SHIP_OVERDUE("발송 기한 경과", ExceptionTab.DELAY),
        INSPECT_OVERDUE("검수 지연", ExceptionTab.DELAY),
        RESHIP_DELAYED("재발송 지연", ExceptionTab.DELAY),
        PICKUP_UNCONFIRMED("집화 확인 필요", ExceptionTab.DELIVERY),
        TRACKING_STALLED("추적 정지", ExceptionTab.DELIVERY),
        RETURNING("반송 중", ExceptionTab.DELIVERY),
        COLLECTION_UNSCANNED("회수 송장 미조회", ExceptionTab.DELIVERY);

        private final String label;
        private final ExceptionTab tab;

        ExceptionKind(String label, ExceptionTab tab) {
            this.label = label;
            this.tab = tab;
        }

        public String getLabel() {
            return label;
        }

        public ExceptionTab tab() {
            return tab;
        }
    }

    public enum LinkType {
        /** 06a 주문 상세. */
        ORDER,
        /** 06b 클레임 상세. */
        CLAIM
    }

    @Schema(name = "AdminOrderExceptionLink", description = "[열기]의 목적지 — 서버가 정한다(FE 는 kind 로 고르지 않는다)")
    public record Link(LinkType type, Long orderId, Long deliveryGroupId, @Schema(nullable = true) Long claimId) {
    }

    @Schema(name = "AdminOrderExceptionInvoice", description = "송장 열 — 주문 송장(집화 · 추적 정지 · 반송) 또는 회수 송장. 가리지 않는다")
    public record Invoice(@Schema(nullable = true) DeliveryCarrier carrier, @Schema(nullable = true) String carrierLabel,
                          @Schema(nullable = true) String trackingNumber) {
    }

    @Schema(name = "AdminOrderExceptionItem", description = "처리 지연 행은 기한 · 알림 · 다음 단계가, 배송 예외 행은 송장 · 기준 · 처리 주체가 찬다")
    public record ExceptionItem(
            ExceptionTab tab,
            ExceptionKind kind,
            @Schema(example = "발송 기한 경과") String kindLabel,
            @Schema(description = "대상 열 — 주문번호 또는 접수번호(CLM-). 회수 송장 미조회는 주문번호", example = "CLM-3015")
            String targetNumber,
            @Schema(nullable = true) String subOrderNumber,
            Long orderId,
            Long deliveryGroupId,
            @Schema(nullable = true) Long claimId,
            Long marketId,
            @Schema(example = "데일리랩") String brandName,
            @Schema(description = "기한 — 처리 지연만", nullable = true) LocalDateTime dueAt,
            @Schema(description = "기한 보조 문장", example = "공구 마감 + 3영업일(주문 시점 값)", nullable = true) String dueBasisLabel,
            @Schema(description = "기준 시각 — 정렬 기준(처리 지연은 기한과 같다)", nullable = true) LocalDateTime basisAt,
            @Schema(description = "기준 열 문장 — 배송 예외만", example = "집화 후 7일", nullable = true) String basisLabel,
            @Schema(description = "기준 시각부터 경과 시간", example = "60") long elapsedHours,
            @Schema(description = "기한 다음 날부터 오늘까지 영업일 — 처리 지연만", nullable = true) Integer elapsedBusinessDays,
            @Schema(description = "경과 열 문장", example = "3영업일") String elapsedLabel,
            @Schema(description = "기한 · 기준을 넘겼는가 — 탭 정의상 전부 참(색은 FE)") boolean overdue,
            @Schema(description = "시스템 자동 알림 횟수 — 재발송 지연 · 배송 예외는 null(「—」)", nullable = true) Integer noticeCount,
            @Schema(nullable = true) LocalDateTime lastNoticeAt,
            @Schema(description = "다음 자동 알림 예정 — 대행 조건에 닿았거나 알림이 없는 유형이면 null", nullable = true)
            LocalDateTime nextNoticeAt,
            @Schema(description = "대행 가능 조건 충족(알림 N회 무응답) — 상태가 아니라 조건이다") boolean actOnBehalfAvailable,
            @Schema(description = "다음 단계 열 — 처리 지연만", example = "자동 알림 대기 · 2회차 09.18", nullable = true) String nextStepLabel,
            @Schema(description = "다음 단계 보조", example = "송장 대행 · 직권 취소 — 주문 상세", nullable = true) String nextStepNote,
            @Schema(description = "송장 — 배송 예외만", nullable = true) Invoice invoice,
            @Schema(description = "처리 주체 — 배송 예외만 · CS 답변 문장", example = "소비자·브랜드가 택배사 조회", nullable = true)
            String handlerLabel,
            Link link
    ) {
    }

    @Schema(name = "AdminOrderExceptionPage")
    public record ExceptionPage(
            @Schema(description = "서버 기준 시각 — 경과 · 다음 회차 계산의 기준") LocalDateTime asOf,
            PageResponse<ExceptionItem> page
    ) {
    }

    @Schema(name = "AdminOrderExceptionSummary")
    public record ExceptionSummary(
            LocalDateTime asOf,
            @Schema(description = "탭 숫자 — 필터 · 검색과 무관한 전체. DELAY 가 06a 툴바 「처리 지연 N건」", example = "{\"DELAY\": 3, \"DELIVERY\": 4}")
            Map<String, Long> tabCounts,
            @Schema(description = "유형 셀렉트 건수 — 7종 전부(0건 포함)") Map<String, Long> kindCounts,
            @Schema(description = "툴바 「대행 가능 N건」 — 처리 지연 중 대행 조건 충족", example = "1") long actOnBehalfCount,
            @Schema(description = "사이드바 배지", example = "7") long badge,
            @Schema(description = "배지 범위 — ALL(처리 지연 + 배송 예외) · DELAY(처리 지연만)", example = "ALL") String badgeScope
    ) {
    }
}
