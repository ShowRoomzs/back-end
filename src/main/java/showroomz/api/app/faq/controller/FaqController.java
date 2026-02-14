package showroomz.api.app.faq.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.docs.FaqControllerDocs;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.api.app.faq.service.FaqService;

import java.util.List;

@RestController
@RequestMapping("/v1/common/faqs")
@RequiredArgsConstructor
public class FaqController implements FaqControllerDocs {

    private final FaqService faqService;

    @Override
    @GetMapping
    public ResponseEntity<List<FaqResponse>> getFaqList() {
        return ResponseEntity.ok(faqService.getFaqList());
    }

    @Override
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getFaqCategories() {
        return ResponseEntity.ok(faqService.getFaqCategories());
    }
}

