package showroomz.api.app.inquiry.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.inquiry.docs.CommonProductInquiryControllerDocs;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.service.ProductInquiryService;

import java.util.List;

@RestController
@RequestMapping("/v1/common/product-inquiries")
@RequiredArgsConstructor
public class CommonProductInquiryController implements CommonProductInquiryControllerDocs {

    private final ProductInquiryService productInquiryService;

    @Override
    @GetMapping("/categories")
    public ResponseEntity<List<InquiryCategoryResponse>> getProductInquiryCategories() {
        return ResponseEntity.ok(productInquiryService.getProductInquiryCategories());
    }
}
