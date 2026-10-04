package showroomz.api.seller.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.order.docs.SellerOrderControllerDocs;
import showroomz.api.seller.order.dto.BatchActionResponse;
import showroomz.api.seller.order.dto.CancelRequestRejectRequest;
import showroomz.api.seller.order.dto.PrepareStartRequest;
import showroomz.api.seller.order.dto.PurchaseOrderRequest;
import showroomz.api.seller.order.dto.PurchaseOrderTemplateDto;
import showroomz.api.seller.order.dto.SellerDirectCancelRequest;
import showroomz.api.seller.order.dto.SellerOrderDetailResponse;
import showroomz.api.seller.order.dto.SellerOrderListItem;
import showroomz.api.seller.order.dto.SellerOrderSummaryResponse;
import showroomz.api.seller.order.dto.ShipmentParseResponse;
import showroomz.api.seller.order.dto.ShipmentRegisterRequest;
import showroomz.api.seller.order.dto.ShipmentUpdateRequest;
import showroomz.api.seller.order.service.SellerOrderCommandService;
import showroomz.api.seller.order.service.SellerOrderCommandService.PurchaseOrderFile;
import showroomz.api.seller.order.service.SellerOrderQueryService;
import showroomz.domain.order.type.OrderDateBasis;
import showroomz.domain.order.type.OrderSearchType;
import showroomz.domain.order.type.OrderSortType;
import showroomz.domain.order.type.OrderTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/v1/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController implements SellerOrderControllerDocs {

    private final SellerOrderQueryService queryService;
    private final SellerOrderCommandService commandService;

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<SellerOrderListItem>> getOrders(
            @RequestParam(value = "tab", required = false) OrderTab tab,
            @RequestParam(value = "dateBasis", required = false) OrderDateBasis dateBasis,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "searchType", required = false) OrderSearchType searchType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) OrderSortType sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getOrders(getCurrentSellerEmail(), tab, dateBasis, from, to, searchType,
                keyword, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<SellerOrderSummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/{deliveryGroupId}")
    public ResponseEntity<SellerOrderDetailResponse> getOrder(@PathVariable Long deliveryGroupId) {
        return ResponseEntity.ok(queryService.getOrder(getCurrentSellerEmail(), deliveryGroupId));
    }

    // ── 준비 시작 · 발주서 ──────────────────────────────────────────────────

    @Override
    @PostMapping("/prepare-start")
    public ResponseEntity<BatchActionResponse> prepareStart(@Valid @RequestBody PrepareStartRequest request) {
        return ResponseEntity.ok(commandService.prepareStart(getCurrentSellerEmail(), request));
    }

    @Override
    @PostMapping("/purchase-order")
    public ResponseEntity<byte[]> downloadPurchaseOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderFile file = commandService.downloadPurchaseOrder(getCurrentSellerEmail(), request);
        return excelResponse(file.content(), file.filename());
    }

    @Override
    @GetMapping("/purchase-order/template")
    public ResponseEntity<PurchaseOrderTemplateDto.Response> getPurchaseOrderTemplate() {
        return ResponseEntity.ok(commandService.getTemplate(getCurrentSellerEmail()));
    }

    @Override
    @PutMapping("/purchase-order/template")
    public ResponseEntity<PurchaseOrderTemplateDto.Response> updatePurchaseOrderTemplate(
            @Valid @RequestBody PurchaseOrderTemplateDto.UpdateRequest request) {
        return ResponseEntity.ok(commandService.updateTemplate(getCurrentSellerEmail(), request));
    }

    // ── 송장 ────────────────────────────────────────────────────────────────

    @Override
    @PostMapping("/shipments")
    public ResponseEntity<BatchActionResponse> registerShipments(@Valid @RequestBody ShipmentRegisterRequest request) {
        return ResponseEntity.ok(commandService.registerShipments(getCurrentSellerEmail(), request));
    }

    @Override
    @PostMapping(value = "/shipments/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ShipmentParseResponse> parseShipments(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(commandService.parseShipments(getCurrentSellerEmail(), file));
    }

    @Override
    @GetMapping("/shipments/template")
    public ResponseEntity<byte[]> shipmentTemplate() {
        return excelResponse(commandService.shipmentTemplate(), "송장_업로드_양식.xlsx");
    }

    @Override
    @PatchMapping("/{deliveryGroupId}/shipment")
    public ResponseEntity<SellerOrderDetailResponse> updateShipment(
            @PathVariable Long deliveryGroupId, @Valid @RequestBody ShipmentUpdateRequest request) {
        return ResponseEntity.ok(commandService.updateShipment(getCurrentSellerEmail(), deliveryGroupId, request));
    }

    // ── 취소 ────────────────────────────────────────────────────────────────

    @Override
    @PostMapping("/cancel")
    public ResponseEntity<BatchActionResponse> directCancel(@Valid @RequestBody SellerDirectCancelRequest request) {
        return ResponseEntity.ok(commandService.directCancel(getCurrentSellerEmail(), request));
    }

    @Override
    @PostMapping("/cancel-requests/{cancelRequestId}/approve")
    public ResponseEntity<SellerOrderDetailResponse> approveCancelRequest(@PathVariable Long cancelRequestId) {
        return ResponseEntity.ok(commandService.approveCancelRequest(getCurrentSellerEmail(), cancelRequestId));
    }

    @Override
    @PostMapping("/cancel-requests/{cancelRequestId}/reject")
    public ResponseEntity<SellerOrderDetailResponse> rejectCancelRequest(
            @PathVariable Long cancelRequestId, @Valid @RequestBody CancelRequestRejectRequest request) {
        return ResponseEntity.ok(commandService.rejectCancelRequest(getCurrentSellerEmail(), cancelRequestId, request));
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> excelResponse(byte[] content, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
