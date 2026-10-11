package showroomz.api.admin.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import showroomz.domain.calendar.entity.BusinessHoliday;

import java.time.LocalDate;

public final class BusinessHolidayDto {

    private BusinessHolidayDto() {
    }

    public record Item(
            @Schema(description = "공휴일", example = "2026-10-09") LocalDate date,
            @Schema(description = "이름", example = "한글날") String name
    ) {
        public static Item of(BusinessHoliday holiday) {
            return new Item(holiday.getDate(), holiday.getName());
        }
    }

    public record UpsertRequest(
            @Schema(description = "이름 — 50자", example = "임시공휴일")
            @NotBlank @Size(max = 50) String name
    ) {
    }
}
