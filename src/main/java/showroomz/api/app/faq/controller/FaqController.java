package showroomz.api.app.faq.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.api.app.faq.service.FaqService;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

@Tag(name = "Common - FAQ", description = "자주 묻는 질문 관련 API")
@RestController
@RequestMapping("/v1/common/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @Operation(summary = "FAQ 목록 조회", description = "자주 묻는 질문 목록을 조회합니다. 타입을 지정하지 않으면 전체 목록이 반환됩니다.")
    @GetMapping
    public ResponseEntity<List<FaqResponse>> getFaqList(
            @Parameter(description = "질문 타입 (DELIVERY, ORDER_PAYMENT 등)")
            @RequestParam(value = "type", required = false) InquiryType type) {

        return ResponseEntity.ok(faqService.getFaqList(type));
    }
}

