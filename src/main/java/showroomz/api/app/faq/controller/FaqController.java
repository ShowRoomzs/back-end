package showroomz.api.app.faq.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.docs.FaqControllerDocs;
import showroomz.api.app.faq.dto.FaqCategoryItem;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.api.app.faq.service.FaqService;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;

@RestController
@RequestMapping("/v1/common/faqs")
@RequiredArgsConstructor
public class FaqController implements FaqControllerDocs {

    private final FaqService faqService;

    @Override
    @GetMapping
    public ResponseEntity<List<FaqResponse>> getFaqList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category) {
        FaqCategory categoryEnum = FaqCategory.fromRequestParam(category);
        List<FaqResponse> list = faqService.getFaqList(keyword, categoryEnum);
        return ResponseEntity.ok(list);
    }

    @Override
    @GetMapping("/categories")
    public ResponseEntity<List<FaqCategoryItem>> getFaqCategories() {
        return ResponseEntity.ok(faqService.getFaqCategories());
    }
}

