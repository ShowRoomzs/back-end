package showroomz.api.app.inquiry.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.dto.PublicProductInquiryResponse;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Common - Product Inquiry", description = "공용 상품 문의 조회 API")
public interface CommonProductInquiryControllerDocs {

    @Operation(
            summary = "상품 문의 타입 목록 조회",
            description = "상품 문의 등록 시 선택할 수 있는 문의 타입 목록을 조회합니다. \n\n" +
                    "**권한:** 인증 불필요 (비로그인 허용)\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InquiryCategoryResponse.class)),
                            examples = @ExampleObject(value = "[\n" +
                                    "  { \"key\": \"OPTION\", \"description\": \"옵션\" },\n" +
                                    "  { \"key\": \"INGREDIENT_USAGE\", \"description\": \"성분·사용법\" },\n" +
                                    "  { \"key\": \"RESTOCK\", \"description\": \"재입고\" },\n" +
                                    "  { \"key\": \"DELIVERY\", \"description\": \"배송\" },\n" +
                                    "  { \"key\": \"ETC\", \"description\": \"기타\" }\n" +
                                    "]")))
    })
    ResponseEntity<List<InquiryCategoryResponse>> getProductInquiryCategories();

    @Operation(
            summary = "상품 문의 목록 조회 (상품 상세 문의 탭)",
            description = "C7 상품 상세 「문의」 탭의 목록입니다. 최신순이고 삭제 집행된 문의는 빠집니다.\n\n" +
                    "- `type` — 유형 필터(비우면 전체) · `status` — `WAITING`(답변대기) · `ANSWERED`(답변완료)\n" +
                    "- **비밀글** — 작성자 본인에게만 `content` · `imageUrls` · `answer` 를 싣습니다. 아니면 비어 있고 앱이 " +
                    "「비밀글입니다. 작성자와 판매자만 볼 수 있어요」를 그립니다\n" +
                    "- `authorName` — 닉네임 앞 절반만 노출(수민초록 → 수민** · 홀수 길이는 가리는 쪽을 더 많이)\n" +
                    "- `mine` — 로그인한 소비자 본인의 문의\n" +
                    "- 개수(「상품 문의 12」)는 `pageInfo.totalResults` 입니다\n\n" +
                    "**권한:** 인증 불필요 (비로그인 허용 · 로그인하면 본인 비밀글을 봅니다)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "id": 5021, "type": "INGREDIENT_USAGE", "typeName": "성분·사용법", "status": "ANSWERED",
                                          "secret": false, "mine": false,
                                          "content": "민감성인데 아침에도 써도 괜찮을까요?", "imageUrls": [],
                                          "authorName": "수민**", "createdAt": "2026-08-08T01:12:00Z",
                                          "answer": { "content": "네, 아침저녁 모두 사용 가능합니다.", "answeredAt": "2026-08-09T02:30:00Z" } },
                                        { "id": 5022, "type": "OPTION", "typeName": "옵션", "status": "ANSWERED",
                                          "secret": true, "mine": false, "content": null, "imageUrls": [],
                                          "authorName": "현*", "createdAt": "2026-08-09T00:40:00Z", "answer": null }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false }
                                    }"""))),
            @ApiResponse(responseCode = "404", description = "없는 상품 (`PRODUCT_NOT_FOUND`)")
    })
    PageResponse<PublicProductInquiryResponse> getProductInquiries(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @Parameter(description = "상품 ID", example = "101", in = ParameterIn.PATH) Long productId,
            @Parameter(description = "문의 유형 — 비우면 전체", example = "INGREDIENT_USAGE") ProductInquiryType type,
            @Parameter(description = "답변 상태 — WAITING · ANSWERED · 비우면 전체", example = "ANSWERED") InquiryStatus status,
            @Parameter(hidden = true) PagingRequest pagingRequest);
}
