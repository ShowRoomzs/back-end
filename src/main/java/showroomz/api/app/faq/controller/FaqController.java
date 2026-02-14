package showroomz.api.app.faq.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.docs.FaqControllerDocs;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.api.app.faq.service.FaqService;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

@RestController
@RequestMapping("/v1/common/faqs")
@RequiredArgsConstructor
@Hidden
public class FaqController implements FaqControllerDocs {

    private final FaqService faqService;

    @Override
    @GetMapping
    public ResponseEntity<List<FaqResponse>> getFaqList(
            @RequestParam(value = "type", required = false) InquiryType type) {

        return ResponseEntity.ok(faqService.getFaqList(type));
    }
}

