package showroomz.api.admin.calendar.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.calendar.dto.BusinessHolidayDto;
import showroomz.api.app.auth.entity.UserPrincipal;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin - Business Holiday", description = "영업일 달력(공휴일) 관리 API")
public interface AdminBusinessHolidayControllerDocs {

    @Operation(summary = "공휴일 목록",
            description = """
                    그 해의 등록 공휴일. **영업일 = 주말 · 공휴일 제외** — 발송 기한(공구 마감 + N영업일) · 취소 요청 응답 기한
                    (1영업일) · 검수 기한(2영업일)이 모두 이 달력을 쓴다. 주말은 목록에 없다(달력이 따로 뺀다).
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<List<BusinessHolidayDto.Item>> getHolidays(
            @Parameter(description = "연도", example = "2026") int year);

    @Operation(summary = "공휴일 등록 · 이름 변경",
            description = """
                    날짜가 키다 — 없으면 등록하고 있으면 이름만 바꾼다. 임시 공휴일 · 대체 공휴일 · 선거일을 지정되는 대로 더한다.
                    **이미 발급된 기한은 바뀌지 않는다**(스냅샷) — 이후 계산부터 적용된다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "저장 성공"))
    ResponseEntity<BusinessHolidayDto.Item> upsert(
            @Parameter(description = "날짜", example = "2026-10-09") LocalDate date,
            BusinessHolidayDto.UpsertRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "공휴일 삭제", description = "없는 날짜도 204 다.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "삭제 성공"))
    ResponseEntity<Void> delete(@Parameter(description = "날짜", example = "2026-10-09") LocalDate date);
}
