package showroomz.domain.groupbuy.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.AdminGroupBuyQueue;
import showroomz.domain.groupbuy.type.AdminGroupBuySortType;
import showroomz.domain.groupbuy.type.AdminGroupBuyTab;
import showroomz.domain.groupbuy.type.CreatorGroupBuySortType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 공구 목록 조회 — QueryDSL로만 표현되는 판정식(조치 필요)을 쓰는 조회.
 *
 * <p>스튜디오 메서드는 <b>모두 {@code creator_id}를 받는다</b>(31 설계 0-4). 판정 없는 스튜디오용 조회를 두지 않는다.
 * 어드민 메서드는 가시성 필터가 없다 — 어드민은 모든 공구를 본다(32 설계 2-1).
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

    // ── 어드민(32 설계) ──────────────────────────────────────────────────────

    /**
     * 어드민 목록. 검색은 공구명 · 공구번호 · 브랜드명 · 쇼룸명 — 양측을 다 찾는다(32 설계 2-5).
     *
     * @param pattern {@code %keyword%} 또는 null
     */
    Page<GroupBuy> searchForAdmin(AdminGroupBuyTab tab, String pattern, AdminGroupBuySortType sort,
                                  LocalDateTime now, Pageable pageable);

    /** 목록과 <b>같은 조건·같은 정렬</b>의 id 전체 — 상세의 이웃(이전·다음). */
    List<Long> findOrderedIdsForAdmin(AdminGroupBuyTab tab, String pattern, AdminGroupBuySortType sort,
                                      LocalDateTime now);

    /** 큐 1종의 건수 — 요약·GNB 배지. 검색어와 무관하다(28 설계 0-5). */
    long countAdminQueue(AdminGroupBuyQueue queue, LocalDateTime now);

    /** 주어진 공구 중 조치 큐에 해당하는 것 — 목록 행의 {@code actionRequired}. 배지와 같은 식이다. */
    Set<Long> findAdminActionRequiredIds(Collection<Long> groupBuyIds, LocalDateTime now);

    /** 오픈 승인 큐에서 가장 먼저 제출된 1건 — 기한은 제출일 + 영업일이라 제출 시각 순서와 같다. */
    DeadlineRow findEarliestOpenReview();

    /** 소명 검토 큐에서 집행 예정이 가장 이른 1건. */
    DeadlineRow findEarliestAppealReview(LocalDateTime now);

    /** 정산 지연 감시 — 종료(ENDED) 중 종료 시각이 가장 이른 1건. */
    DeadlineRow findEarliestEnded();

    /** 종료(ENDED) 중 종료 시각이 기준 이하인 건수 — 정산 지연 감시 기한 초과. */
    long countEndedAtOrBefore(LocalDateTime threshold);

    /** 요약의 기한 행 — 없으면 메서드가 null을 돌려준다. */
    record DeadlineRow(Long groupBuyId, String title, LocalDateTime at) {
    }
}
