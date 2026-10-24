package showroomz.domain.message.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.domain.connection.entity.Connection;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.message.type.ThreadStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

    /**
     * 연결 쌍의 일반 스레드는 {@code kind = CONNECTION}으로 찾는다 — 같은 연결에 공구 3자 스레드가 함께 붙으므로
     * 연결만으로 찾으면 여러 건이 나온다(30-1 1-3).
     */
    Optional<MessageThread> findByConnectionAndKind(Connection connection, ThreadKind kind);

    /** 공구 미이행 3자 스레드 — 공구당 1개다(30-1 1-4). */
    Optional<MessageThread> findFirstByKindAndSubjectIdOrderByIdAsc(ThreadKind kind, Long subjectId);

    /**
     * §13-1 좌측 목록 — 운영자 채널(OPERATOR_MARKET) 최상단 고정 + STATUS=OPEN만, 최근 메시지순.
     * 목록 아바타(counterpartImageUrl)가 CREATOR.USER.PROFILE_IMAGE_URL에 있으므로 user까지 fetch한다
     * — 지연 로딩으로 두면 스레드 수만큼 추가 쿼리가 나간다(N+1).
     * keyword는 좌측 목록 상단의 "쇼룸명 검색" 입력이다 — 값이 있으면 상대 쇼룸명 부분 일치만 남고,
     * 운영자 채널은 자연히 빠진다(creator가 NULL이라 LIKE 조건이 성립하지 않는다).
     */
    @Query("SELECT t FROM MessageThread t JOIN FETCH t.connection c " +
           "LEFT JOIN FETCH c.creator cr LEFT JOIN FETCH cr.user " +
           "WHERE t.status = :status AND c.market = :market " +
           "AND (:keyword IS NULL OR :keyword = '' OR cr.showroomName LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY CASE WHEN c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_MARKET THEN 0 ELSE 1 END, " +
           "t.lastMessageAt DESC NULLS LAST")
    Page<MessageThread> findOpenThreadsForMarket(@Param("market") Market market,
                                                  @Param("status") ThreadStatus status,
                                                  @Param("keyword") String keyword,
                                                  Pageable pageable);

    /**
     * §14-3 `연결됨` 탭 — 운영자 채널(OPERATOR_CREATOR) 최상단 고정 + STATUS=OPEN만, 최근 메시지순.
     * keyword는 시안(S1~S13) 좌측 목록 상단의 "브랜드명 검색" 입력이다 — 값이 있으면 브랜드명 부분 일치만 남는다.
     * 이때 운영자 채널은 자연히 빠진다(market이 NULL이라 LIKE 조건이 성립하지 않는다) — 검색 대상이
     * "브랜드명"이므로 브랜드가 아닌 고정 채널이 검색 결과에 남아 있으면 오히려 방해가 된다.
     */
    @Query("SELECT t FROM MessageThread t JOIN FETCH t.connection c LEFT JOIN FETCH c.market m " +
           "WHERE t.status = :status AND c.creator = :creator " +
           "AND (:keyword IS NULL OR :keyword = '' OR m.marketName LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY CASE WHEN c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_CREATOR THEN 0 ELSE 1 END, " +
           "t.lastMessageAt DESC NULLS LAST")
    Page<MessageThread> findOpenThreadsForCreator(@Param("creator") Creator creator,
                                                   @Param("status") ThreadStatus status,
                                                   @Param("keyword") String keyword,
                                                   Pageable pageable);

    /**
     * 배지 집계 전용 — 스레드 엔티티를 통째로 로드하지 않고 id만 가져온다. 목록과 달리 배지는
     * 정렬·표시 정보가 전혀 필요 없고, 30~60초 폴링(§0) 대상이라 로딩 비용을 줄일 값어치가 있다.
     */
    @Query("SELECT t.id FROM MessageThread t JOIN t.connection c " +
           "WHERE t.status = :status AND c.market = :market")
    List<Long> findOpenThreadIdsForMarket(@Param("market") Market market,
                                           @Param("status") ThreadStatus status);

    @Query("SELECT t.id FROM MessageThread t JOIN t.connection c " +
           "WHERE t.status = :status AND c.creator = :creator")
    List<Long> findOpenThreadIdsForCreator(@Param("creator") Creator creator,
                                            @Param("status") ThreadStatus status);

    /** 어드민 접근 판정 · 헤더(36 설계 2-1) — 연결의 양쪽 당사자를 함께 읽는다. */
    @Query("SELECT t FROM MessageThread t JOIN FETCH t.connection c " +
           "LEFT JOIN FETCH c.market m LEFT JOIN FETCH m.seller " +
           "LEFT JOIN FETCH c.creator cr LEFT JOIN FETCH cr.user " +
           "WHERE t.id = :threadId")
    Optional<MessageThread> findWithPartiesById(@Param("threadId") Long threadId);

    /**
     * 「소명 미확인」(55 계획서 A8) — 아래를 모두 만족하는 운영팀 채널. 목록 정렬과 행 표시가 이 한 문장을 같이 쓴다.
     * <ul>
     *   <li>열린 정지가 있다 — 별칭 {@code sx}(브랜드 · 인플루언서 정지 행, {@code releasedAt IS NULL}로 LEFT JOIN)</li>
     *   <li>그 채널에 회원이 보낸 말풍선이 있다 — {@code TEXT ∧ senderType <> ADMIN}. 회원 쪽 시스템 카드(재발송 요청 등)는 뺀다</li>
     *   <li>그 말풍선이 정지 카드보다 뒤다 — 정지 행의 카드 id(V193 · V199). 카드가 없으면 0</li>
     *   <li>그 말풍선이 운영팀 공용 읽음 위치({@link #OPERATOR_TEAM_READ}, 별칭 {@code rp})보다 뒤다</li>
     * </ul>
     * ORDER BY 안의 상관 EXISTS 는 MySQL 8 · H2(MySQL 모드)가 같은 문장을 받는다 — Hibernate 가 CASE 식 그대로 내보낸다.
     */
    String APPEAL_UNCONFIRMED = "(sx.id IS NOT NULL AND EXISTS (SELECT 1 FROM Message am WHERE am.thread = t " +
            "AND am.messageType = showroomz.domain.message.type.MessageType.TEXT " +
            "AND am.senderType <> showroomz.domain.message.type.ParticipantType.ADMIN " +
            "AND am.id > COALESCE(sx.suspendCardId, 0L) " +
            "AND (rp.lastReadMessageId IS NULL OR am.id > rp.lastReadMessageId)))";

    /** 운영팀 공용 읽음 위치(36 설계 0-5) — {@code ADMIN} · {@code teamParticipantId} 1행. 없으면 아무것도 읽지 않은 것이다. */
    String OPERATOR_TEAM_READ = "LEFT JOIN ThreadParticipant rp ON rp.thread = t " +
            "AND rp.participantType = showroomz.domain.message.type.ParticipantType.ADMIN " +
            "AND rp.participantId = :teamParticipantId ";

    String OPEN_BRAND_SANCTION = "LEFT JOIN BrandSanction sx ON sx.marketId = m.id AND sx.releasedAt IS NULL ";

    String OPEN_CREATOR_SANCTION = "LEFT JOIN CreatorSanction sx ON sx.creatorId = cr.id AND sx.releasedAt IS NULL ";

    /**
     * 어드민 소통 스레드 · 브랜드 탭(36 설계 3-1) — 브랜드당 1개인 운영팀 채널 전량. 메시지 0건 채널도 나온다.
     * {@code marketId}가 있으면 회원번호 검색이다. 검색어는 브랜드명 · 담당자 부분 일치다.
     * 「소명 미확인」 채널이 맨 위에 고정되고({@link #APPEAL_UNCONFIRMED}), 그 안팎은 최근 메시지순이다.
     * 정지 행 · 읽음 위치는 회원 · 채널당 최대 1행이라 행이 불어나지 않는다(count 쿼리는 그대로 둔다).
     */
    @Query(value = "SELECT t FROM MessageThread t JOIN FETCH t.connection c JOIN FETCH c.market m JOIN FETCH m.seller s " +
                   OPEN_BRAND_SANCTION + OPERATOR_TEAM_READ +
                   "WHERE c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_MARKET " +
                   "AND t.kind = showroomz.domain.message.type.ThreadKind.CONNECTION " +
                   "AND (:marketId IS NULL OR m.id = :marketId) " +
                   "AND (:keyword IS NULL OR m.marketName LIKE CONCAT('%', :keyword, '%') " +
                   "     OR s.name LIKE CONCAT('%', :keyword, '%')) " +
                   "ORDER BY CASE WHEN " + APPEAL_UNCONFIRMED + " THEN 0 ELSE 1 END, " +
                   "t.lastMessageAt DESC NULLS LAST, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM MessageThread t JOIN t.connection c JOIN c.market m JOIN m.seller s " +
                        "WHERE c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_MARKET " +
                        "AND t.kind = showroomz.domain.message.type.ThreadKind.CONNECTION " +
                        "AND (:marketId IS NULL OR m.id = :marketId) " +
                        "AND (:keyword IS NULL OR m.marketName LIKE CONCAT('%', :keyword, '%') " +
                        "     OR s.name LIKE CONCAT('%', :keyword, '%'))")
    Page<MessageThread> findOperatorMarketChannels(@Param("marketId") Long marketId,
                                                   @Param("keyword") String keyword,
                                                   @Param("teamParticipantId") Long teamParticipantId,
                                                   Pageable pageable);

    /** 어드민 소통 스레드 · 인플루언서 탭(36 설계 3-1) — 검색어는 쇼룸명 부분 일치다. 「소명 미확인」 고정은 브랜드 탭과 같다. */
    @Query(value = "SELECT t FROM MessageThread t JOIN FETCH t.connection c JOIN FETCH c.creator cr JOIN FETCH cr.user " +
                   OPEN_CREATOR_SANCTION + OPERATOR_TEAM_READ +
                   "WHERE c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_CREATOR " +
                   "AND t.kind = showroomz.domain.message.type.ThreadKind.CONNECTION " +
                   "AND (:creatorId IS NULL OR cr.id = :creatorId) " +
                   "AND (:keyword IS NULL OR cr.showroomName LIKE CONCAT('%', :keyword, '%')) " +
                   "ORDER BY CASE WHEN " + APPEAL_UNCONFIRMED + " THEN 0 ELSE 1 END, " +
                   "t.lastMessageAt DESC NULLS LAST, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM MessageThread t JOIN t.connection c JOIN c.creator cr " +
                        "WHERE c.type = showroomz.domain.connection.type.ConnectionType.OPERATOR_CREATOR " +
                        "AND t.kind = showroomz.domain.message.type.ThreadKind.CONNECTION " +
                        "AND (:creatorId IS NULL OR cr.id = :creatorId) " +
                        "AND (:keyword IS NULL OR cr.showroomName LIKE CONCAT('%', :keyword, '%'))")
    Page<MessageThread> findOperatorCreatorChannels(@Param("creatorId") Long creatorId,
                                                    @Param("keyword") String keyword,
                                                    @Param("teamParticipantId") Long teamParticipantId,
                                                    Pageable pageable);

    /** 목록 페이지 중 「소명 미확인」 브랜드 채널 — 정렬과 같은 판정식({@link #APPEAL_UNCONFIRMED})으로 행 표시를 채운다. */
    @Query("SELECT t.id FROM MessageThread t JOIN t.connection c JOIN c.market m " +
           OPEN_BRAND_SANCTION + OPERATOR_TEAM_READ +
           "WHERE t.id IN :threadIds AND " + APPEAL_UNCONFIRMED)
    List<Long> findAppealUnconfirmedMarketChannelIds(@Param("threadIds") Collection<Long> threadIds,
                                                     @Param("teamParticipantId") Long teamParticipantId);

    /** {@link #findAppealUnconfirmedMarketChannelIds}의 인플루언서 쪽. */
    @Query("SELECT t.id FROM MessageThread t JOIN t.connection c JOIN c.creator cr " +
           OPEN_CREATOR_SANCTION + OPERATOR_TEAM_READ +
           "WHERE t.id IN :threadIds AND " + APPEAL_UNCONFIRMED)
    List<Long> findAppealUnconfirmedCreatorChannelIds(@Param("threadIds") Collection<Long> threadIds,
                                                      @Param("teamParticipantId") Long teamParticipantId);

    /**
     * 정보 바의 「열린 이슈 스레드」(36 설계 3-4 · 44 이슈 스레드 설계서 4-6) — 그 회원이 당사자인 3자 스레드.
     * 정산 조정 스레드는 협의가 진행 중(OPEN)인 것만. 폐기 이력 두 종류의 조건은 그대로다 — 구 이슈 스레드는 이슈가 열려 있는 것만,
     * 이행 이견 스레드는 전량(닫힘 표시가 스레드에 없다).
     */
    @Query("SELECT t FROM MessageThread t JOIN t.connection c " +
           "WHERE t.status = showroomz.domain.message.type.ThreadStatus.OPEN " +
           "AND ((:marketId IS NOT NULL AND c.market.id = :marketId) " +
           "     OR (:creatorId IS NOT NULL AND c.creator.id = :creatorId)) " +
           "AND (t.kind = showroomz.domain.message.type.ThreadKind.GROUP_BUY_FULFILLMENT " +
           "     OR (t.kind = showroomz.domain.message.type.ThreadKind.GROUP_BUY_ISSUE AND EXISTS (" +
           "         SELECT 1 FROM GroupBuyIssue i WHERE i.threadId = t.id " +
           "         AND i.status = showroomz.domain.groupbuy.type.GroupBuyIssueStatus.OPEN)) " +
           "     OR (t.kind = showroomz.domain.message.type.ThreadKind.SETTLEMENT_ADJUSTMENT AND EXISTS (" +
           "         SELECT 1 FROM SettlementAdjustment a WHERE a.threadId = t.id " +
           "         AND a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN))) " +
           "ORDER BY t.id DESC")
    List<MessageThread> findOpenGroupBuyThreadsOf(@Param("marketId") Long marketId,
                                                  @Param("creatorId") Long creatorId);
}
