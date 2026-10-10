package showroomz.api.creator.settlement.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.settlement.docs.CreatorSettlementControllerDocs;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.api.creator.settlement.service.CreatorSettlementCommandService;
import showroomz.api.creator.settlement.service.CreatorSettlementDocumentService;
import showroomz.api.creator.settlement.service.CreatorSettlementQueryService;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/v1/creator/settlements")
@RequiredArgsConstructor
public class CreatorSettlementController implements CreatorSettlementControllerDocs {

    private final CreatorSettlementQueryService queryService;
    private final CreatorSettlementCommandService commandService;
    private final CreatorSettlementDocumentService documentService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<CreatorSettlementDto.ListItem>> getSettlements(
            @RequestParam(value = "status", required = false) Set<SettlementStatus> status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) SettlementPartySort sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getSettlements(getCurrentUserEmail(), status, keyword, sort,
                pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<CreatorSettlementDto.Summary> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentUserEmail()));
    }

    @Override
    @GetMapping("/{settlementId}")
    public ResponseEntity<CreatorSettlementDto.Detail> getSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(queryService.getDetail(getCurrentUserEmail(), settlementId));
    }

    @Override
    @GetMapping("/{settlementId}/items")
    public ResponseEntity<PageResponse<CreatorSettlementDto.Item>> getItems(@PathVariable Long settlementId,
                                                                           @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getItems(getCurrentUserEmail(), settlementId, pagingRequest));
    }

    @Override
    @GetMapping("/{settlementId}/items/download")
    public ResponseEntity<byte[]> downloadItems(@PathVariable Long settlementId) {
        return xlsx(queryService.downloadItems(getCurrentUserEmail(), settlementId));
    }

    @Override
    @PostMapping(value = "/{settlementId}/tax-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatorSettlementDto.TaxInvoiceSubmitResponse> submitTaxInvoice(
            @PathVariable Long settlementId,
            @RequestParam(value = "approvalNumber", required = false) String approvalNumber,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment) {
        return ResponseEntity.ok(commandService.submitTaxInvoice(getCurrentUserEmail(), settlementId, approvalNumber,
                attachment));
    }

    @Override
    @GetMapping("/{settlementId}/withholding-receipt")
    public ResponseEntity<byte[]> downloadWithholdingReceipt(@PathVariable Long settlementId) {
        return documentService.withholdingReceipt(getCurrentUserEmail(), settlementId).toResponse();
    }

    @Override
    @GetMapping("/{settlementId}/tax-invoice/attachment")
    public ResponseEntity<byte[]> downloadTaxInvoiceAttachment(@PathVariable Long settlementId) {
        return documentService.taxInvoiceAttachment(getCurrentUserEmail(), settlementId).toResponse();
    }

    @Override
    @GetMapping("/annual-statement")
    public ResponseEntity<byte[]> downloadAnnualStatement(@RequestParam(value = "year", required = false) Integer year) {
        return documentService.annualStatement(getCurrentUserEmail(), year).toResponse();
    }

    static ResponseEntity<byte[]> xlsx(SettlementStatementExcel.File file) {
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(SettlementStatementExcel.CONTENT_TYPE))
                .body(file.content());
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
