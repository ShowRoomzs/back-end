package showroomz.api.admin.transaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;

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
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getOrders(new AdminOrderDto.SearchParams(tab, status, marketId, keyword),
                from, to, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminOrderDto.SummaryResponse> getSummary(
            @RequestParam(value = "marketId", required = false) Long marketId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(queryService.getSummary(new AdminOrderDto.SearchParams(null, null, marketId, keyword),
                from, to));
    }

    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDto.DetailResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(queryService.getOrder(orderId));
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
