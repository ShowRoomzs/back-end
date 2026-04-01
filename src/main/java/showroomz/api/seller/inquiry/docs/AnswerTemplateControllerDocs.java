package showroomz.api.seller.inquiry.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterResponse;

@Tag(name = "Seller - Answer Template", description = "판매자 답변 템플릿 API")
public interface AnswerTemplateControllerDocs {

    @Operation(
            summary = "답변 템플릿 등록",
            description = "문의 답변에 사용할 템플릿을 등록합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**필수 입력:**\n" +
                    "- `title`: 템플릿 제목 (최대 30자)\n" +
                    "- `category`: 카테고리 (PRODUCT, SIZE, STOCK, DELIVERY, ORDER_PAYMENT, CANCEL_REFUND_EXCHANGE, DEFECT_AS)\n" +
                    "- `content`: 답변 내용 (최대 1000자)\n\n" +
                    "**선택 입력:**\n" +
                    "- `isActive`: 사용 여부. false 설정 시 템플릿 목록에 노출되지 않습니다. 기본값은 true(사용)입니다.\n\n" +
                    "**카테고리 설명:**\n" +
                    "| 코드 | 설명 |\n" +
                    "|------|------|\n" +
                    "| `PRODUCT` | 상품 |\n" +
                    "| `SIZE` | 사이즈 |\n" +
                    "| `STOCK` | 재고/재입고 |\n" +
                    "| `DELIVERY` | 배송 |\n" +
                    "| `ORDER_PAYMENT` | 주문/결제 |\n" +
                    "| `CANCEL_REFUND_EXCHANGE` | 취소/교환/환불 |\n" +
                    "| `DEFECT_AS` | 불량/AS |"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "답변 템플릿 등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnswerTemplateRegisterResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "등록 성공",
                                            value = "{\n" +
                                                    "  \"templateId\": 1\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "제목 30자 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"템플릿 제목은 최대 30자까지 입력 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "답변 내용 1000자 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"답변 내용은 최대 1000자까지 입력 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "필수 항목 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "답변 템플릿 등록 요청 바디",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AnswerTemplateRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "사용 중인 템플릿 등록",
                                    value = "{\n" +
                                            "  \"title\": \"재입고 안내 템플릿\",\n" +
                                            "  \"category\": \"STOCK\",\n" +
                                            "  \"content\": \"안녕하세요, 해당 상품은 다음 주 중 재입고 예정입니다. 감사합니다.\",\n" +
                                            "  \"isActive\": true\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "미사용 템플릿 등록",
                                    value = "{\n" +
                                            "  \"title\": \"배송 지연 안내\",\n" +
                                            "  \"category\": \"DELIVERY\",\n" +
                                            "  \"content\": \"현재 물류 상황으로 인해 배송이 다소 지연될 수 있습니다. 양해 부탁드립니다.\",\n" +
                                            "  \"isActive\": false\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<AnswerTemplateRegisterResponse> registerTemplate(
            @Valid @RequestBody AnswerTemplateRegisterRequest request
    );
}
