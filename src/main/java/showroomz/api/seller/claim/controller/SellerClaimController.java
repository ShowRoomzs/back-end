package showroomz.api.seller.claim.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.claim.docs.SellerClaimControllerDocs;
import showroomz.api.seller.claim.dto.SellerClaimBatchResponse;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimReceiveRequest;
import showroomz.api.seller.claim.dto.SellerClaimRejectRequest;
import showroomz.api.seller.claim.dto.SellerClaimReshipDto;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.api.seller.claim.service.SellerClaimCommandService;
import showroomz.api.seller.claim.service.SellerClaimQueryService;
import showroomz.api.seller.claim.service.SellerClaimReshipService;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/v1/seller/claims")
@RequiredArgsConstructor
public class SellerClaimController implements SellerClaimControllerDocs {

    private final SellerClaimQueryService queryService;
    private final SellerClaimCommandService commandService;
    private final SellerClaimReshipService reshipService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<SellerClaimListItem>> getClaims(
            @RequestParam(value = "tab", required = false) ClaimTab tab,
            @RequestParam(value = "types", required = false) Set<ClaimType> types,
            @RequestParam(value = "reason", required = false) ClaimReason reason,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getClaims(getCurrentSellerEmail(), tab, types, reason, from, to, keyword,
                pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<SellerClaimSummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/{claimId}")
    public ResponseEntity<SellerClaimDetailResponse> getClaim(@PathVariable Long claimId) {
        return ResponseEntity.ok(queryService.getClaim(getCurrentSellerEmail(), claimId));
    }

    @Override
    @PostMapping("/receive")
    public ResponseEntity<SellerClaimBatchResponse> receive(@Valid @RequestBody SellerClaimReceiveRequest request) {
        return ResponseEntity.ok(commandService.receive(getCurrentSellerEmail(), request));
    }

    @Override
    @PostMapping("/{claimId}/inspection/pass")
    public ResponseEntity<SellerClaimDetailResponse> passInspection(@PathVariable Long claimId) {
        return ResponseEntity.ok(commandService.pass(getCurrentSellerEmail(), claimId));
    }

    @Override
    @PostMapping("/{claimId}/inspection/reject")
    public ResponseEntity<SellerClaimDetailResponse> rejectInspection(
            @PathVariable Long claimId, @RequestBody SellerClaimRejectRequest request) {
        return ResponseEntity.ok(commandService.reject(getCurrentSellerEmail(), claimId, request));
    }

    // ── 재발송 ──────────────────────────────────────────────────────────────

    @Override
    @PostMapping("/reshipments")
    public ResponseEntity<SellerClaimBatchResponse> registerReshipments(
            @Valid @RequestBody SellerClaimReshipDto.RegisterRequest request) {
        return ResponseEntity.ok(reshipService.register(getCurrentSellerEmail(), request));
    }

    @Override
    @PatchMapping("/{claimId}/reshipment")
    public ResponseEntity<SellerClaimDetailResponse> updateReshipment(
            @PathVariable Long claimId, @Valid @RequestBody SellerClaimReshipDto.UpdateRequest request) {
        return ResponseEntity.ok(reshipService.update(getCurrentSellerEmail(), claimId, request));
    }

    @Override
    @PostMapping("/reshipments/export")
    public ResponseEntity<byte[]> exportReshipments(@Valid @RequestBody SellerClaimReshipDto.ExportRequest request) {
        SellerClaimReshipService.ExportFile file = reshipService.export(getCurrentSellerEmail(), request);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @Override
    @GetMapping("/reshipments/export/template")
    public ResponseEntity<SellerClaimReshipDto.TemplateResponse> getReshipTemplate() {
        return ResponseEntity.ok(reshipService.getTemplate(getCurrentSellerEmail()));
    }

    @Override
    @PutMapping("/reshipments/export/template")
    public ResponseEntity<SellerClaimReshipDto.TemplateResponse> updateReshipTemplate(
            @Valid @RequestBody SellerClaimReshipDto.TemplateUpdateRequest request) {
        return ResponseEntity.ok(reshipService.updateTemplate(getCurrentSellerEmail(), request));
    }

    @Override
    @PostMapping(value = "/reshipments/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SellerClaimReshipDto.ParseResponse> parseReshipments(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(reshipService.parse(getCurrentSellerEmail(), file));
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
