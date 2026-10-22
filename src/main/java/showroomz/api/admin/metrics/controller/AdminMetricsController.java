package showroomz.api.admin.metrics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.metrics.docs.AdminMetricsControllerDocs;
import showroomz.api.admin.metrics.dto.AdminMetricsDto;
import showroomz.api.admin.metrics.service.AdminMetricsQueryService;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsTab;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController implements AdminMetricsControllerDocs {

    private final AdminMetricsQueryService metricsQueryService;

    @Override
    @GetMapping
    public ResponseEntity<AdminMetricsDto.Response> getMetrics(
            @RequestParam(required = false) MetricsPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MetricsTab tab) {
        return ResponseEntity.ok(metricsQueryService.getMetrics(preset, from, to, tab));
    }

    @Override
    @GetMapping("/tabs")
    public ResponseEntity<List<AdminMetricsDto.TabBadge>> getTabs(
            @RequestParam(required = false) MetricsPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(metricsQueryService.getTabs(preset, from, to));
    }
}
