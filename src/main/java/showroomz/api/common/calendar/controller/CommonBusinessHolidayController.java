package showroomz.api.common.calendar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.calendar.docs.CommonBusinessHolidayControllerDocs;
import showroomz.api.common.calendar.dto.HolidayItem;
import showroomz.api.common.calendar.service.CommonBusinessHolidayService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/common/business-holidays")
@RequiredArgsConstructor
public class CommonBusinessHolidayController implements CommonBusinessHolidayControllerDocs {

    private final CommonBusinessHolidayService holidayService;

    @Override
    @GetMapping
    public ResponseEntity<List<HolidayItem>> getHolidays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(holidayService.getHolidays(from, to));
    }
}
