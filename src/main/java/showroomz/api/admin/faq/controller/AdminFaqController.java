package showroomz.api.admin.faq.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.service.AdminFaqService;

import java.net.URI;
import java.util.Objects;

@Tag(name = "Admin FAQ", description = "관리자 FAQ 관리 API")
@RestController
@RequestMapping("/v1/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private final AdminFaqService adminFaqService;

    @Operation(summary = "FAQ 등록", description = "관리자가 새로운 FAQ를 등록합니다.")
    @PostMapping
    public ResponseEntity<Void> registerFaq(@Valid @RequestBody AdminFaqRegisterRequest request) {
        Long faqId = adminFaqService.registerFaq(request);
        URI location = Objects.requireNonNull(URI.create("/v1/admin/faqs/" + faqId));
        return ResponseEntity.created(location).build();
    }
}

