package showroomz.api.app.inquiry.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.docs.InquiryControllerDocs;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.InquiryRegisterResponse;
import showroomz.api.app.inquiry.dto.InquiryUpdateRequest;
import showroomz.api.app.inquiry.service.InquiryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/inquiries")
@RequiredArgsConstructor
@Hidden
public class InquiryController implements InquiryControllerDocs {

    private final InquiryService inquiryService;

    @Override
    @PostMapping
    public ResponseEntity<InquiryRegisterResponse> registerInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody InquiryRegisterRequest request) {
        InquiryRegisterResponse response = inquiryService.registerInquiry(userPrincipal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
            @PathVariable("inquiryId") Long inquiryId) {
        return inquiryService.getInquiryDetail(userPrincipal.getUserId(), inquiryId);
    }

    @Override
    @PatchMapping("/{inquiryId}")
    public ResponseEntity<Void> updateInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody InquiryUpdateRequest request) {
        inquiryService.updateInquiry(userPrincipal.getUserId(), inquiryId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<Void> deleteInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId) {
        inquiryService.deleteInquiry(userPrincipal.getUserId(), inquiryId);
        return ResponseEntity.noContent().build();
    }
}
