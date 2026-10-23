package showroomz.api.common.company.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.company.dto.CompanyInfoResponse;

@Tag(name = "Common - Company Info", description = "공용 사업자 정보 API")
public interface CommonCompanyInfoControllerDocs {

    @Operation(
            summary = "운영자 사업자 정보",
            description = "C 마이 · C4 쇼룸 · C7 상품 상세 하단 · 디자인 시스템 Footer 가 같은 값을 그린다(1021 결정 노트 기-35).\n\n"
                    + "- 세금계산서 공급받는자 · 원천징수영수증 발행자와 같은 출처(설정 `company.*`)\n"
                    + "- `mailOrderReportNumber` 가 null 이면 「신고 예정」, 고객센터 값이 null 이면 「[확인 필요]」로 그린다\n\n"
                    + "인증 불요."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CompanyInfoResponse.class)))
    })
    ResponseEntity<CompanyInfoResponse> getCompanyInfo();
}
