package showroomz.api.common.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "공휴일 — 대체공휴일 포함")
public record HolidayItem(
        @Schema(example = "2026-10-09") LocalDate date,
        @Schema(example = "한글날") String name
) {
}
