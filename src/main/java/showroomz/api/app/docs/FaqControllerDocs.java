package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

@Tag(name = "Common - FAQ", description = "자주 묻는 질문(FAQ) 조회 API")
public interface FaqControllerDocs {

    @Operation(
            summary = "FAQ 목록 조회",
            description = "자주 묻는 질문(FAQ) 목록을 조회합니다.\n\n" +
                    "**필터:**\n" +
                    "- `type`을 전달하면 해당 타입의 FAQ만 반환합니다.\n" +
                    "- `type`을 생략하면 전체(노출=true) FAQ를 반환합니다.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FaqResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"id\": 1,\n" +
                                                    "    \"type\": \"DELIVERY\",\n" +
                                                    "    \"category\": \"배송 지연\",\n" +
                                                    "    \"question\": \"배송은 얼마나 걸리나요?\",\n" +
                                                    "    \"answer\": \"평균 2~3일 소요됩니다.\"\n" +
                                                    "  }\n" +
                                                    "]"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 파라미터 오류 (예: type 값이 enum에 없음)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<FaqResponse>> getFaqList(
            @Parameter(description = "질문 타입 (DELIVERY, ORDER_PAYMENT 등)", required = false, example = "DELIVERY")
            @RequestParam(value = "type", required = false) InquiryType type
    );
}

