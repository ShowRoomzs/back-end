package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;

import java.util.List;

@Tag(name = "User - Inquiry (1:1 문의)", description = "1:1 문의 관련 API")
public interface CommonInquiryControllerDocs {

    @Operation(
            summary = "1:1 문의 카테고리 목록 조회",
            description = "문의 등록 시 선택할 수 있는 대분류(InquiryType)와 소분류(InquiryDetailType) 목록을 조회합니다.\n\n" +
                    "**권한:** 인증 불필요 (비로그인 허용)\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<List<InquiryCategoryResponse>> getInquiryCategories();
}
