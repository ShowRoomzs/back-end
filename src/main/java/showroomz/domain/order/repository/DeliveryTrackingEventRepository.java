package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.List;
import java.util.Optional;

/** append-only — UPDATE·DELETE 메서드를 두지 않는다(앱 클레임 설계서 1-6). */
public interface DeliveryTrackingEventRepository extends JpaRepository<DeliveryTrackingEvent, Long> {

    long countByCarrierAndTrackingNumber(DeliveryCarrier carrier, String trackingNumber);

    /** 화면용 — 최신순 전체(앱이 접는다). */
    List<DeliveryTrackingEvent> findByCarrierAndTrackingNumberOrderBySeqDesc(DeliveryCarrier carrier,
                                                                             String trackingNumber);

    /** 마지막 스캔 한 줄 — 파트너센터 주문 상세 우 레일. */
    Optional<DeliveryTrackingEvent> findFirstByCarrierAndTrackingNumberOrderBySeqDesc(DeliveryCarrier carrier,
                                                                                      String trackingNumber);
}
