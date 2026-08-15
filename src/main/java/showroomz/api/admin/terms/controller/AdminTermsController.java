package showroomz.api.admin.terms.controller;

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
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.terms.docs.AdminTermsControllerDocs;
import showroomz.api.admin.terms.dto.AdminTermsDocumentDetailResponse;
import showroomz.api.admin.terms.dto.AdminTermsDocumentRegisterRequest;
import showroomz.api.admin.terms.dto.AdminTermsListRequest;
import showroomz.api.admin.terms.dto.AdminTermsPageResponse;
import showroomz.api.admin.terms.dto.AdminTermsVersionDetailResponse;
import showroomz.api.admin.terms.dto.AdminTermsVersionRegisterRequest;
import showroomz.api.admin.terms.service.AdminTermsService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URI;

/**
 * 약관·정책 관리 (기획 §21) — 수정·삭제 엔드포인트가 없다.
 *
 * <p>등록된 원문은 어떤 경로로도 바뀌지 않는다. 개정은 새 버전 등록뿐이며 과거 버전은 조회만 가능하다.
 */
@RestController
@RequestMapping("/v1/admin/terms")
@RequiredArgsConstructor
public class AdminTermsController implements AdminTermsControllerDocs {

    private final AdminTermsService adminTermsService;

    @Override
    @PostMapping
    public ResponseEntity<Void> registerDocument(
            @Valid @RequestBody AdminTermsDocumentRegisterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long documentId = adminTermsService.registerDocument(request, requireOperatorId(principal));
        return ResponseEntity.created(URI.create("/v1/admin/terms/" + documentId)).build();
    }

    @Override
    @GetMapping
    public ResponseEntity<AdminTermsPageResponse> getDocuments(
            @ModelAttribute AdminTermsListRequest request,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(adminTermsService.getDocuments(request, pagingRequest));
    }

    @Override
    @GetMapping("/{documentId}")
    public ResponseEntity<AdminTermsDocumentDetailResponse> getDocument(
            @PathVariable("documentId") Long documentId) {
        return ResponseEntity.ok(adminTermsService.getDocument(documentId));
    }

    @Override
    @PostMapping("/{documentId}/versions")
    public ResponseEntity<Void> registerVersion(
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody AdminTermsVersionRegisterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long versionId = adminTermsService.registerVersion(documentId, request, requireOperatorId(principal));
        return ResponseEntity
                .created(URI.create("/v1/admin/terms/" + documentId + "/versions/" + versionId))
                .build();
    }

    @Override
    @GetMapping("/{documentId}/versions/{versionId}")
    public ResponseEntity<AdminTermsVersionDetailResponse> getVersion(
            @PathVariable("documentId") Long documentId,
            @PathVariable("versionId") Long versionId) {
        return ResponseEntity.ok(adminTermsService.getVersion(documentId, versionId));
    }

    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
