package showroomz.domain.settlement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementAdminSort;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.Collection;

/** 정산 목록(QueryDSL) — 서피스별 축(마켓 · 인플루언서 · 어드민 전체)의 상태 · 키워드 · 정렬 · 페이징. */
public interface SettlementRepositoryCustom {

    /** 파트너 13 — 내 마켓의 정산. 키워드 = 공구명 · 인플루언서 쇼룸명. */
    Page<Settlement> searchForMarket(Long marketId, Collection<SettlementStatus> statuses, String keyword,
                                     SettlementPartySort sort, Pageable pageable);

    /** 스튜디오 12 — 내 정산. 키워드 = 공구명 · 브랜드명. */
    Page<Settlement> searchForCreator(Long creatorId, Collection<SettlementStatus> statuses, String keyword,
                                      SettlementPartySort sort, Pageable pageable);

    /**
     * 어드민 07a — 전체 정산. 키워드가 {@code STL-}로 시작하면 정산번호 전방 일치, 아니면 공구명 · 브랜드명 · 쇼룸명(7-1).
     */
    Page<Settlement> searchForAdmin(Collection<SettlementStatus> statuses, String keyword, SettlementAdminSort sort,
                                    Pageable pageable);

    /** 어드민 07a 합계 행 · 툴바 — {@link #searchForAdmin}과 같은 조건의 금액 합. */
    SettlementTotals totalsForAdmin(Collection<SettlementStatus> statuses, String keyword);
}
