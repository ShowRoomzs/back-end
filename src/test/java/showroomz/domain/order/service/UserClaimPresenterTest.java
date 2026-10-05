package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.service.UserClaimPresenter.TodoType;
import showroomz.domain.order.service.UserClaimPresenter.View;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.UserClaimPhase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 소비자 표시 단계 유도(앱 클레임 설계서 4-2 표 전 행) — DB 없이 돈다. */
@DisplayName("소비자 클레임 표시 단계")
class UserClaimPresenterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDateTime NOW = TODAY.atTime(10, 0);

    @Test
    @DisplayName("회수 대기 — 로즈 · 할 일 「회수 송장 등록 필요 · MM.dd까지」. 교환이면 라벨만 바뀐다")
    void requested() {
        View view = present(claim(ClaimType.RETURN, ClaimStatus.REQUESTED), null);

        assertThat(view.phase()).isEqualTo(UserClaimPhase.REQUESTED);
        assertThat(view.label()).isEqualTo("반품 요청");
        assertThat(view.sub()).isEqualTo("회수 송장 입력 필요");
        assertThat(view.actionRequired()).isTrue();
        assertThat(view.listSub()).isEqualTo("진행 중 · 요청");
        assertThat(view.todo().type()).isEqualTo(TodoType.REGISTER_COLLECTION_INVOICE);
        assertThat(view.todo().label()).isEqualTo("회수 송장 등록 필요 · 10.11까지");
        assertThat(view.todo().dueDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(present(claim(ClaimType.EXCHANGE, ClaimStatus.REQUESTED), null).label()).isEqualTo("교환 요청");
    }

    @Test
    @DisplayName("회수중 · 검수 중 · 환불 대기 — 기다리면 되는 구간은 로즈도 할 일도 없다")
    void waitingStages() {
        OrderClaim collecting = claim(ClaimType.RETURN, ClaimStatus.COLLECTING);
        collecting.getCollection().registerInvoice(DeliveryCarrier.CJ, "684922013378", NOW);

        assertView(present(collecting, null), UserClaimPhase.COLLECTING, "회수중", "CJ대한통운 684922013378",
                "진행 중 · 회수중");
        assertView(present(claim(ClaimType.RETURN, ClaimStatus.ARRIVED), null), UserClaimPhase.INSPECTING, "검수 중",
                "브랜드가 상품을 확인하고 있어요", "진행 중 · 검수 중");
        assertView(present(claim(ClaimType.RETURN, ClaimStatus.RECEIVED), null), UserClaimPhase.INSPECTING, "검수 중",
                "브랜드가 상품을 확인하고 있어요", "진행 중 · 검수 중");
        assertView(present(claim(ClaimType.RETURN, ClaimStatus.REFUND_PENDING), null), UserClaimPhase.APPROVED,
                "반품 승인", "검수 완료 · 환불 예정", "환불 처리 중");
    }

    @Test
    @DisplayName("교환 승인 뒤 — 재발송 준비 · 재발송")
    void exchangeReship() {
        assertView(present(claim(ClaimType.EXCHANGE, ClaimStatus.RESHIP_READY), null), UserClaimPhase.RESHIP_PREPARING,
                "교환 승인", "새 상품 발송 준비 중", "진행 중 · 재발송 준비");
        assertView(present(claim(ClaimType.EXCHANGE, ClaimStatus.RESHIPPING), null), UserClaimPhase.RESHIPPING,
                "재발송", "새 상품이 출발했어요", "진행 중 · 재발송");
    }

    @Test
    @DisplayName("거절 보류 — 판정이 다 끝나기 전에는 결제를 열지 않고, 끝나면 재발송 배송비 결제가 할 일이다")
    void rejectHold() {
        OrderClaim waiting = rejected(claim(ClaimType.RETURN, ClaimStatus.REJECT_HOLD));
        View waitingView = present(waiting, null);
        assertView(waitingView, UserClaimPhase.REJECTED_WAITING, "반품 반려", "함께 보낸 상품을 검수하고 있어요", "검수 반려");

        OrderClaim payable = finalized(rejected(claim(ClaimType.RETURN, ClaimStatus.REJECT_HOLD)));
        View payView = present(payable, charge(ClaimChargeStatus.PENDING, NOW.plusDays(14)));
        assertThat(payView.phase()).isEqualTo(UserClaimPhase.REJECTED_PAY);
        assertThat(payView.actionRequired()).isTrue();
        assertThat(payView.sub()).isEqualTo("재발송 배송비 결제 필요");
        assertThat(payView.listSub()).isEqualTo("검수 반려");
        assertThat(payView.todo().type()).isEqualTo(TodoType.PAY_RESHIP_FEE);
        assertThat(payView.todo().label()).isEqualTo("재발송 배송비 결제 필요 · 10.19까지");

        // 결제 기한이 지나도 할 일은 남는다 — 날짜만 빠진다.
        View overdue = present(payable, charge(ClaimChargeStatus.PENDING, NOW.minusDays(1)));
        assertThat(overdue.todo().label()).isEqualTo("재발송 배송비 결제 필요");
        assertThat(overdue.todo().dueDate()).isNull();
    }

    @Test
    @DisplayName("거절 뒤 재발송 — 정산 방법에 따라 보조 문구가 갈린다")
    void rejectedReship() {
        OrderClaim returnClaim = rejected(claim(ClaimType.RETURN, ClaimStatus.RESHIP_READY));
        OrderClaim exchangeClaim = rejected(claim(ClaimType.EXCHANGE, ClaimStatus.RESHIP_READY));

        assertView(present(returnClaim, charge(ClaimChargeStatus.DEDUCTED, null)), UserClaimPhase.REJECTED_PREPARING,
                "반품 반려", "환불액에서 재발송비 차감", "검수 반려");
        assertView(present(returnClaim, charge(ClaimChargeStatus.PAID, null)), UserClaimPhase.REJECTED_PREPARING,
                "반품 반려", "반려 상품 발송 준비 중", "검수 반려");
        assertView(present(exchangeClaim, charge(ClaimChargeStatus.COVERED, null)), UserClaimPhase.REJECTED_PREPARING,
                "교환 반려", "받은 상품을 다시 보내드려요", "검수 반려");
        assertView(present(rejected(claim(ClaimType.RETURN, ClaimStatus.RESHIPPING)), null),
                UserClaimPhase.REJECTED_RESHIPPING, "반품 반려", "반려 상품 재발송 중", "검수 반려 · 재발송");
    }

    @Test
    @DisplayName("종결 — 목록 보조 문구가 없다(항목이 원래 줄로 돌아간다). 거절은 반송 완료와 폐기를 가른다")
    void completed() {
        assertView(present(done(ClaimType.RETURN, ClaimResult.REFUNDED), null), UserClaimPhase.DONE, "반품 완료", null,
                null);
        assertView(present(done(ClaimType.EXCHANGE, ClaimResult.EXCHANGED), null), UserClaimPhase.DONE, "교환 완료",
                null, null);
        assertView(present(done(ClaimType.RETURN, ClaimResult.CANCELLED), null), UserClaimPhase.CANCELLED, "요청 취소",
                null, null);
        assertView(present(rejected(done(ClaimType.RETURN, ClaimResult.REJECTED)), null), UserClaimPhase.REJECTED_DONE,
                "반품 반려", "반려 상품 수령 완료", null);
        OrderClaim disposed = rejected(done(ClaimType.RETURN, ClaimResult.REJECTED));
        ReflectionTestUtils.setField(disposed, "disposedAt", NOW);
        assertView(present(disposed, null), UserClaimPhase.REJECTED_DISPOSED, "반품 반려", "보관 기간이 끝났어요", null);
    }

    @Test
    @DisplayName("검수 반려 단계만 탈색을 푼다")
    void rejectedStage() {
        assertThat(UserClaimPhase.REJECTED_PAY.isRejectedStage()).isTrue();
        assertThat(UserClaimPhase.REJECTED_RESHIPPING.isRejectedStage()).isTrue();
        assertThat(UserClaimPhase.REQUESTED.isRejectedStage()).isFalse();
        assertThat(UserClaimPhase.RESHIPPING.isRejectedStage()).isFalse();
    }

    // ------------------------------------------------------------------ 픽스처

    private static View present(OrderClaim claim, OrderClaimCharge charge) {
        return UserClaimPresenter.present(claim, charge, TODAY);
    }

    private static void assertView(View view, UserClaimPhase phase, String label, String sub, String listSub) {
        assertThat(view.phase()).isEqualTo(phase);
        assertThat(view.label()).isEqualTo(label);
        assertThat(view.sub()).isEqualTo(sub);
        assertThat(view.listSub()).isEqualTo(listSub);
        assertThat(view.actionRequired()).isFalse();
        assertThat(view.todo()).isNull();
    }

    static OrderClaim claim(ClaimType type, ClaimStatus status) {
        OrderClaimCollection collection = OrderClaimCollection.builder()
                .type(type).invoiceDueAt(LocalDate.of(2026, 10, 11).atTime(23, 59, 59)).createdAt(NOW).build();
        return OrderClaim.builder().collection(collection).type(type).quantity(1).status(status).requestedAt(NOW)
                .collectDueAt(NOW.plusDays(2)).build();
    }

    private static OrderClaim done(ClaimType type, ClaimResult result) {
        OrderClaim claim = claim(type, ClaimStatus.COMPLETED);
        ReflectionTestUtils.setField(claim, "result", result);
        return claim;
    }

    static OrderClaim rejected(OrderClaim claim) {
        ReflectionTestUtils.setField(claim, "rejectedAt", NOW);
        return claim;
    }

    private static OrderClaim finalized(OrderClaim claim) {
        ReflectionTestUtils.setField(claim.getCollection(), "finalizedAt", NOW);
        return claim;
    }

    private static OrderClaimCharge charge(ClaimChargeStatus status, LocalDateTime dueAt) {
        return OrderClaimCharge.builder().type(ClaimChargeType.REJECT_RESHIP).amount(3000).status(status).dueAt(dueAt)
                .createdAt(NOW).build();
    }
}
