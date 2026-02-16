package showroomz.api.common.bank.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import showroomz.api.common.bank.dto.BankResponse;

import java.util.List;

@Tag(name = "Common - Bank", description = "은행/증권사 목록 조회 API")
public interface CommonBankControllerDocs {

    @Operation(
            summary = "은행 목록 조회",
            description = "환불 계좌 등록 등에 사용할 은행/증권사 목록을 조회합니다. (자주 쓰는 순서 정렬)\n\n" +
                    "**권한:** 없음 (비회원 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "은행 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BankResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "[\n" +
                                                    "  {\"code\": \"090\", \"name\": \"카카오뱅크\"},\n" +
                                                    "  {\"code\": \"092\", \"name\": \"토스뱅크\"},\n" +
                                                    "  {\"code\": \"004\", \"name\": \"KB국민은행\"}\n" +
                                                    "]"
                                    )
                            }
                    )
            )
    })
    List<BankResponse> getBanks();
}
