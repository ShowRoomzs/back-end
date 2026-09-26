package showroomz.domain.groupbuy.service.port;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 판매 모듈이 생기기 전의 빈 구현 — 항상 empty다. 0을 돌려주지 않는다(설계서 0-6).
 * 판매 관리가 {@link GroupBuySalesReader}를 구현하면 이 클래스를 지운다.
 */
@Component
public class EmptyGroupBuySalesReader implements GroupBuySalesReader {

    @Override
    public Optional<GroupBuySales> readSales(Long groupBuyId) {
        return Optional.empty();
    }

    @Override
    public Optional<GroupBuyOrderClosure> readClosure(Long groupBuyId) {
        return Optional.empty();
    }

    @Override
    public Optional<Long> countOrdersSince(Long groupBuyId, LocalDateTime since) {
        return Optional.empty();
    }

    @Override
    public Optional<Long> countOneToOneInquiriesSince(Long groupBuyId, LocalDateTime since) {
        return Optional.empty();
    }
}
