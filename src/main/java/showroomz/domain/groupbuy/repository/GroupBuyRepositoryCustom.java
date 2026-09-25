package showroomz.domain.groupbuy.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.CreatorGroupBuySortType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 스튜디오 공구 조회 — <b>모든 메서드가 {@code creator_id}를 받는다</b>(31 설계 0-4). 판정 없는 스튜디오용 조회를 두지 않는다.
 */
public interface GroupBuyRepositoryCustom {

    /**
     * 스튜디오 목록. 검색은 공구명(계약) · 브랜드명 · 공구번호다 — 스튜디오에서 검색 대상 상대는 브랜드다(31 설계 1-4).
     *
     * @param pattern {@code %keyword%} 또는 null
     */
    Page<GroupBuy> searchForCreator(Long creatorId, Collection<GroupBuyStatus> statuses, String pattern,
                                    CreatorGroupBuySortType sort, LocalDateTime now, Pageable pageable);

    /** 목록과 <b>같은 조건·같은 정렬</b>의 id 전체 — 상세의 이웃(이전·다음)을 목록과 어긋나지 않게 고른다. */
    List<Long> findOrderedIdsForCreator(Long creatorId, Collection<GroupBuyStatus> statuses, String pattern,
                                        CreatorGroupBuySortType sort, LocalDateTime now);

    /** 「내 조치 필요」 건수 — GNB 배지 · 목록 헤더. */
    long countActionRequiredForCreator(Long creatorId, LocalDateTime now);

    /** 주어진 공구 중 「내 조치 필요」인 것 — 목록 행의 {@code actionRequired}. 배지와 같은 식이다. */
    Set<Long> findActionRequiredIdsForCreator(Long creatorId, Collection<Long> groupBuyIds, LocalDateTime now);
}
