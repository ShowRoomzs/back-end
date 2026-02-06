package showroomz.api.app.inquiry.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.service.InquiryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Inquiry (1:1 문의)", description = "1:1 문의 관련 API")
@RestController
@RequestMapping("/v1/user/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "1:1 문의 등록", description = "문의 타입(Enum)과 상세 유형(String), 내용을 입력하여 문의를 등록합니다.")
    @PostMapping
    public Long registerInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody InquiryRegisterRequest request) {
        return inquiryService.registerInquiry(userPrincipal.getUserId(), request);
    }

    @Operation(summary = "내 문의 내역 조회", description = "내가 등록한 1:1 문의 내역을 최신순으로 페이징 조회합니다.")
    @GetMapping
    public PageResponse<InquiryListResponse> getMyInquiries(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid PagingRequest pagingRequest) {
        return inquiryService.getMyInquiries(userPrincipal.getUserId(), pagingRequest.toPageable());
    }

    @Operation(summary = "문의 상세 조회", description = "특정 문의의 상세 내용과 답변을 조회합니다.")
    @GetMapping("/{inquiryId}")
    public InquiryDetailResponse getInquiryDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long inquiryId) {
        return inquiryService.getInquiryDetail(userPrincipal.getUserId(), inquiryId);
    }
}
