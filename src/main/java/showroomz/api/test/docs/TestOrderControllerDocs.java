package showroomz.api.test.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.test.dto.TestOrderDto;

@Tag(name = "Z Test", description = "테스트용 API")
public interface TestOrderControllerDocs {

    @Operation(
            summary = "주문 배송완료 처리 (테스트)",
            description = "주문번호의 **배송중(SHIPPING)** 하위주문을 전부 **배송완료(DELIVERED)** 로 바꿉니다. " +
                    "테스트 송장은 택배 조회에 이벤트가 없어 자동 전환되지 않으므로 이 API로 대신합니다.\n\n" +
                    "배송 추적의 자동 확인과 같은 전이라 출처는 「자동 확인」, 처리 이력 메모는 「테스트 처리」로 남고, " +
                    "구매확정 타이머가 지금 시각부터 시작합니다. 배송중이 아닌 하위주문은 건너뜁니다.\n\n" +
                    "**권한:** 없음 (테스트 전용)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "배송완료 처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TestOrderDto.DeliveredResponse.class),
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"orderNumber\": \"20261014-000004\",\n" +
                                            "  \"deliveredSubOrderNumbers\": [\"20261014-000004-01\"],\n" +
                                            "  \"deliveredAt\": \"2026-10-17T14:30:00\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "주문 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "미존재",
                                    value = "{\"code\": \"ORDER_NOT_FOUND\", \"message\": \"주문을 찾을 수 없습니다.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "배송중인 하위주문 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "배송중 아님",
                                    value = "{\"code\": \"ORDER_STATE_CHANGED\", " +
                                            "\"message\": \"배송중(SHIPPING)인 하위주문이 없습니다. 현재: 20261014-000004-01 PREPARING\"}"
                            )
                    )
            )
    })
    ResponseEntity<TestOrderDto.DeliveredResponse> markDelivered(
            @Parameter(
                    name = "orderNumber",
                    description = "주문번호 (yyyyMMdd-NNNNNN)",
                    required = true,
                    example = "20261014-000004",
                    in = ParameterIn.PATH
            )
            @PathVariable("orderNumber") String orderNumber
    );
}
