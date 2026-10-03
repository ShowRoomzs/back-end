package showroomz.api.seller.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.seller.order.dto.BatchActionResponse;
import showroomz.api.seller.order.dto.ShipmentRegisterRequest;
import showroomz.api.seller.order.dto.ShipmentUpdateRequest;
import showroomz.api.seller.order.service.SellerOrderAccessGuard.SellerScope;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.MarketPurchaseOrderTemplateRepository;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.PurchaseOrderDownloadLogRepository;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 송장 형식 검증의 세 갈래(34 설계서 0-4 · 3-2) — 통합 테스트는 Noop 스텁(UNAVAILABLE)만 타므로, 연동 후에 열릴
 * VALID·INVALID 분기는 포트를 대역으로 바꿔 여기서 본다. 검사 순서(요청 내 중복 → 전역 중복 → 형식 → 상태)도 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("송장 등록·수정 — 택배 연동 형식 판정 VALID / INVALID / UNAVAILABLE")
class SellerOrderCommandServiceTrackerTest {

    private static final String EMAIL = "brand@showroomz.test";
    private static final Long SELLER_ID = 3L;
    private static final Long MARKET_ID = 7L;
    private static final Long GROUP_ID = 11L;

    @Mock private SellerOrderAccessGuard accessGuard;
    @Mock private SellerOrderQueryService queryService;
    @Mock private OrderDeliveryGroupRepository deliveryGroupRepository;
    @Mock private OrderProductRepository orderProductRepository;
    @Mock private OrderCancelRequestRepository cancelRequestRepository;
    @Mock private MarketPurchaseOrderTemplateRepository templateRepository;
    @Mock private PurchaseOrderDownloadLogRepository downloadLogRepository;
    @Mock private OrderFulfillmentService fulfillmentService;
    @Mock private PurchaseOrderExcelWriter excelWriter;
    @Mock private ShipmentExcelParser excelParser;
    @Mock private DeliveryTrackerPort tracker;
    @Mock private OrderProperties orderProperties;

    @InjectMocks private SellerOrderCommandService service;

    private SellerScope scope;

    @BeforeEach
    void setUp() {
        Market market = new Market();
        ReflectionTestUtils.setField(market, "id", MARKET_ID);
        scope = new SellerScope(SELLER_ID, market);
        when(accessGuard.resolve(EMAIL)).thenReturn(scope);
    }

    @Test
    @DisplayName("INVALID — 하드 차단 · 상태 전이·이력 없음 · 자릿수/체크디지트 구분 없는 한 문구")
    void invalidFormatIsSkipped() {
        noActiveInvoice();
        when(tracker.validateInvoice(DeliveryCarrier.CJ, "123456789012")).thenReturn(ValidationResult.INVALID);

        BatchActionResponse response = service.registerShipments(EMAIL, register("1234-5678-9012"));

        assertThat(response.succeeded()).isZero();
        assertThat(response.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.code()).isEqualTo("INVOICE_FORMAT_INVALID");
            assertThat(skipped.message()).isEqualTo("송장번호 형식이 올바르지 않습니다. 다시 확인해 주세요.");
        });
        verify(deliveryGroupRepository, never()).registerInvoice(anyLong(), anyLong(), any(), anyString(), any());
        verifyNoInteractions(fulfillmentService);
    }

    @Test
    @DisplayName("VALID — 등록되고 이력에 「검증 생략」 표기가 붙지 않는다")
    void validFormatRegistersWithoutSkipNote() {
        noActiveInvoice();
        when(tracker.validateInvoice(DeliveryCarrier.CJ, "123456789012")).thenReturn(ValidationResult.VALID);
        when(deliveryGroupRepository.registerInvoice(eq(GROUP_ID), eq(MARKET_ID), eq(DeliveryCarrier.CJ),
                eq("123456789012"), any())).thenReturn(1);

        BatchActionResponse response = service.registerShipments(EMAIL, register("1234-5678-9012"));

        assertThat(response.succeeded()).isEqualTo(1);
        verify(fulfillmentService).appendHistory(eq(GROUP_ID), eq(FulfillmentEventType.INVOICE_REGISTERED),
                eq(FulfillmentActorType.SELLER), eq(SELLER_ID), eq("CJ대한통운 123456789012"), any());
    }

    @Test
    @DisplayName("UNAVAILABLE(연동 전·장애) — 통과로 간주하지 않고 「형식 검증 생략」으로 기록한 채 등록한다")
    void unavailableRegistersWithSkipNote() {
        noActiveInvoice();
        when(tracker.validateInvoice(DeliveryCarrier.CJ, "123456789012")).thenReturn(ValidationResult.UNAVAILABLE);
        when(deliveryGroupRepository.registerInvoice(eq(GROUP_ID), eq(MARKET_ID), eq(DeliveryCarrier.CJ),
                eq("123456789012"), any())).thenReturn(1);

        service.registerShipments(EMAIL, register("123456789012"));

        verify(fulfillmentService).appendHistory(eq(GROUP_ID), eq(FulfillmentEventType.INVOICE_REGISTERED),
                eq(FulfillmentActorType.SELLER), eq(SELLER_ID), eq("CJ대한통운 123456789012 · 형식 검증 생략(연동 전)"),
                any());
    }

    @Test
    @DisplayName("전역 중복이 형식 판정보다 먼저다 — 겹치면 연동 API 를 부르지 않는다")
    void duplicateCheckedBeforeFormat() {
        OrderDeliveryGroup owner = group(99L, "20261003-000099");
        when(deliveryGroupRepository.findActiveByInvoice(eq(DeliveryCarrier.CJ), eq("123456789012"), any()))
                .thenReturn(List.of(owner));

        BatchActionResponse response = service.registerShipments(EMAIL, register("123456789012"));

        assertThat(response.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.code()).isEqualTo("INVOICE_DUPLICATE");
            assertThat(skipped.message()).isEqualTo("20261003-000099에 이미 등록된 번호입니다.");
        });
        verifyNoInteractions(tracker);
    }

    @Test
    @DisplayName("송장 수정도 INVALID 면 400 INVOICE_FORMAT_INVALID — UPDATE 를 시도하지 않는다")
    void updateRejectsInvalidFormat() {
        when(accessGuard.loadOwned(GROUP_ID, scope)).thenReturn(group(GROUP_ID, "20261003-000001"));
        when(tracker.validateInvoice(DeliveryCarrier.HANJIN, "777788889999")).thenReturn(ValidationResult.INVALID);

        assertThatThrownBy(() -> service.updateShipment(EMAIL, GROUP_ID,
                new ShipmentUpdateRequest(DeliveryCarrier.HANJIN, "7777-8888-9999")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_FORMAT_INVALID);
        verify(deliveryGroupRepository, never()).updateInvoice(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("송장 수정 VALID — 이력 detail 은 「구 택배사 번호 → 신 택배사 번호」")
    void updateRecordsBeforeAndAfter() {
        OrderDeliveryGroup group = group(GROUP_ID, "20261003-000001");
        ReflectionTestUtils.setField(group, "carrier", DeliveryCarrier.CJ);
        ReflectionTestUtils.setField(group, "trackingNumber", "444455556666");
        when(accessGuard.loadOwned(GROUP_ID, scope)).thenReturn(group);
        when(tracker.validateInvoice(DeliveryCarrier.HANJIN, "777788889999")).thenReturn(ValidationResult.VALID);
        when(deliveryGroupRepository.findActiveByInvoice(eq(DeliveryCarrier.HANJIN), eq("777788889999"), any()))
                .thenReturn(List.of());
        when(deliveryGroupRepository.updateInvoice(GROUP_ID, MARKET_ID, DeliveryCarrier.HANJIN, "777788889999"))
                .thenReturn(1);

        service.updateShipment(EMAIL, GROUP_ID, new ShipmentUpdateRequest(DeliveryCarrier.HANJIN, "7777-8888-9999"));

        verify(fulfillmentService).appendHistory(eq(GROUP_ID), eq(FulfillmentEventType.INVOICE_UPDATED),
                eq(FulfillmentActorType.SELLER), eq(SELLER_ID),
                eq("CJ대한통운 444455556666 → 한진택배 777788889999"), any(LocalDateTime.class));
    }

    // ------------------------------------------------------------------ 보조

    private void noActiveInvoice() {
        when(deliveryGroupRepository.findActiveByInvoice(eq(DeliveryCarrier.CJ), eq("123456789012"),
                eq(FulfillmentStatus.INVOICE_ACTIVE))).thenReturn(List.of());
    }

    private static ShipmentRegisterRequest register(String trackingNumber) {
        return new ShipmentRegisterRequest(List.of(
                new ShipmentRegisterRequest.Row(GROUP_ID, DeliveryCarrier.CJ, trackingNumber)));
    }

    private static OrderDeliveryGroup group(Long id, String orderNumber) {
        Order order = Order.create(null, orderNumber, new Order.Totals(27_200, 0, 3_000, 30_200),
                new Order.AddressSnapshot("김수민", "010-1234-5678", "06234", "서울", "12층"),
                null, "크림", "key-" + id, LocalDateTime.now().plusMinutes(30));
        OrderDeliveryGroup group = OrderDeliveryGroup.builder().order(order).productTotal(27_200).deliveryFee(3_000).build();
        ReflectionTestUtils.setField(group, "id", id);
        ReflectionTestUtils.setField(group, "fulfillmentStatus", FulfillmentStatus.SHIPPING);
        return group;
    }
}
