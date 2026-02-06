package showroomz.api.app.inquiry.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.service.InquiryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Tag(name = "User - Inquiry (1:1 문의)", description = "1:1 문의 관련 API")
@RestController
@RequestMapping("/v1/user/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "1:1 문의 등록", description = "문의 유형, 제목, 내용, 이미지(URL)를 입력하여 문의를 등록합니다.")
    @PostMapping
    public ResponseEntity<Long> registerInquiry(
            @Valid @RequestBody InquiryRegisterRequest request) {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        Long inquiryId = inquiryService.registerInquiry(userPrincipal.getUserId(), request);
        return ResponseEntity.ok(inquiryId);
    }

    @Operation(summary = "내 문의 내역 조회", description = "내가 등록한 1:1 문의 내역을 페이징하여 조회합니다.")
    @GetMapping
    public ResponseEntity<PageResponse<InquiryListResponse>> getMyInquiries(
            @Valid PagingRequest pagingRequest) {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        return ResponseEntity.ok(
                inquiryService.getMyInquiries(userPrincipal.getUserId(), pagingRequest.toPageable())
        );
    }

    @Operation(summary = "문의 상세 조회", description = "특정 문의의 상세 내용과 답변을 조회합니다.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<InquiryDetailResponse> getInquiryDetail(
            @PathVariable Long inquiryId) {
        UserPrincipal userPrincipal = getAuthenticatedUser();
        return ResponseEntity.ok(
                inquiryService.getInquiryDetail(userPrincipal.getUserId(), inquiryId)
        );
    }

    private UserPrincipal getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return (UserPrincipal) principal;
    }
}
