package showroomz.domain.order.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.entity.MarketPurchaseOrderTemplate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("이행 상태·탭·택배사·발주서 컬럼 규칙(34 설계서 1-2 · 1-3 · 1-7 · 1-8) — 서버가 소유하는 값의 계약")
class OrderFulfillmentTypesTest {

    @Test
    @DisplayName("이행 상태 8종 — 배지 톤은 서버가 내린다 · 반송중은 위험(§34-1 최신 표)")
    void fulfillmentStatusTones() {
        assertThat(FulfillmentStatus.values()).hasSize(8);
        assertThat(FulfillmentStatus.NEW.getTone()).isEqualTo(OrderBadgeTone.WARNING);
        assertThat(FulfillmentStatus.PREPARING.getTone()).isEqualTo(OrderBadgeTone.INFO);
        assertThat(FulfillmentStatus.SHIPPING.getTone()).isEqualTo(OrderBadgeTone.INFO);
        assertThat(FulfillmentStatus.RETURNING.getTone()).isEqualTo(OrderBadgeTone.DANGER);
        assertThat(FulfillmentStatus.DELIVERED.getTone()).isEqualTo(OrderBadgeTone.SUCCESS);
        assertThat(FulfillmentStatus.CONFIRMED.getTone()).isEqualTo(OrderBadgeTone.SUCCESS);
        assertThat(FulfillmentStatus.CANCELLED.getTone()).isEqualTo(OrderBadgeTone.NEUTRAL);
    }

    @Test
    @DisplayName("작업 큐 = NEW·PREPARING · 송장 중복 검사 대상 = 종결 전(NEW~DELIVERED) · 미종결 = 확정·취소·결제 전이 아닌 것")
    void statusSets() {
        assertThat(FulfillmentStatus.WORKABLE).containsExactlyInAnyOrder(FulfillmentStatus.NEW, FulfillmentStatus.PREPARING);
        assertThat(FulfillmentStatus.INVOICE_ACTIVE).containsExactlyInAnyOrder(FulfillmentStatus.NEW,
                FulfillmentStatus.PREPARING, FulfillmentStatus.SHIPPING, FulfillmentStatus.RETURNING,
                FulfillmentStatus.DELIVERED);
        assertThat(Arrays.stream(FulfillmentStatus.values()).filter(FulfillmentStatus::isSettlementOpen))
                .containsExactlyInAnyOrder(FulfillmentStatus.NEW, FulfillmentStatus.PREPARING,
                        FulfillmentStatus.SHIPPING, FulfillmentStatus.RETURNING, FulfillmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("탭 9종 — 결제 전은 어느 탭에도 없고 · 취소 요청은 오버레이 필터로 NEW/PREPARING 과 갈린다")
    void tabs() {
        assertThat(OrderTab.values()).hasSize(9);
        assertThat(OrderTab.ALL.getStatuses()).isEqualTo(EnumSet.complementOf(EnumSet.of(FulfillmentStatus.PENDING)));
        for (OrderTab tab : OrderTab.values()) {
            assertThat(tab.getStatuses()).as(tab.name()).doesNotContain(FulfillmentStatus.PENDING);
        }
        assertThat(OrderTab.NEW.getPendingCancelFilter()).isEqualTo(OrderTab.PendingCancelFilter.NONE);
        assertThat(OrderTab.PREPARING.getPendingCancelFilter()).isEqualTo(OrderTab.PendingCancelFilter.NONE);
        assertThat(OrderTab.CANCEL_REQUESTED.getPendingCancelFilter()).isEqualTo(OrderTab.PendingCancelFilter.EXISTS);
        assertThat(OrderTab.CANCEL_REQUESTED.getStatuses())
                .containsExactlyInAnyOrder(FulfillmentStatus.NEW, FulfillmentStatus.PREPARING);
        assertThat(OrderTab.SHIPPING.getPendingCancelFilter()).isEqualTo(OrderTab.PendingCancelFilter.ANY);
    }

    @Test
    @DisplayName("택배사 11종 — 한글명·코드명(대소문자 무시)·앞뒤 공백 허용 · 그 밖은 null(자유 입력 없음)")
    void carrierFromLabel() {
        assertThat(DeliveryCarrier.values()).hasSize(11);
        assertThat(DeliveryCarrier.fromLabel("CJ대한통운")).isEqualTo(DeliveryCarrier.CJ);
        assertThat(DeliveryCarrier.fromLabel(" 우체국택배 ")).isEqualTo(DeliveryCarrier.EPOST);
        assertThat(DeliveryCarrier.fromLabel("hanjin")).isEqualTo(DeliveryCarrier.HANJIN);
        assertThat(DeliveryCarrier.fromLabel("CU편의점택배")).isEqualTo(DeliveryCarrier.CU);
        assertThat(DeliveryCarrier.fromLabel("페덱스")).isNull();
        assertThat(DeliveryCarrier.fromLabel("")).isNull();
        assertThat(DeliveryCarrier.fromLabel(null)).isNull();
        // 스마트택배 택배사 코드 — 채워진 것끼리 겹치면 다른 택배사의 송장을 조회한다. 쿠팡·우리택배는 코드 미확인.
        assertThat(DeliveryCarrier.values()).extracting(DeliveryCarrier::getTrackerCode)
                .filteredOn(code -> code != null).doesNotHaveDuplicates().hasSize(9);
    }

    @Test
    @DisplayName("발주서 컬럼 13종 · 기본 8종 — 「소비자 요청 배송일」은 없다(시안 정정 #21)")
    void purchaseOrderColumns() {
        assertThat(PurchaseOrderColumn.values()).hasSize(13);
        assertThat(Arrays.stream(PurchaseOrderColumn.values()).filter(PurchaseOrderColumn::isBasic))
                .containsExactly(PurchaseOrderColumn.ORDER_NUMBER, PurchaseOrderColumn.RECIPIENT,
                        PurchaseOrderColumn.PHONE, PurchaseOrderColumn.ZIP_CODE, PurchaseOrderColumn.ADDRESS,
                        PurchaseOrderColumn.PRODUCT_NAME, PurchaseOrderColumn.OPTION, PurchaseOrderColumn.QUANTITY);
    }

    @Test
    @DisplayName("발주서 템플릿은 CSV 로 저장하고 순서를 보존해 되읽는다")
    void templateRoundTrip() {
        Market market = new Market();
        ReflectionTestUtils.setField(market, "id", 1L);
        List<PurchaseOrderColumn> columns = List.of(PurchaseOrderColumn.QUANTITY, PurchaseOrderColumn.RECIPIENT,
                PurchaseOrderColumn.SUB_ORDER_NUMBER);

        MarketPurchaseOrderTemplate template = MarketPurchaseOrderTemplate.of(market, columns, 3L);
        assertThat(template.getColumns()).isEqualTo("QUANTITY,RECIPIENT,SUB_ORDER_NUMBER");
        assertThat(template.columnList()).containsExactlyElementsOf(columns);
        assertThat(template.getUpdatedBy()).isEqualTo(3L);

        template.update(List.of(PurchaseOrderColumn.PHONE), 4L);
        assertThat(template.columnList()).containsExactly(PurchaseOrderColumn.PHONE);
        assertThat(template.getUpdatedBy()).isEqualTo(4L);
    }

    @Test
    @DisplayName("취소 유형·사유 라벨 — 취소 탭 「취소 사유」 열과 상세 문구의 원본")
    void cancelLabels() {
        assertThat(OrderCancelType.CONSUMER.getLabel()).isEqualTo("소비자 취소 · 준비 시작 전");
        assertThat(OrderCancelType.REQUEST_APPROVED.getLabel()).isEqualTo("취소 요청 승인 · 브랜드 승인");
        assertThat(OrderCancelType.SELLER_DIRECT.getLabel()).isEqualTo("브랜드 직권 취소");
        assertThat(Arrays.stream(CancelRequestReason.values()).map(CancelRequestReason::getLabel))
                .containsExactly("단순 변심", "주문 실수", "다른 결제 수단으로 변경", "기타");
        assertThat(Arrays.stream(SellerCancelReason.values()).map(SellerCancelReason::getLabel))
                .containsExactly("품절", "상품 하자", "배송 불가 지역", "기타");
    }
}
