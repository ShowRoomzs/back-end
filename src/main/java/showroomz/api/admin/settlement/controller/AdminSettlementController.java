package showroomz.api.admin.settlement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.settlement.docs.AdminSettlementControllerDocs;
import showroomz.api.admin.settlement.dto.AdminSettlementDto;
import showroomz.api.admin.settlement.service.AdminSettlementCommandService;
import showroomz.api.admin.settlement.service.AdminSettlementQueryService;
import showroomz.api.admin.settlement.service.AdminWithholdingReportService;
import showroomz.api.admin.settlement.type.AdminSettlementSort;
import showroomz.api.admin.settlement.type.AdminSettlementTab;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/v1/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController implements AdminSettlementControllerDocs {

    private final AdminSettlementQueryService queries;
    private final AdminSettlementCommandService commands;
    private final AdminWithholdingReportService withholdingReports;

    @Override
    @GetMapping
    public AdminSettlementDto.ListResponse<?> list(@RequestParam(required = false) AdminSettlementTab tab,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) AdminSettlementSort sort,
                                                   @ModelAttribute PagingRequest paging) {
        return queries.list(tab, keyword, sort, paging);
    }

    @Override
    @GetMapping("/summary")
    public AdminSettlementDto.Summary summary() {
        return queries.summary();
    }

    @Override
    @GetMapping("/withholding-report")
    public ResponseEntity<byte[]> withholdingReport(@RequestParam(required = false) YearMonth month,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        if (month == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "month(YYYY-MM)가 필요합니다.");
        }
        return withholdingReports.download(month, operator(principal)).toResponse();
    }

    @Override
    @GetMapping("/by-thread/{threadId}")
    public AdminSettlementDto.ByThread byThread(@PathVariable Long threadId) {
        return queries.byThread(threadId);
    }

    @Override
    @GetMapping("/{settlementId}")
    public AdminSettlementDto.Detail detail(@PathVariable Long settlementId) {
        return queries.detail(settlementId);
    }

    @Override
    @GetMapping("/{settlementId}/items")
    public PageResponse<AdminSettlementDto.Item> items(@PathVariable Long settlementId,
                                                       @ModelAttribute PagingRequest paging) {
        return queries.items(settlementId, paging);
    }

    @Override
    @GetMapping("/{settlementId}/statement.xlsx")
    public ResponseEntity<byte[]> statement(@PathVariable Long settlementId) {
        SettlementStatementExcel.File file = queries.statement(settlementId);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(SettlementStatementExcel.CONTENT_TYPE))
                .body(file.content());
    }

    @Override
    @PostMapping("/{settlementId}/payouts/{payoutId}/redistribute")
    public AdminSettlementDto.Detail redistribute(@PathVariable Long settlementId, @PathVariable Long payoutId,
                                                  @Valid @RequestBody AdminSettlementDto.RedistributeRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        commands.redistribute(settlementId, payoutId, request.accountSource(), operator(principal));
        return queries.detail(settlementId);
    }

    @Override
    @PostMapping("/{settlementId}/tax-documents/{documentId}/verify")
    public AdminSettlementDto.Detail verifyTaxInvoice(@PathVariable Long settlementId, @PathVariable Long documentId,
                                                      @Valid @RequestBody AdminSettlementDto.VerifyRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        commands.verifyTaxInvoice(settlementId, documentId, request.result(), operator(principal));
        return queries.detail(settlementId);
    }

    @Override
    @PostMapping(value = "/{settlementId}/tax-documents/{documentId}/issue",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminSettlementDto.Detail issueBrandInvoice(@PathVariable Long settlementId, @PathVariable Long documentId,
                                                       @RequestPart(value = "file", required = false) MultipartFile file,
                                                       @RequestParam(value = "approvalNumber", required = false)
                                                       String approvalNumber,
                                                       @RequestParam(value = "issuedDate", required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDate,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        commands.issueBrandInvoice(settlementId, documentId, file, approvalNumber, issuedDate, operator(principal));
        return queries.detail(settlementId);
    }

    private Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
