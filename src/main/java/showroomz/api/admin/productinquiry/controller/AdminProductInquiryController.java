package showroomz.api.admin.productinquiry.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDeleteDecision;
import showroomz.api.admin.productinquiry.service.AdminProductInquiryService;

@Tag(name = "Admin - Product Inquiry", description = "상품 문의 삭제 요청 판단 API (§23-5)")
@RestController
@RequestMapping("/v1/admin/product-inquiries")
@RequiredArgsConstructor
public class AdminProductInquiryController {

    private final AdminProductInquiryService adminProductInquiryService;

    @Operation(
            summary = "문의 삭제 집행",
            description = "브랜드의 삭제 요청을 받아들여 문의를 삭제합니다.\n\n" +
                    "질문과 브랜드 답변이 **함께** 소비자 화면에서 내려갑니다 — 질문 없는 답변은 성립하지 않기 때문입니다. " +
                    "원문·답변은 보관되며 파트너센터·어드민 상세에서만 조회됩니다.\n\n" +
                    "`reason`은 운영자 내부 기록이라 브랜드·작성자에게 공개되지 않습니다."
    )
    @PostMapping("/{inquiryId}/delete-request/execute")
    public ResponseEntity<Void> executeDelete(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminProductInquiryDeleteDecision.ExecuteRequest request) {
        adminProductInquiryService.executeDelete(inquiryId, request.getReason());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "문의 삭제 요청 반려",
            description = "브랜드의 삭제 요청만 기각하고 문의는 그대로 게시합니다.\n\n" +
                    "노출 축만 정상으로 돌아가므로 상태는 **요청 직전 상태**(답변대기/답변완료)로 복귀합니다. " +
                    "별도 상태값을 만들지 않는 대신 처리 이력에 「삭제 요청 반려 · 결과 알림 수신」으로 남습니다.\n\n" +
                    "`reason`(반려 사유)은 요청한 브랜드에게 전달됩니다. 브랜드는 사유를 보완해 재요청할 수 있습니다."
    )
    @PostMapping("/{inquiryId}/delete-request/reject")
    public ResponseEntity<Void> rejectDeleteRequest(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminProductInquiryDeleteDecision.RejectRequest request) {
        adminProductInquiryService.rejectDeleteRequest(inquiryId, request.getReason());
        return ResponseEntity.noContent().build();
    }
}
