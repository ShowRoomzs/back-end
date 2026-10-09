package showroomz.api.common.carrier.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import showroomz.api.common.carrier.dto.DeliveryCarrierResponse;

import java.util.List;

@Tag(name = "Common - Delivery Carrier", description = "택배사 목록 API")
public interface CommonDeliveryCarrierControllerDocs {

    @Operation(
            summary = "택배사 목록 조회",
            description = """
                    **추적 연동 업체 목록 하나**를 내린다 — 파트너센터 주문 송장 · 재발송 송장, 소비자 회수 송장이 모두 이 목록에서
                    고른다(거래 관리 결정 5). 자유 입력 · 수기 송장은 없다. 노출 순서 그대로 그린다.

                    목록에 없는 코드(`COUPANG` 등 추적 연동이 없는 택배사)로 송장을 등록하면 거절된다.

                    **권한:** 없음
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DeliveryCarrierResponse.class)),
                            examples = @ExampleObject(name = "목록", value = """
                                    [
                                      {"code": "CJ", "label": "CJ대한통운"},
                                      {"code": "EPOST", "label": "우체국택배"},
                                      {"code": "HANJIN", "label": "한진택배"},
                                      {"code": "LOTTE", "label": "롯데택배"},
                                      {"code": "LOGEN", "label": "로젠택배"},
                                      {"code": "KYUNGDONG", "label": "경동택배"},
                                      {"code": "DAESIN", "label": "대신택배"},
                                      {"code": "ILYANG", "label": "일양로지스"},
                                      {"code": "CU", "label": "CU 편의점택배"},
                                      {"code": "GS25", "label": "GS25 편의점택배"},
                                      {"code": "HAPDONG", "label": "합동택배"},
                                      {"code": "WOORI", "label": "우리택배"}
                                    ]
                                    """)))
    })
    List<DeliveryCarrierResponse> getCarriers();
}
