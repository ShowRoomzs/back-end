package showroomz.api.admin.faq.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.faq.docs.AdminFaqControllerDocs;
import showroomz.api.admin.faq.dto.AdminFaqListRequest;
import showroomz.api.admin.faq.dto.AdminFaqListResponse;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.dto.AdminFaqUpdateRequest;
import showroomz.api.admin.faq.dto.FaqReorderRequest;
import showroomz.api.admin.faq.service.AdminFaqService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/v1/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController implements AdminFaqControllerDocs {

    private final AdminFaqService adminFaqService;

    @Override
    @PostMapping
    public ResponseEntity<Void> registerFaq(@Valid @RequestBody AdminFaqRegisterRequest request) {
        Long faqId = adminFaqService.registerFaq(request);
        URI location = Objects.requireNonNull(URI.create("/v1/admin/faqs/" + faqId));
        return ResponseEntity.created(location).build();
    }

    @Override
    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderFaqs(@Valid @RequestBody FaqReorderRequest request) {
        adminFaqService.reorderFaqs(request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{faqId}")
    public ResponseEntity<AdminFaqListResponse> getFaq(@PathVariable("faqId") Long faqId) {
        AdminFaqListResponse response = adminFaqService.getFaq(faqId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminFaqListResponse>> getFaqs(
            @ModelAttribute AdminFaqListRequest request,
            @ModelAttribute PagingRequest pagingRequest) {
        PageResponse<AdminFaqListResponse> response = adminFaqService.getFaqs(request, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{faqId}")
    public ResponseEntity<Void> updateFaq(
            @PathVariable("faqId") Long faqId,
            @Valid @RequestBody AdminFaqUpdateRequest request) {
        adminFaqService.updateFaq(faqId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{faqId}")
    public ResponseEntity<Void> deleteFaq(@PathVariable("faqId") Long faqId) {
        adminFaqService.deleteFaq(faqId);
        return ResponseEntity.noContent().build();
    }
}

