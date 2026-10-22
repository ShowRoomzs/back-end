package showroomz.api.admin.metrics.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운영 지표 5탭(ui-admin-21-metrics rev.2). */
@Getter
@RequiredArgsConstructor
public enum MetricsTab {
    CORE("핵심"),
    SUPPLY("공급"),
    GROUP_BUY("공구"),
    DEMAND("수요"),
    OPERATION("운영");

    private final String label;
}
