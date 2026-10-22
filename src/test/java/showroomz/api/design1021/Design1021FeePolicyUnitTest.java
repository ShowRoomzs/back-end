package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.RemoteAreaPostcode;
import showroomz.domain.order.repository.RemoteAreaPostcodeRepository;
import showroomz.domain.order.service.ClaimFeePolicy;
import showroomz.domain.order.service.ClaimFeePolicy.ReturnContext;
import showroomz.domain.order.service.DeliveryRegionResolver;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryRegion;
import showroomz.domain.market.entity.Market;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1021 설계서 3절 — 반품 · 교환 비용 구조(기-18 · 기-20 · 기-93)의 순수 규칙. 스프링 없이 판정만 본다.
 */
@DisplayName("1021 3절 — 권역 판정 · 최초 배송비 차감 · 재발송 배송비 · 귀책 변경 운임")
class Design1021FeePolicyUnitTest {

    private static final int BASE_FEE = 3_000;

    // ------------------------------------------------------------------ 3-2 권역 판정

    @Nested
    @DisplayName("DeliveryRegionResolver — 우편번호 → 권역")
    class Region {

        private final RemoteAreaPostcodeRepository repository = mock(RemoteAreaPostcodeRepository.class);
        private final DeliveryRegionResolver resolver = new DeliveryRegionResolver(repository);

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"63000, JEJU", "63100, JEJU", "63644, JEJU", "63-100, JEJU", "62999, STANDARD", "63645, STANDARD"})
        @DisplayName("제주(63000 ~ 63644)는 등록 데이터 없이 코드가 판정한다 · 하이픈은 무시")
        void jejuByCode(String zip, DeliveryRegion expected) {
            when(repository.findCovering(anyString())).thenReturn(List.of());
            assertThat(resolver.resolve(zip)).isEqualTo(expected);
        }

        @Test
        @DisplayName("도서산간은 운영 등록 범위만 · 등록 범위가 제주로 표시돼 있으면 제주 우선 · 겹치지 않으면 일반")
        void remoteByRegistration() {
            when(repository.findCovering("23008")).thenReturn(List.of(
                    RemoteAreaPostcode.of("23000", "23010", DeliveryRegion.REMOTE, "옹진군", LocalDateTime.now())));
            when(repository.findCovering("59100")).thenReturn(List.of(
                    RemoteAreaPostcode.of("59000", "59200", DeliveryRegion.REMOTE, "완도군", LocalDateTime.now()),
                    RemoteAreaPostcode.of("59100", "59100", DeliveryRegion.JEJU, "겹침 등록", LocalDateTime.now())));
            when(repository.findCovering("06234")).thenReturn(List.of());

            assertThat(resolver.resolve("23008")).isEqualTo(DeliveryRegion.REMOTE);
            assertThat(resolver.resolve("59100")).isEqualTo(DeliveryRegion.JEJU);
            assertThat(resolver.resolve("06234")).isEqualTo(DeliveryRegion.STANDARD);
        }

        @ParameterizedTest(name = "\"{0}\" → 일반")
        @CsvSource(value = {"NULL", "''", "1234", "123456", "abcde"}, nullValues = "NULL")
        @DisplayName("우편번호를 모르면(빈 값 · 5자리가 아님) 일반 — 추가비를 매기지 않는다")
        void unknownPostcodeIsStandard(String zip) {
            assertThat(resolver.resolve(zip)).isEqualTo(DeliveryRegion.STANDARD);
        }

        @Test
        @DisplayName("권역 추가비 = 마켓 설정 · 일반 0 · 설정이 비면 0")
        void surchargeOfMarket() {
            Market market = mock(Market.class);
            when(market.getJejuSurcharge()).thenReturn(4_000);
            when(market.getRemoteAreaSurcharge()).thenReturn(null);

            assertThat(DeliveryRegionResolver.surchargeOf(market, DeliveryRegion.JEJU)).isEqualTo(4_000);
            assertThat(DeliveryRegionResolver.surchargeOf(market, DeliveryRegion.REMOTE)).isZero();
            assertThat(DeliveryRegionResolver.surchargeOf(market, DeliveryRegion.STANDARD)).isZero();
            assertThat(DeliveryRegionResolver.surchargeOf(null, DeliveryRegion.JEJU)).isZero();
        }
    }

    // ------------------------------------------------------------------ 3-3 클레임 비용

    @Nested
    @DisplayName("ClaimFeePolicy — 최초 배송비 차감(기-18)")
    class ReturnDeduction {

        private final ClaimFeePolicy policy = new ClaimFeePolicy();

        @Test
        @DisplayName("무료배송 · 소비자 귀책 반품 — 남는 금액이 기준 이상이면 0 · 미만이면 기본 배송비 · 기준과 같으면 0")
        void thresholdDecides() {
            OrderDeliveryGroup group = group(true, 50_000, 0);

            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group,
                    new ReturnContext(54_400, false))).isZero();
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group,
                    new ReturnContext(50_000, false))).isZero();
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group,
                    new ReturnContext(49_999, false))).isEqualTo(BASE_FEE);
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group,
                    new ReturnContext(0, false))).isEqualTo(BASE_FEE);
        }

        @Test
        @DisplayName("같은 하위주문에서 이미 뺐으면 다시 빼지 않는다")
        void onlyOncePerGroup() {
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group(true, 50_000, 0),
                    new ReturnContext(0, true))).isZero();
        }

        @Test
        @DisplayName("유료배송 주문 · 브랜드 귀책 · 교환은 차감 없음")
        void notApplicable() {
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group(false, 50_000, 0),
                    new ReturnContext(0, false))).isZero();
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.SELLER, group(true, 50_000, 0),
                    new ReturnContext(0, false))).isZero();
            assertThat(policy.returnDeduction(ClaimType.EXCHANGE, ClaimFeeBearer.CONSUMER, group(true, 50_000, 0),
                    new ReturnContext(0, false))).isZero();
        }

        @Test
        @DisplayName("기준을 모르는 옛 주문은 남는 금액과 무관하게 뺀다(무료배송으로 나간 사실만으로)")
        void legacyOrderWithoutThreshold() {
            assertThat(policy.returnDeduction(ClaimType.RETURN, ClaimFeeBearer.CONSUMER, group(true, null, 0),
                    new ReturnContext(1_000_000, false))).isEqualTo(BASE_FEE);
        }

        @Test
        @DisplayName("신청 화면 미리보기 — 전량 반품 기준 최대 차감액")
        void previewForFullReturn() {
            assertThat(policy.returnDeductionForFullReturn(ClaimType.RETURN, ClaimFeeBearer.CONSUMER,
                    group(true, 50_000, 0), false)).isEqualTo(BASE_FEE);
            assertThat(policy.returnDeductionForFullReturn(ClaimType.RETURN, ClaimFeeBearer.CONSUMER,
                    group(true, 50_000, 0), true)).isZero();
        }
    }

    @Nested
    @DisplayName("ClaimFeePolicy — 재발송 배송비(기-18 「편도 · 권역 추가비 별도」) · 귀책 변경 운임(기-93)")
    class Reship {

        private final ClaimFeePolicy policy = new ClaimFeePolicy();

        @Test
        @DisplayName("재발송 배송비 = 기본 배송비 + 결제 시점 권역 추가비 — 무료배송이어도 같다")
        void reshipIncludesSurcharge() {
            OrderDeliveryGroup jeju = group(true, 50_000, 4_000);
            OrderDeliveryGroup standard = group(false, 50_000, 0);

            assertThat(policy.reshipFee(jeju)).isEqualTo(BASE_FEE + 4_000);
            assertThat(policy.rejectReshipFee(jeju)).isEqualTo(BASE_FEE + 4_000);
            assertThat(policy.reshipFee(standard)).isEqualTo(BASE_FEE);
        }

        @Test
        @DisplayName("교환 재발송 배송비는 고객 귀책만 — 브랜드 귀책은 0")
        void exchangeFeeOnlyForConsumerFault() {
            OrderDeliveryGroup jeju = group(true, 50_000, 4_000);

            assertThat(policy.exchangeReshipFee(ClaimFeeBearer.CONSUMER, jeju)).isEqualTo(BASE_FEE + 4_000);
            assertThat(policy.exchangeReshipFee(ClaimFeeBearer.SELLER, jeju)).isZero();
        }

        @Test
        @DisplayName("귀책 변경 운임은 그 상품의 기본 배송비 정액 — 권역 추가비 · 소비자가 실제 낸 운임과 무관")
        void freightIsFlatBaseFee() {
            assertThat(policy.freightFlat(group(true, 50_000, 4_000))).isEqualTo(BASE_FEE);
        }

        @Test
        @DisplayName("권역 추가비를 모르는(옛) 주문은 0 으로 본다")
        void nullSurchargeIsZero() {
            OrderDeliveryGroup legacy = mock(OrderDeliveryGroup.class);
            when(legacy.getBaseDeliveryFee()).thenReturn(BASE_FEE);
            when(legacy.getRegionSurcharge()).thenReturn(null);

            assertThat(policy.reshipFee(legacy)).isEqualTo(BASE_FEE);
        }
    }

    private static OrderDeliveryGroup group(boolean freeShipping, Integer threshold, int surcharge) {
        OrderDeliveryGroup group = mock(OrderDeliveryGroup.class);
        when(group.isFreeShippingApplied()).thenReturn(freeShipping);
        when(group.getFreeShippingThreshold()).thenReturn(threshold);
        when(group.getBaseDeliveryFee()).thenReturn(BASE_FEE);
        when(group.getRegionSurcharge()).thenReturn(surcharge);
        return group;
    }
}
