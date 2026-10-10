package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 원천세 신고 자료 다운로드 기록(44 어드민 설계서 5-5) — 주민등록번호가 든 파일의 반출 기록. */
@Entity
@Table(name = "settlement_withholding_report_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementWithholdingReportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** YYYY-MM — 인플루언서 몫 지급 월. */
    @Column(name = "report_month", nullable = false, length = 7)
    private String reportMonth;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "downloaded_at", nullable = false)
    private LocalDateTime downloadedAt;

    public static SettlementWithholdingReportLog of(Long operatorId, String reportMonth, int rowCount,
                                                    LocalDateTime downloadedAt) {
        SettlementWithholdingReportLog log = new SettlementWithholdingReportLog();
        log.operatorId = operatorId;
        log.reportMonth = reportMonth;
        log.rowCount = rowCount;
        log.downloadedAt = downloadedAt;
        return log;
    }
}
