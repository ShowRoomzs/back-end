package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.settlement.entity.SettlementWithholdingReportLog;

public interface SettlementWithholdingReportLogRepository extends JpaRepository<SettlementWithholdingReportLog, Long> {
}
