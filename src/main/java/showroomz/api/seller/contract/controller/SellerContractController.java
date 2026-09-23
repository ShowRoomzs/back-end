package showroomz.api.seller.contract.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.contract.docs.SellerContractControllerDocs;
import showroomz.api.seller.contract.dto.ContractCancelRequest;
import showroomz.api.seller.contract.dto.ContractClausesResponse;
import showroomz.api.seller.contract.dto.ContractCreateRequest;
import showroomz.api.seller.contract.dto.ContractCreateResponse;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.api.seller.contract.dto.ContractDocumentDownloadResponse;
import showroomz.api.seller.contract.dto.ContractDuplicateResponse;
import showroomz.api.seller.contract.dto.ContractFormSourcesResponse;
import showroomz.api.seller.contract.dto.ContractListItem;
import showroomz.api.seller.contract.dto.ContractResendRequestResponse;
import showroomz.api.seller.contract.dto.ContractReviewRequestRequest;
import showroomz.api.seller.contract.dto.ContractSummaryResponse;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.api.seller.contract.dto.ContractValidationResponse;
import showroomz.api.seller.contract.service.SellerContractCommandService;
import showroomz.api.seller.contract.service.SellerContractQueryService;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractSortType;
import showroomz.domain.contract.type.ContractTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/seller/contracts")
@RequiredArgsConstructor
public class SellerContractController implements SellerContractControllerDocs {

    private final SellerContractQueryService sellerContractQueryService;
    private final SellerContractCommandService sellerContractCommandService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ContractListItem>> getContracts(
            @RequestParam(value = "tab", required = false) ContractTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "sort", required = false) ContractSortType sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(sellerContractQueryService.getContracts(
                getCurrentSellerEmail(), tab, keyword, startDate, endDate, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<ContractSummaryResponse> getSummary() {
        return ResponseEntity.ok(sellerContractQueryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/form-sources")
    public ResponseEntity<ContractFormSourcesResponse> getFormSources() {
        return ResponseEntity.ok(sellerContractQueryService.getFormSources(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/clauses")
    public ResponseEntity<ContractClausesResponse> getClauses() {
        return ResponseEntity.ok(sellerContractQueryService.getClauses(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDetailResponse> getContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(sellerContractQueryService.getContract(getCurrentSellerEmail(), contractId));
    }

    @Override
    @GetMapping("/{contractId}/documents/{type}")
    public ResponseEntity<ContractDocumentDownloadResponse> getDocument(@PathVariable Long contractId,
                                                                        @PathVariable ContractDocumentType type) {
        return ResponseEntity.ok(sellerContractQueryService.getDocument(getCurrentSellerEmail(), contractId, type));
    }

    @Override
    @PostMapping
    public ResponseEntity<ContractCreateResponse> createContract(
            @Valid @RequestBody ContractCreateRequest request) {
        ContractCreateResponse response =
                sellerContractCommandService.create(getCurrentSellerEmail(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PutMapping("/{contractId}")
    public ResponseEntity<ContractDetailResponse> updateContract(@PathVariable Long contractId,
                                                                 @Valid @RequestBody ContractUpdateRequest request) {
        return ResponseEntity.ok(
                sellerContractCommandService.update(getCurrentSellerEmail(), contractId, request));
    }

    @Override
    @DeleteMapping("/{contractId}")
    public ResponseEntity<Void> deleteContract(@PathVariable Long contractId) {
        sellerContractCommandService.delete(getCurrentSellerEmail(), contractId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{contractId}/validate")
    public ResponseEntity<ContractValidationResponse> validateContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(sellerContractCommandService.validate(getCurrentSellerEmail(), contractId));
    }

    @Override
    @PostMapping("/{contractId}/review-request")
    public ResponseEntity<ContractDetailResponse> requestReview(
            @PathVariable Long contractId,
            @RequestBody(required = false) ContractReviewRequestRequest request) {
        // 경고가 하나도 없으면 FE가 바디 없이 부를 수 있다 — 빈 목록과 같은 뜻이다.
        ContractReviewRequestRequest safeRequest =
                request == null ? new ContractReviewRequestRequest(List.of()) : request;
        return ResponseEntity.ok(
                sellerContractCommandService.requestReview(getCurrentSellerEmail(), contractId, safeRequest));
    }

    @Override
    @PostMapping("/{contractId}/review-request/cancel")
    public ResponseEntity<ContractDetailResponse> cancelReviewRequest(@PathVariable Long contractId) {
        return ResponseEntity.ok(
                sellerContractCommandService.cancelReviewRequest(getCurrentSellerEmail(), contractId));
    }

    @Override
    @PostMapping("/{contractId}/cancel")
    public ResponseEntity<ContractDetailResponse> cancelContract(@PathVariable Long contractId,
                                                                 @Valid @RequestBody ContractCancelRequest request) {
        return ResponseEntity.ok(
                sellerContractCommandService.cancelContract(getCurrentSellerEmail(), contractId, request));
    }

    @Override
    @PostMapping("/{contractId}/resend-request")
    public ResponseEntity<ContractResendRequestResponse> requestResend(@PathVariable Long contractId) {
        return ResponseEntity.ok(
                sellerContractCommandService.requestResend(getCurrentSellerEmail(), contractId));
    }

    @Override
    @PostMapping("/{contractId}/fixed-fee/payment")
    public ResponseEntity<ContractDetailResponse> recordFixedFeePayment(@PathVariable Long contractId) {
        return ResponseEntity.ok(
                sellerContractCommandService.recordFixedFeePayment(getCurrentSellerEmail(), contractId));
    }

    @Override
    @PostMapping("/{contractId}/duplicate")
    public ResponseEntity<ContractDuplicateResponse> duplicateContract(@PathVariable Long contractId) {
        ContractDuplicateResponse response =
                sellerContractCommandService.duplicate(getCurrentSellerEmail(), contractId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
