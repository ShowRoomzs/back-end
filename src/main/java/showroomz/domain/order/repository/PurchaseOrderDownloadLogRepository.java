package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.PurchaseOrderDownloadLog;

/** 발주서 반출 이력 — 기록만 한다. 조회 화면은 어드민 몫(§34-13 #14). */
public interface PurchaseOrderDownloadLogRepository extends JpaRepository<PurchaseOrderDownloadLog, Long> {
}
