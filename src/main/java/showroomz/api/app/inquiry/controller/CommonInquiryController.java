package showroomz.api.app.inquiry.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.docs.CommonInquiryControllerDocs;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.service.InquiryService;

import java.util.List;

@RestController
@RequestMapping("/v1/common/inquiries")
@RequiredArgsConstructor
public class CommonInquiryController implements CommonInquiryControllerDocs {

    private final InquiryService inquiryService;

    @Override
    @GetMapping("/categories")
    public ResponseEntity<List<InquiryCategoryResponse>> getInquiryCategories() {
        return ResponseEntity.ok(inquiryService.getInquiryCategories());
    }
}
