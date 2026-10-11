package showroomz.api.seller.claim.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("반품·교환 목록 「재발송비」 열(결정 14) — 반려 건은 반려 재발송비 · 교환은 선결제분 · 청구가 없으면 null")
class SellerClaimReshipFeeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 10, 0);

    @Test
    @DisplayName("고객 귀책 교환 — 요청 때 낸 교환 재발송비를 「결제됨」으로 · 금액 그대로")
    void exchangePrepaid() {
        SellerClaimListItem.ReshipFee fee = SellerClaimAssembler.reshipFee(claim(ClaimType.EXCHANGE),
                List.of(charge(ClaimChargeType.EXCHANGE_RESHIP, 6000, ClaimChargeStatus.PAID)));

        assertThat(fee.amount()).isEqualTo(6000);
        assertThat(fee.status()).isEqualTo(ClaimChargeStatus.PAID);
        assertThat(fee.statusLabel()).isEqualTo("결제됨");
    }

    @Test
    @DisplayName("반려 건 — 교환 선결제가 있어도 반려 재발송비를 본다(결제 대기 · 충당)")
    void rejectedUsesRejectReship() {
        List<OrderClaimCharge> charges = List.of(
                charge(ClaimChargeType.EXCHANGE_RESHIP, 6000, ClaimChargeStatus.PAID),
                charge(ClaimChargeType.REJECT_RESHIP, 3000, ClaimChargeStatus.PENDING));

        SellerClaimListItem.ReshipFee fee = SellerClaimAssembler.reshipFee(rejected(claim(ClaimType.EXCHANGE)), charges);
        assertThat(fee.amount()).isEqualTo(3000);
        assertThat(fee.statusLabel()).isEqualTo("결제 대기");

        SellerClaimListItem.ReshipFee covered = SellerClaimAssembler.reshipFee(rejected(claim(ClaimType.RETURN)),
                List.of(charge(ClaimChargeType.REJECT_RESHIP, 3000, ClaimChargeStatus.COVERED)));
        assertThat(covered.status()).isEqualTo(ClaimChargeStatus.COVERED);
    }

    @Test
    @DisplayName("청구가 없으면 null — 브랜드 귀책 교환(0원) · 반려되지 않은 반품 · 반려 판정 전")
    void noCharge() {
        assertThat(SellerClaimAssembler.reshipFee(claim(ClaimType.EXCHANGE), List.of())).isNull();
        assertThat(SellerClaimAssembler.reshipFee(claim(ClaimType.RETURN),
                List.of(charge(ClaimChargeType.REJECT_RESHIP, 3000, ClaimChargeStatus.PENDING)))).isNull();
        assertThat(SellerClaimAssembler.reshipFee(rejected(claim(ClaimType.RETURN)), List.of())).isNull();
    }

    @Test
    @DisplayName("같은 종류가 여럿이면 마지막 청구가 지금 값이다")
    void latestChargeWins() {
        SellerClaimListItem.ReshipFee fee = SellerClaimAssembler.reshipFee(rejected(claim(ClaimType.RETURN)), List.of(
                charge(ClaimChargeType.REJECT_RESHIP, 3000, ClaimChargeStatus.VOID),
                charge(ClaimChargeType.REJECT_RESHIP, 3000, ClaimChargeStatus.PAID)));

        assertThat(fee.status()).isEqualTo(ClaimChargeStatus.PAID);
    }

    private static OrderClaim claim(ClaimType type) {
        OrderClaimCollection collection = OrderClaimCollection.builder()
                .type(type).invoiceDueAt(NOW.plusDays(7)).createdAt(NOW).build();
        return OrderClaim.builder().collection(collection).type(type).quantity(1).status(ClaimStatus.RESHIP_READY)
                .requestedAt(NOW).collectDueAt(NOW.plusDays(2)).build();
    }

    private static OrderClaim rejected(OrderClaim claim) {
        ReflectionTestUtils.setField(claim, "rejectedAt", NOW);
        return claim;
    }

    private static OrderClaimCharge charge(ClaimChargeType type, int amount, ClaimChargeStatus status) {
        return OrderClaimCharge.builder().type(type).amount(amount).status(status).createdAt(NOW).build();
    }
}
