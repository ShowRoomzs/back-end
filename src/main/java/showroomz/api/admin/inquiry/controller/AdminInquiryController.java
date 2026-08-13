package showroomz.api.admin.inquiry.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import showroomz.api.admin.inquiry.docs.AdminInquiryControllerDocs;
import showroomz.api.admin.inquiry.dto.AdminInquiryDto;
import showroomz.api.admin.inquiry.service.AdminInquiryService;
import showroomz.api.admin.inquiry.type.AdminInquiryStatusFilter;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.cs.type.CsCategory;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/inquiries")
@RequiredArgsConstructor
@Hidden
public class AdminInquiryController implements AdminInquiryControllerDocs {

    private final AdminInquiryService adminInquiryService;

    @Override
    @GetMapping
    public ResponseEntity<AdminInquiryDto.ListResponse> getList(
            @RequestParam(value = "status", defaultValue = "ALL") AdminInquiryStatusFilter status,
            @RequestParam(value = "type", required = false) CsCategory type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        return ResponseEntity.ok(adminInquiryService.getList(status, type, keyword, pageable));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminInquiryDto.SummaryResponse> getSummary() {
        return ResponseEntity.ok(adminInquiryService.getSummary());
    }

    @Override
    @GetMapping("/types")
    public ResponseEntity<List<AdminInquiryDto.TypeOption>> getTypes() {
        return ResponseEntity.ok(adminInquiryService.getTypeOptions());
    }

    @Override
    @GetMapping("/{inquiryId}")
    public ResponseEntity<AdminInquiryDto.DetailResponse> getDetail(
            @PathVariable("inquiryId") Long inquiryId,
            @RequestParam(value = "status", defaultValue = "ALL") AdminInquiryStatusFilter status,
            @RequestParam(value = "type", required = false) CsCategory type,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(adminInquiryService.getDetail(inquiryId, status, type, keyword));
    }

    @Override
    @PostMapping("/{inquiryId}/answer")
    public ResponseEntity<AdminInquiryDto.AnswerResponse> registerAnswer(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminInquiryDto.AnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminInquiryService.registerAnswer(inquiryId, requireOperatorId(principal), request));
    }

    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
