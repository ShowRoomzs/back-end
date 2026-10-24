package showroomz.api.admin.transaction.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어드민 주문 목록 · 소비자 상세 최근 주문의 반품 · 교환 요약 라벨(56 C2) — {@link AdminOrderQueryService#claimSummary}는 클레임 목록만 받는
 * 순수 함수라 상태 · 결과 조합을 직접 넣는다. 실제 반품 경로의 「반품 진행중」 · 「반품완료」는 {@code AdminOrderIntegrationTest} 「[56 C2] …」가 본다.
 */
@DisplayName("AdminOrderQueryService — 반품 · 교환 요약 라벨")
class AdminOrderClaimSummaryTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-24T10:00:00");

    @Test
    @DisplayName("[56 C2-05] 교환을 환불로 닫음 「환불완료」 · 반품 반려 「반품 반려」 · 교환 반려 「교환 반려」 · 교환 진행중 「교환 진행중」")
    void labels() {
        assertThat(label(claim(1L, ClaimType.EXCHANGE, ClaimStatus.COMPLETED, ClaimResult.REFUNDED))).isEqualTo("환불완료");
        assertThat(label(claim(2L, ClaimType.RETURN, ClaimStatus.COMPLETED, ClaimResult.REJECTED))).isEqualTo("반품 반려");
        assertThat(label(claim(3L, ClaimType.EXCHANGE, ClaimStatus.COMPLETED, ClaimResult.REJECTED))).isEqualTo("교환 반려");
        // 진행 중은 단계와 무관하게 유형 + 「진행중」 — 재발송 단계 · 반려 보류(이의 가능 · 종결 전)도 같다
        for (ClaimStatus open : List.of(ClaimStatus.REQUESTED, ClaimStatus.RECEIVED, ClaimStatus.RESHIP_READY,
                ClaimStatus.RESHIPPING, ClaimStatus.REJECT_HOLD)) {
            assertThat(label(claim(4L, ClaimType.EXCHANGE, open, null))).as(open.name()).isEqualTo("교환 진행중");
        }

        // 기존 라벨과 갈린다 — 같은 결과(REFUNDED)라도 반품은 「반품완료」 · 교환 종결은 「교환완료」
        assertThat(label(claim(5L, ClaimType.RETURN, ClaimStatus.COMPLETED, ClaimResult.REFUNDED))).isEqualTo("반품완료");
        assertThat(label(claim(6L, ClaimType.EXCHANGE, ClaimStatus.COMPLETED, ClaimResult.EXCHANGED))).isEqualTo("교환완료");
    }

    @Test
    @DisplayName("[56 C2-05] 요약 행 — 라벨과 함께 유형 · 단계 · 결과 · 접수번호가 그 클레임 그대로 · 결제 대기 · 요청 취소는 건수에서 빠진다")
    void summaryCarriesClaim() {
        OrderClaim refundedExchange = claim(11L, ClaimType.EXCHANGE, ClaimStatus.COMPLETED, ClaimResult.REFUNDED);
        OrderClaim older = claim(10L, ClaimType.RETURN, ClaimStatus.COMPLETED, ClaimResult.REJECTED);
        ReflectionTestUtils.setField(older, "requestedAt", NOW.minusDays(3));
        OrderClaim unpaid = claim(12L, ClaimType.EXCHANGE, ClaimStatus.PAYMENT_PENDING, null);
        ReflectionTestUtils.setField(unpaid, "requestedAt", NOW.plusHours(1));
        OrderClaim withdrawn = claim(13L, ClaimType.RETURN, ClaimStatus.COMPLETED, ClaimResult.CANCELLED);
        ReflectionTestUtils.setField(withdrawn, "requestedAt", NOW.plusHours(2));

        AdminOrderDto.ClaimSummary summary = AdminOrderQueryService.claimSummary(
                List.of(older, refundedExchange, unpaid, withdrawn));

        assertThat(summary.claimId()).isEqualTo(11L);
        assertThat(summary.claimNumber()).isEqualTo("CLM-11");
        assertThat(summary.type()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(summary.status()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(summary.result()).isEqualTo(ClaimResult.REFUNDED);
        assertThat(summary.label()).isEqualTo("환불완료");
        assertThat(summary.count()).isEqualTo(2);
    }

    private static String label(OrderClaim claim) {
        return AdminOrderQueryService.claimSummary(List.of(claim)).label();
    }

    private static OrderClaim claim(Long id, ClaimType type, ClaimStatus status, ClaimResult result) {
        OrderClaim claim = OrderClaim.builder().type(type).quantity(1).status(status).requestedAt(NOW).build();
        ReflectionTestUtils.setField(claim, "id", id);
        ReflectionTestUtils.setField(claim, "result", result);
        return claim;
    }
}
