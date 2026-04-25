package showroomz.api.common.faq.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.faq.dto.FaqCategoryItem;
import showroomz.api.app.faq.service.FaqService;
import showroomz.api.common.faq.docs.CommonFaqControllerDocs;

import java.util.List;

@RestController
@RequestMapping("/v1/common/faqs")
@RequiredArgsConstructor
public class CommonFaqController implements CommonFaqControllerDocs {

    private final FaqService faqService;

    @Override
    @GetMapping("/categories")
    public ResponseEntity<List<FaqCategoryItem>> getFaqCategories() {
        return ResponseEntity.ok(faqService.getFaqCategories());
    }
}
