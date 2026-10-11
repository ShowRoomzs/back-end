package showroomz.api.admin.transaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.transaction.docs.AdminOrderControllerDocs;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.admin.transaction.service.AdminOrderCommandService;
import showroomz.api.admin.transaction.service.AdminOrderQueryService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.order.type.AdminOrderSearchType;
import showroomz.domain.order.type.AdminOrderSort;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController implements AdminOrderControllerDocs {

    private final AdminOrderQueryService queryService;
    private final AdminOrderCommandService commandService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminOrderDto.ListItem>> getOrders(
            @RequestParam(value = "tab", required = false) AdminOrderTab tab,
            @RequestParam(value = "status", required = false) FulfillmentStatus status,
            @RequestParam(value = "marketId", required = false) Long marketId,
            @RequestParam(value = "groupBuyId", required = false) Long groupBuyId,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam(value = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam(value = "trackingAlert", required = false) TrackingAlert trackingAlert,
            @RequestParam(value = "searchType", required = false) AdminOrderSearchType searchType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) AdminOrderSort sort,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getOrders(new AdminOrderDto.SearchParams(tab, status, marketId, groupBuyId,
                creatorId, paymentMethod, trackingAlert, searchType, keyword, sort), from, to, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminOrderDto.SummaryResponse> getSummary(
            @RequestParam(value = "marketId", required = false) Long marketId,
            @RequestParam(value = "groupBuyId", required = false) Long groupBuyId,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam(value = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam(value = "searchType", required = false) AdminOrderSearchType searchType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(queryService.getSummary(new AdminOrderDto.SearchParams(null, null, marketId, groupBuyId,
                creatorId, paymentMethod, null, searchType, keyword, null),
                from, to));
    }

    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDto.DetailResponse> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable Long orderId) {
        // 개인정보 열람 기록(41 보고 9번 · 권고) — 수취인 · 연락처 · 배송지를 전체 노출하는 대신 누가 언제 열었는지 남긴다.
        log.info("어드민 주문 상세 열람 - operatorId: {}, orderId: {}", operatorId(principal), orderId);
        return ResponseEntity.ok(queryService.getOrder(orderId));
    }

    @Override
    @PostMapping("/groups/{deliveryGroupId}/lost")
    public ResponseEntity<AdminOrderDto.DetailResponse> markLost(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable Long deliveryGroupId,
                                                                 @Valid @RequestBody AdminOrderDto.MarkLostRequest request) {
        return ResponseEntity.ok(commandService.markLost(operatorId(principal), deliveryGroupId, request));
    }

    @Override
    @PostMapping("/groups/{deliveryGroupId}/delivered")
    public ResponseEntity<AdminOrderDto.DetailResponse> markDelivered(@AuthenticationPrincipal UserPrincipal principal,
                                                                      @PathVariable Long deliveryGroupId,
                                                                      @Valid @RequestBody AdminOrderDto.MarkDeliveredRequest request) {
        return ResponseEntity.ok(commandService.markDelivered(operatorId(principal), deliveryGroupId, request));
    }

    @Override
    @PatchMapping("/groups/{deliveryGroupId}/delivered-at")
    public ResponseEntity<AdminOrderDto.DetailResponse> correctDeliveredAt(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryGroupId,
            @Valid @RequestBody AdminOrderDto.CorrectDeliveredAtRequest request) {
        return ResponseEntity.ok(commandService.correctDeliveredAt(operatorId(principal), deliveryGroupId, request));
    }

    @Override
    @PostMapping("/groups/{deliveryGroupId}/shipment")
    public ResponseEntity<AdminOrderDto.DetailResponse> registerShipment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryGroupId,
            @Valid @RequestBody AdminOrderDto.ShipmentRequest request) {
        return ResponseEntity.ok(commandService.registerShipment(operatorId(principal), deliveryGroupId, request));
    }

    @Override
    @PostMapping("/groups/{deliveryGroupId}/cancel")
    public ResponseEntity<AdminOrderDto.DetailResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryGroupId,
            @Valid @RequestBody AdminOrderDto.CancelCommand request) {
        return ResponseEntity.ok(commandService.cancel(operatorId(principal), deliveryGroupId, request));
    }

    @Override
    @PostMapping("/groups/{deliveryGroupId}/refund-tasks")
    public ResponseEntity<AdminOrderDto.DetailResponse> enqueueRefund(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryGroupId,
            @Valid @RequestBody AdminOrderDto.OperatorRefundRequest request) {
        return ResponseEntity.ok(commandService.enqueueRefund(operatorId(principal), deliveryGroupId, request));
    }

    @Override
    @PostMapping("/groups/{deliveryGroupId}/defect-claims")
    public ResponseEntity<AdminOrderDto.DefectClaimResponse> openDefectClaim(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryGroupId,
            @Valid @RequestBody AdminOrderDto.DefectClaimRequest request) {
        return ResponseEntity.ok(commandService.openDefectClaim(operatorId(principal), deliveryGroupId, request));
    }

    static Long operatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
