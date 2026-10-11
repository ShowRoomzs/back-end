package showroomz.api.admin.calendar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.calendar.docs.AdminBusinessHolidayControllerDocs;
import showroomz.api.admin.calendar.dto.BusinessHolidayDto;
import showroomz.api.admin.calendar.service.AdminBusinessHolidayService;
import showroomz.api.app.auth.entity.UserPrincipal;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/business-holidays")
@RequiredArgsConstructor
public class AdminBusinessHolidayController implements AdminBusinessHolidayControllerDocs {

    private final AdminBusinessHolidayService holidayService;

    @Override
    @GetMapping
    public ResponseEntity<List<BusinessHolidayDto.Item>> getHolidays(@RequestParam("year") int year) {
        return ResponseEntity.ok(holidayService.getHolidays(year));
    }

    @Override
    @PutMapping("/{date}")
    public ResponseEntity<BusinessHolidayDto.Item> upsert(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody BusinessHolidayDto.UpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(holidayService.upsert(date, request.name(),
                principal == null ? null : principal.getUserId()));
    }

    @Override
    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        holidayService.delete(date);
        return ResponseEntity.noContent().build();
    }
}
