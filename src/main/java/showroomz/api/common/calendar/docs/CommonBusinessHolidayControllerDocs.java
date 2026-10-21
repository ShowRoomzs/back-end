package showroomz.api.common.calendar.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.calendar.dto.HolidayItem;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Common - Business Holidays", description = "공휴일 공개 조회 — 홈 달력의 주말 · 공휴일 표시")
public interface CommonBusinessHolidayControllerDocs {

    @Operation(
            summary = "공휴일 조회",
            description = "기간 안의 공휴일(대체공휴일 · 임시공휴일 포함)을 날짜순으로 내립니다. 주말은 포함하지 않습니다(FE 가 요일로 칠한다).\n\n"
                    + "영업일 계산(발송 기한 · 응답 기한 · 정산 확인 기간)과 같은 공휴일 집합입니다 — 어드민이 등록한 공휴일이 곧바로 반영됩니다.\n\n"
                    + "최대 400일. 인증 불요."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = HolidayItem.class)))),
            @ApiResponse(responseCode = "400", description = "기간 오류(INVALID_INPUT_VALUE)")
    })
    ResponseEntity<List<HolidayItem>> getHolidays(
            @Parameter(description = "시작일(포함)", example = "2026-09-27") LocalDate from,
            @Parameter(description = "종료일(포함)", example = "2026-11-07") LocalDate to);
}
