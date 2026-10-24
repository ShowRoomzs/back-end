package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.ClaimFeePolicy;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.DeliveryRegion;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.address.entity.DeliveryAddress;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P3 — 반품 · 교환 비용 구조(3절 · 기-18 · 기-20) · 정산 분해 줄(6-1).
 */
@DisplayName("1021 P3 — 권역 추가비 · 결제 시점 스냅샷 · 일부 반품 재판정 · 정산 「반품 시 차감한 최초 배송비」")
class Design1021P3FeeStructureIntegrationTest extends SettlementTestSupport {

    private static final int JEJU_SURCHARGE = 4_000;

    @Autowired private OrderClaimRepository claimRepository;
    @Autowired private ClaimFeePolicy feePolicy;

    @Test
    @DisplayName("제주 배송지 — 무료배송이어도 권역 추가비 부과 · 스냅샷 3열 · 재발송 배송비에 추가비 · 추가비가 달라지는 배송지로 변경 불가")
    void jejuSurchargeSnapshot() throws Exception {
        jdbc.update("UPDATE market SET jeju_surcharge = ? WHERE market_id = ?", JEJU_SURCHARGE, brand.marketId());
        jdbc.update("UPDATE delivery_address SET zip_code = '63100' WHERE address_id = ?", address.getId());

        OrderDeliveryGroup group = purchase(creamVariant, 2).group();   // 54,400 · 무료배송
        assertThat(group.isFreeShippingApplied()).isTrue();
        assertThat(group.getDeliveryRegion()).isEqualTo(DeliveryRegion.JEJU);
        assertThat(group.getRegionSurcharge()).isEqualTo(JEJU_SURCHARGE);
        assertThat(group.getDeliveryFee()).isEqualTo(JEJU_SURCHARGE);
        assertThat(group.getFreeShippingThreshold()).isEqualTo(50_000);
        assertThat(feePolicy.reshipFee(group)).isEqualTo(DELIVERY_FEE + JEJU_SURCHARGE);

        DeliveryAddress seoul = deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(consumer).recipientName("김수민").zipCode("06234").address("서울 강남구 테헤란로 000")
                .detailAddress("2층").phoneNumber("010-1234-5678").isDefault(false).build());
        mockMvc.perform(patch("/v1/user/orders/" + group.getOrder().getId() + "/delivery-address")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("addressId", seoul.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ADDRESS_REGION_CHANGED"));
    }

    @Test
    @DisplayName("기-18 일부 반품 — 남는 금액이 기준 이상이면 차감 없음 · 기준 아래로 내려가면 최초 배송비 차감 · 같은 주문에서 한 번만")
    void partialReturnDeduction() {
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 3).group());   // 81,600 · 무료배송(기준 50,000)
        OrderProduct cream = itemsOf(group).get(0);

        Long first = returnClaim(group, cream, 1, ClaimReason.CHANGE_OF_MIND);   // 남는 54,400 ≥ 50,000
        assertThat(collectionOf(first).getReturnDeduction()).isZero();

        Long second = returnClaim(group, cream, 1, ClaimReason.CHANGE_OF_MIND);  // 남는 27,200 < 50,000
        assertThat(collectionOf(second).getReturnDeduction()).isEqualTo(DELIVERY_FEE);

        Long third = returnClaim(group, cream, 1, ClaimReason.CHANGE_OF_MIND);   // 이미 뺐다
        assertThat(collectionOf(third).getReturnDeduction()).isZero();
    }

    @Test
    @DisplayName("6-1 — 반품 시 차감한 최초 배송비는 브랜드 정산에 남는다(+ 줄 · 건수) · 검산이 맞는다")
    void settlementKeepsReturnDeduction() {
        OrderDeliveryGroup returned = deliveredFrom(purchase(creamVariant, 2).group());   // 54,400 · 무료배송
        OrderDeliveryGroup kept = confirmedGroup(serumVariant, 1);
        Long claimId = returnClaim(returned, itemsOf(returned).get(0), 2, ClaimReason.CHANGE_OF_MIND);
        assertThat(collectionOf(claimId).getReturnDeduction()).isEqualTo(DELIVERY_FEE);
        passClaim(claimId);
        endGroupBuy();

        Settlement s = generate(GENERATED_AT);

        assertThat(s.getReturnDeductionFeeAmount()).isEqualTo(DELIVERY_FEE);
        assertThat(s.getReturnDeductionCount()).isEqualTo(1);
        assertThat(s.getFreightAdjustmentAmount()).isZero();
        assertThat(s.getBrandPayoutAmount() + s.getBrandClawbackAmount()).isEqualTo(s.getConfirmedSalesAmount()
                - s.getPgFeeAmount() - s.getPlatformFeeAmount() - s.getRewardAmount() - s.getRewardVatAmount()
                + s.getReshipFeeAmount() + s.getConsumerDeliveryFeeAmount() + DELIVERY_FEE);
        assertThat(kept.getId()).isNotNull();
    }

    private showroomz.domain.order.entity.OrderClaimCollection collectionOf(Long claimId) {
        return inTransaction(() -> {
            var collection = claimRepository.findById(claimId).orElseThrow().getCollection();
            collection.getReturnDeduction();
            return collection;
        });
    }

    private static final java.time.LocalDateTime GENERATED_AT = java.time.LocalDateTime.of(2026, 10, 2, 15, 0);
}
