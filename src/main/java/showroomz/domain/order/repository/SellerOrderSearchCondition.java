package showroomz.domain.order.repository;

import showroomz.domain.order.type.OrderDateBasis;
import showroomz.domain.order.type.OrderSearchType;
import showroomz.domain.order.type.OrderSortType;
import showroomz.domain.order.type.OrderTab;

import java.time.LocalDateTime;

/**
 * 파트너센터 주문 목록 검색 조건(34 설계서 4-1). 기본값 보정(탭 기본 기간·기본 정렬·1년 상한)은
 * 서비스가 끝내고 여기는 확정값만 담는다.
 */
public record SellerOrderSearchCondition(
        Long marketId,
        OrderTab tab,
        OrderDateBasis dateBasis,
        LocalDateTime from,
        LocalDateTime to,
        OrderSearchType searchType,
        String keyword,
        OrderSortType sort
) {
}
