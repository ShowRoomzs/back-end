package showroomz.api.admin.transaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.transaction.docs.AdminRefundControllerDocs;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.service.AdminRefundService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController implements AdminRefundControllerDocs {

    private final AdminRefundService refundService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminTransactionDto.RefundItem>> getRefunds(
            @RequestParam(value = "tab", required = false) AdminTransactionDto.RefundTab tab,
            @RequestParam(value = "route", required = false) AdminTransactionDto.RefundRoute route,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) AdminTransactionDto.RefundSort sort,
            @RequestParam(value = "days", required = false) Integer days,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(refundService.getRefunds(tab, route, keyword, sort, days, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminTransactionDto.RefundSummary> getSummary() {
        return ResponseEntity.ok(refundService.getSummary());
    }

    @Override
    @GetMapping("/{refundTaskId}")
    public ResponseEntity<AdminTransactionDto.RefundDetail> getRefund(@PathVariable Long refundTaskId) {
        return ResponseEntity.ok(refundService.getRefund(refundTaskId));
    }

    @Override
    @PostMapping("/{refundTaskId}/void")
    public ResponseEntity<AdminTransactionDto.RefundItem> voidRefund(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long refundTaskId,
            @Valid @RequestBody AdminTransactionDto.RefundVoidRequest request) {
        return ResponseEntity.ok(refundService.voidRefund(AdminOrderController.operatorId(principal), refundTaskId, request));
    }

    @Override
    @PostMapping("/{refundTaskId}/manual-complete")
    public ResponseEntity<AdminTransactionDto.RefundItem> recordManual(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long refundTaskId,
            @Valid @RequestBody AdminTransactionDto.RefundManualCompleteRequest request) {
        return ResponseEntity.ok(refundService.recordManual(AdminOrderController.operatorId(principal), refundTaskId, request));
    }

    @Override
    @PostMapping("/{refundTaskId}/execute")
    public ResponseEntity<AdminTransactionDto.RefundExecuteResponse> execute(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long refundTaskId) {
        return ResponseEntity.ok(refundService.execute(AdminOrderController.operatorId(principal), refundTaskId));
    }
}
