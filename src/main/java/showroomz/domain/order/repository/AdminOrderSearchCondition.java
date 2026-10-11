package showroomz.domain.order.repository;

import showroomz.domain.order.type.AdminOrderSearchType;
import showroomz.domain.order.type.AdminOrderSort;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.LocalDateTime;

/**
 * 어드민 주문 조회 조건(06a) — 단위는 주문(결제 1건)이다. 하위주문 하나라도 조건에 맞으면 그 주문이 나온다.
 *
 * @param status        상태 셀렉트 — null 이면 전체
 * @param marketId      브랜드 상세에서 「이 브랜드 진행 주문 보기」로 넘어온 경우
 * @param groupBuyId    상세 조건 「공구」
 * @param creatorId     상세 조건 「인플루언서」 — 하위주문의 공구를 연 크리에이터
 * @param paymentMethod 상세 조건 「결제수단」 — 주문 결제의 수단
 * @param trackingAlert 배송 이상 탭의 「이상 유형」 셀렉트
 * @param searchType    검색 대상 — null · ALL 이면 통합(주문번호 · 하위주문번호 · 수취인 · 브랜드명 · 송장)
 * @param keyword       검색어
 * @param sort          정렬 — null 이면 결제일시 최신순
 */
public record AdminOrderSearchCondition(AdminOrderTab tab, FulfillmentStatus status, Long marketId, Long groupBuyId,
                                        Long creatorId, PaymentMethod paymentMethod, TrackingAlert trackingAlert,
                                        AdminOrderSearchType searchType, String keyword, AdminOrderSort sort,
                                        LocalDateTime from, LocalDateTime to) {

    /** 기본 축(탭 · 상태 · 브랜드 · 통합 키워드 · 기간)만 — 요약 · 테스트용. */
    public AdminOrderSearchCondition(AdminOrderTab tab, FulfillmentStatus status, Long marketId, String keyword,
                                     LocalDateTime from, LocalDateTime to) {
        this(tab, status, marketId, null, null, null, null, null, keyword, null, from, to);
    }
}
