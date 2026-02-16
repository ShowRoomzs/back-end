package showroomz.api.admin.faq.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.faq.docs.AdminFaqControllerDocs;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.service.AdminFaqService;

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
}

