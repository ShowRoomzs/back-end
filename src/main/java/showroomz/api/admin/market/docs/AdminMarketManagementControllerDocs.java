package showroomz.api.admin.market.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - Market", description = "관리자 마켓 관리 API")
public interface AdminMarketManagementControllerDocs {

    @Operation(
            summary = "마켓 관리 상세 정보 조회",
            description = "마켓 정보 관리용 상세 정보를 조회합니다. 마켓 관리 페이지에서 사용됩니다.\n\n" +
                    "**반환 정보:**\n" +
                    "- 마켓 기본 정보 (마켓명, 고객센터 번호, 이미지, 소개글, URL, 대표 카테고리, SNS)\n" +
                    "- 등록 상품 수(검수 대기 포함), 검수 대기 상품 수\n" +
                    "- 누적/이번 달 판매액·주문 수(연동 전 더미)\n" +
                    "- 입점일(processedDate), 운영 개월 수, 마켓 상태, 관리자 메모, 가입일\n" +
                    "- 정산(더미: 최근 정산일, 미정산액), 최근 로그인\n" +
                    "- 사업자·정산 계좌 정보(셀러 등록 데이터)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMarketDto.MarketAdminDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음",
                                            value = "{\"code\": \"MARKET_NOT_FOUND\", \"message\": \"존재하지 않는 마켓입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminMarketDto.MarketAdminDetailResponse> getMarketInfo(
            @Parameter(
                    description = "조회할 마켓 ID",
                    required = true,
                    example = "10",
                    in = ParameterIn.PATH
            )
            @PathVariable Long marketId
    );
}
