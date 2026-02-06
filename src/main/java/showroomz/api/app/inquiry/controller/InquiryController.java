package showroomz.api.app.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.docs.InquiryControllerDocs;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.service.InquiryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/inquiries")
@RequiredArgsConstructor
public class InquiryController implements InquiryControllerDocs {

    private final InquiryService inquiryService;

    @Override
    @PostMapping
    public Long registerInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody InquiryRegisterRequest request) {
        return inquiryService.registerInquiry(userPrincipal.getUserId(), request);
    }

    @Override
    @GetMapping
    public PageResponse<InquiryListResponse> getMyInquiries(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid PagingRequest pagingRequest) {
        return inquiryService.getMyInquiries(userPrincipal.getUserId(), pagingRequest.toPageable());
    }

    @Override
    @GetMapping("/{inquiryId}")
    public InquiryDetailResponse getInquiryDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long inquiryId) {
        return inquiryService.getInquiryDetail(userPrincipal.getUserId(), inquiryId);
    }
}
