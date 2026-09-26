package showroomz.api.admin.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.api.admin.contract.service.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.contract.type.*;
import showroomz.global.dto.*;
import showroomz.global.error.exception.*;

@RestController
@RequestMapping("/v1/admin/contracts")
@Tag(name = "Admin - Contract", description = "관리자 계약 관리 API — 모두싸인에서 처리한 절차 기록 · 계약 조건은 읽기 전용")
@RequiredArgsConstructor
public class AdminContractController {
    private final AdminContractQueryService queries;
    private final AdminContractCommandService commands;
    private final AdminContractDocumentService documents;
    private final ContractDraftService drafts;

    @GetMapping
    @Operation(summary = "계약 목록 및 조치 큐", description = "작성중 제외. queue 선택 시 tab·sort보다 우선합니다. 페이지 크기 20 또는 50.")
    public PageResponse<ListItem> list(@RequestParam(defaultValue = "ALL") AdminContractTab tab,
            @RequestParam(required = false) AdminContractQueue queue, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "REVIEW_REQUESTED_ASC") AdminContractSort sort, @ModelAttribute PagingRequest paging) {
        if (paging.getSize() != 20 && paging.getSize() != 50) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return queries.list(tab, queue, keyword, sort, paging.toPageable(Sort.unsorted()));
    }
    @GetMapping("/summary")
    @Operation(summary = "검색과 무관한 큐·탭 카운트")
    public Summary summary() { return queries.summary(); }
    @GetMapping("/{id}")
    @Operation(summary = "계약 상세, 권한 및 전체 이력")
    public Detail detail(@PathVariable Long id) { return queries.detail(id); }
    @PostMapping("/{id}/review/approve")
    @Operation(summary = "검토 승인 및 외부 발송 시각 기록")
    public ProcessResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.approve(id, operator(principal), request);
    }
    @PostMapping("/{id}/review/reject")
    @Operation(summary = "검토 반려")
    public ProcessResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.reject(id, operator(principal), request);
    }
    @PutMapping("/{id}/signatures")
    @Operation(summary = "서명 현황 갱신", description = "version 필수. null로 서명 해제 가능. 기준 시각은 서버에서 기록합니다.")
    public ProcessResponse signatures(@PathVariable Long id, @Valid @RequestBody SignatureRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.updateSignatures(id, operator(principal), request);
    }
    @PostMapping("/{id}/conclude")
    @Operation(summary = "체결 완료", description = "서명 2종·체결 문서 2종 필수. 체결과 같은 트랜잭션에서 공구가 생성되고 응답의 groupBuyNumber로 돌려준다 — 공구 생성에 실패하면 체결도 롤백된다.")
    public ProcessResponse conclude(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { return commands.conclude(id, operator(principal)); }
    @PostMapping("/{id}/expire")
    @Operation(summary = "대시보드 재확인 후 수동 만료")
    public ProcessResponse expire(@PathVariable Long id, @Valid @RequestBody ExpireRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.expire(id, operator(principal), request);
    }
    @PostMapping("/{id}/cancel")
    @Operation(summary = "계약 취소", description = "서명 요청 발송 이후 체결 전(SIGNING·CONCLUSION_PENDING)만. "
            + "이 구간은 브랜드가 취소할 수 없다. 모두싸인 서명 요청 회수 확인(signatureRequestWithdrawn) 필수 · "
            + "사유 5종 · ETC면 메모 필수. 양측에 통지합니다.")
    public ProcessResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.cancel(id, operator(principal), request);
    }
    @PostMapping("/{id}/resend/handle")
    @Operation(summary = "외부 재발송 완료 기록", description = "미처리 요청 전부 처리. 발송 시각·서명 기한은 변경하지 않습니다.")
    public ProcessResponse resend(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { return commands.handleResend(id, operator(principal)); }
    @PostMapping("/{id}/documents/presign")
    @Operation(summary = "체결 문서 업로드 URL", description = "Content-Type: application/pdf로 PUT. 유효기간 15분.")
    public PresignResponse presign(@PathVariable Long id, @Valid @RequestBody PresignRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return documents.presign(id, operator(principal), request);
    }
    @PostMapping("/{id}/documents")
    @Operation(summary = "업로드 완료 등록 또는 교체")
    public DownloadResponse register(@PathVariable Long id, @Valid @RequestBody RegisterDocumentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return documents.register(id, operator(principal), request);
    }
    @DeleteMapping("/{id}/documents/{type}")
    @Operation(summary = "체결 전 문서 삭제")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable ContractDocumentType type, @AuthenticationPrincipal UserPrincipal principal) {
        documents.delete(id, operator(principal), type);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/documents/{type}")
    @Operation(summary = "문서 다운로드 URL", description = "유효기간 5분.")
    public DownloadResponse download(@PathVariable Long id, @PathVariable ContractDocumentType type) { return documents.download(id, type); }
    @GetMapping("/{id}/document-draft")
    @Operation(summary = "계약서 PDF 생성본 다운로드", description = "제출본 시각 기준 캐시. 재요청 시 재생성. 미확정 문안은 초안 표시를 유지합니다.")
    public DownloadResponse draft(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { return drafts.download(id, operator(principal)); }
    private Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        return principal.getUserId();
    }
}
