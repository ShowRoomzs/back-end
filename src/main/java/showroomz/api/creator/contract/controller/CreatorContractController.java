package showroomz.api.creator.contract.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.contract.docs.CreatorContractControllerDocs;
import showroomz.api.creator.contract.dto.CreatorContractClausesResponse;
import showroomz.api.creator.contract.dto.CreatorContractDeclineRequest;
import showroomz.api.creator.contract.dto.CreatorContractDetailResponse;
import showroomz.api.creator.contract.dto.CreatorContractDocumentDownloadResponse;
import showroomz.api.creator.contract.dto.CreatorContractListItem;
import showroomz.api.creator.contract.dto.CreatorContractResendRequestResponse;
import showroomz.api.creator.contract.dto.CreatorContractSummaryResponse;
import showroomz.api.creator.contract.service.CreatorContractCommandService;
import showroomz.api.creator.contract.service.CreatorContractQueryService;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.CreatorContractSortType;
import showroomz.domain.contract.type.CreatorContractTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 쇼룸 스튜디오 계약 관리(§27) — 인플루언서가 받은 계약의 조회 · 거절 · 재발송 요청.
 *
 * <p>쓰기가 둘뿐이다. 서명 · 조건 수정 · 계약 작성 · 취소 엔드포인트는 <b>존재하지 않는다</b>.
 */
@RestController
@RequestMapping("/v1/creator/contracts")
@RequiredArgsConstructor
public class CreatorContractController implements CreatorContractControllerDocs {

    private final CreatorContractQueryService creatorContractQueryService;
    private final CreatorContractCommandService creatorContractCommandService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<CreatorContractListItem>> getContracts(
            @RequestParam(value = "tab", required = false) CreatorContractTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) CreatorContractSortType sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(creatorContractQueryService.getContracts(
                getCurrentUserEmail(), tab, keyword, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<CreatorContractSummaryResponse> getSummary() {
        return ResponseEntity.ok(creatorContractQueryService.getSummary(getCurrentUserEmail()));
    }

    @Override
    @GetMapping("/{contractId}")
    public ResponseEntity<CreatorContractDetailResponse> getContract(
            @PathVariable Long contractId,
            @RequestParam(value = "tab", required = false) CreatorContractTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) CreatorContractSortType sort) {
        return ResponseEntity.ok(creatorContractQueryService.getContract(
                getCurrentUserEmail(), contractId, tab, keyword, sort));
    }

    @Override
    @GetMapping("/{contractId}/clauses")
    public ResponseEntity<CreatorContractClausesResponse> getClauses(@PathVariable Long contractId) {
        return ResponseEntity.ok(creatorContractQueryService.getClauses(getCurrentUserEmail(), contractId));
    }

    @Override
    @GetMapping("/{contractId}/documents/{type}")
    public ResponseEntity<CreatorContractDocumentDownloadResponse> getDocument(
            @PathVariable Long contractId,
            @PathVariable ContractDocumentType type) {
        return ResponseEntity.ok(
                creatorContractQueryService.getDocument(getCurrentUserEmail(), contractId, type));
    }

    @Override
    @PostMapping("/{contractId}/decline")
    public ResponseEntity<CreatorContractDetailResponse> decline(
            @PathVariable Long contractId,
            @Valid @RequestBody CreatorContractDeclineRequest request) {
        return ResponseEntity.ok(
                creatorContractCommandService.decline(getCurrentUserEmail(), contractId, request));
    }

    @Override
    @PostMapping("/{contractId}/resend-request")
    public ResponseEntity<CreatorContractResendRequestResponse> requestResend(@PathVariable Long contractId) {
        return ResponseEntity.ok(
                creatorContractCommandService.requestResend(getCurrentUserEmail(), contractId));
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
