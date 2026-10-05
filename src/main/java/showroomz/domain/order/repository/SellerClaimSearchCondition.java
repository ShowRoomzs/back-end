package showroomz.domain.order.repository;

import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 파트너센터 반품·교환 목록 조건(35 설계서 1-3). 기간의 기준은 신청일시다.
 *
 * @param types   비어 있으면 전체
 * @param claimId 키워드가 접수번호(CLM-NNNN)였을 때 — PK 조회로 바꾼다
 * @param orderNumber 키워드가 접수번호가 아니었을 때 — 주문번호 검색
 */
public record SellerClaimSearchCondition(Long marketId, ClaimTab tab, Set<ClaimType> types, ClaimReason reason,
                                         LocalDateTime from, LocalDateTime to, Long claimId, String orderNumber) {
}
