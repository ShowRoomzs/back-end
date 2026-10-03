package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.OrderRefundTask;

/** 환불 집행 큐 — 이 모듈은 INSERT 만 한다. 집행·조회는 어드민 거래 관리 몫이다(34 설계서 1-9). */
public interface OrderRefundTaskRepository extends JpaRepository<OrderRefundTask, Long> {
}
