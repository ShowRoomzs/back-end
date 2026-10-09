package showroomz.api.seller.claim;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 받는 API 가 없는 도메인 진입점(보강 시나리오 6절 AD-01 ~ AD-07) — 어드민 컨트롤러가 붙기 전에 진입점 자체의 가드를 닫아
 * 둔다. 동시 호출(같은 회차 고지 · 가드 계산 뒤 고지)은 실 MySQL 몫이고 여기서는 순차 경로만 본다.
 */
@IntegrationTest
class ClaimAdminEntryIntegrationTest extends ClaimTestSupport {

    // ------------------------------------------------------------------ 재발송 직권 완료

    @Test
    @DisplayName("[AD-01] 교환 재발송 직권 완료 — 교환 완료 · 구매확정 카운트가 지금부터 다시 서고 이력의 주체는 운영자다")
    void adminCompletesExchangeReship() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = passed(exchangeClaim(group, creamVariant));
        registerReship(claimId, "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime now = LocalDateTime.now().withNano(0);

        claimService.completeReshipByAdmin(claimId, 7L, now);

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "EXCHANGED");
        assertThat(reload(group).getConfirmRestartAt()).isEqualTo(now);
        assertThat(jdbc.queryForMap("SELECT actor_type, actor_id FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'RESHIP_DELIVERED'", claimId))
                .containsEntry("actor_type", "ADMIN").containsEntry("actor_id", 7L);
        assertThat(fulfillmentService.confirmIfDue(group.getId(), now.plusDays(6))).isFalse();
    }

    @Test
    @DisplayName("[AD-02] 거절 반송 직권 완료는 거절 종결이고 타이머는 그대로 — 재발송 중이 아닌 건 · 두 번째 호출은 409")
    void adminCompletesRejectReship() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = rejectedClaim(returnClaim(group));
        claimService.markReshipFeePaid(chargeIdOf(claimId), "clm-test-1", consumer.getId(), LocalDateTime.now());
        Long ready = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));

        assertStateChanged(() -> claimService.completeReshipByAdmin(claimId, 1L, LocalDateTime.now()));
        registerReship(claimId, "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));

        claimService.completeReshipByAdmin(claimId, 1L, LocalDateTime.now());

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REJECTED");
        // 반려로 정지가 풀렸다 — 정지한 시간만큼만 기산점이 밀리고(1009 기획 수정본 4절) 반송 도착은 타이머를 새로 세우지 않는다.
        OrderDeliveryGroup timer = reload(group);
        assertThat(timer.getConfirmPausedAt()).isNull();
        assertThat(timer.getConfirmRestartAt()).isBetween(timer.getDeliveredAt(), timer.getDeliveredAt().plusMinutes(5));
        assertStateChanged(() -> claimService.completeReshipByAdmin(claimId, 1L, LocalDateTime.now()));
        assertStateChanged(() -> claimService.completeReshipByAdmin(ready, 1L, LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ 환불 집행

    @Test
    @DisplayName("[AD-03] PG 가 거절해 결제 밖에서 돌려주고 수동 기록 — 기록액이 예정액과 달라도 항목별 확정액의 합이 기록액이고, 같은 큐 행은 두 번 기록되지 않는다")
    void refundWithDifferentAmount() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        // PG 자동 환불이 거절되면 큐는 FAILED 로 남는다 — 운영자가 결제 밖에서 돌려준 뒤 수동으로 기록하는 경로다.
        fake.willFailCancel(group.getOrder().getPaidPaymentId(), FakePaymentGateway.Failure.REJECTED);
        RequestResult request = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group),
                newInvoice());
        request.claimIds().forEach(this::passQuietly);
        Long taskId = refundTaskIds(group).get(0);

        claimService.completeRefund(taskId, 40_000, 1L, LocalDateTime.now());

        List<Long> refunded = request.claimIds().stream().map(id -> ((Number) claimRow(id).get("refunded_amount"))
                .longValue()).toList();
        assertThat(refunded.stream().mapToLong(Long::longValue).sum()).isEqualTo(40_000);
        // 차감분은 앞 항목부터 뺀다 — 뒤 항목은 상품 금액 그대로다.
        assertThat(refunded.get(1)).isEqualTo(goodsAmount(request.claimIds().get(1)));
        assertThat(collectionRow(request.collectionId())).containsEntry("refund_amount", 40_000);
        assertStateChanged(() -> claimService.completeRefund(taskId, 40_000, 1L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("[AD-04] 일부 반려 요청의 환불 집행 — 통과 건만 닫히고 반려 건은 재발송 흐름 그대로다")
    void refundOnPartialRejection() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        RequestResult request = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group),
                newInvoice());
        Long approved = request.claimIds().get(0);
        Long rejected = request.claimIds().get(1);
        rejectedClaim(rejected);
        passed(approved);
        long expected = goodsAmount(approved) - DELIVERY_FEE;
        // 판정이 다 끝난 순간 PG 즉시 자동 환불 — 집행 단계가 없다(1009 기획 수정본 2절).
        assertThat(refundTasks(group)).singleElement().satisfies(task -> {
            assertThat(((Number) task.get("refund_amount")).longValue()).isEqualTo(expected);
            assertThat(task.get("status")).isEqualTo("DONE");
        });

        assertThat(claimRow(approved)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(claimStatus(rejected)).isEqualTo("RESHIP_READY");
        registerReship(rejected, "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));
    }

    // ------------------------------------------------------------------ 고지 · 폐기

    @Disabled("판정 대기 — 보강 시나리오 N5. 현행은 결제 기한 전에도 고지가 기록된다")
    @Test
    @DisplayName("[AD-05] 결제 기한 전에는 미결제 고지를 기록하지 않는다(권장안)")
    void noticeBeforeDueRejected() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));

        assertThatThrownBy(() -> claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L,
                LocalDateTime.now())).isInstanceOf(BusinessException.class);
        assertThat(claimRow(claimId)).containsEntry("notice_count", 0);
    }

    @Test
    @DisplayName("[AD-06] 한 박스의 거절 보류 2건 중 1건만 폐기하면 청구는 남고, 마지막 건까지 폐기하면 소멸한다")
    void disposeOneOfTwo() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        RequestResult request = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group),
                newInvoice());
        request.claimIds().forEach(this::rejectQuietly);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (Long claimId : request.claimIds()) {
            claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(5));
            claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(4));
        }

        claimService.disposeAfterStorage(request.claimIds().get(0), 1L, now);
        assertThat(chargeStatusOf(request.claimIds().get(0))).isEqualTo("PENDING");
        assertThat(claimStatus(request.claimIds().get(1))).isEqualTo("REJECT_HOLD");

        claimService.disposeAfterStorage(request.claimIds().get(1), 1L, now);
        assertThat(chargeStatusOf(request.claimIds().get(1))).isEqualTo("VOID");
    }

    @Test
    @DisplayName("[AD-07] 보관 기한이 지났어도 고지가 한 번 더 기록되면 기한이 밀려 폐기할 수 없다")
    void extraNoticeExtendsStorage() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(5));
        claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(4));
        claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusDays(1));

        assertThatThrownBy(() -> claimService.disposeAfterStorage(claimId, 1L, now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_STORAGE_NOT_EXPIRED));
        assertThat(claimRow(claimId)).containsEntry("status", "REJECT_HOLD").containsEntry("notice_count", 3);
    }

    // ------------------------------------------------------------------ 보조

    private void passQuietly(Long claimId) {
        try {
            passed(claimId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void rejectQuietly(Long claimId) {
        try {
            rejectedClaim(claimId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long goodsAmount(Long claimId) {
        return jdbc.queryForObject("SELECT p.price * c.quantity FROM order_claim c JOIN order_product p "
                + "ON p.order_product_id = c.order_product_id WHERE c.claim_id = ?", Long.class, claimId);
    }

    private static void assertStateChanged(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_STATE_CHANGED));
    }
}
