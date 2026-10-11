package showroomz.domain.order.repository;

import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimSort;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 파트너센터 반품·교환 목록 조건(35 설계서 1-3). 기간의 기준은 신청일시다. 어드민(06b · {@code marketId = null})은 브랜드명 · 소비자명
 * 검색과 정렬 셀렉트 · 검수 지연 상단 고정을 더 쓴다(38 설계서 7절 #2 · #3).
 *
 * @param types        비어 있으면 전체
 * @param claimId      키워드가 접수번호(CLM-NNNN)였을 때 — PK 조회로 바꾼다
 * @param orderNumber  키워드가 접수번호가 아니었을 때 — 주문번호 검색
 * @param marketName   브랜드명 부분 일치(어드민)
 * @param consumerName 소비자명 부분 일치(어드민) — 행의 소비자명과 같은 값(주문 수취인)
 * @param sort         정렬 셀렉트 — null 이면 탭의 기본 정렬
 * @param overdueFirst 검수 지연(입고 · 기한 경과)을 상단에 고정 — 어드민 전체 탭
 * @param now          검수 지연 판정 시각
 */
public record SellerClaimSearchCondition(Long marketId, ClaimTab tab, Set<ClaimType> types, ClaimReason reason,
                                         LocalDateTime from, LocalDateTime to, Long claimId, String orderNumber,
                                         String marketName, String consumerName, ClaimSort sort, boolean overdueFirst,
                                         LocalDateTime now) {

    public SellerClaimSearchCondition(Long marketId, ClaimTab tab, Set<ClaimType> types, ClaimReason reason,
                                      LocalDateTime from, LocalDateTime to, Long claimId, String orderNumber) {
        this(marketId, tab, types, reason, from, to, claimId, orderNumber, null, null, null, false, LocalDateTime.now());
    }
}
