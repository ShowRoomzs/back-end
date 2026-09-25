package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long>, ContractRepositoryCustom {

    /** 문서 변경과 체결 검증도 동일한 계약 행을 잠가 원자적으로 처리한다. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contract c WHERE c.id = :contractId")
    Optional<Contract> findForAdminUpdate(@Param("contractId") Long contractId);

    /**
     * 임시저장의 낙관적 락 — <b>버전 검사와 증가를 조건부 UPDATE 하나로</b> 한다(설계서 3-3·3-4).
     *
     * <p>{@code items}는 {@code mappedBy} 역방향 컬렉션이라 항목만 갈아 끼운 저장은 계약 행을
     * 더럽히지 않는다 — JPA에 맡기면 {@code @Version}이 오르지 않고, 탭 두 개가 같은 버전을 들고
     * 번갈아 저장해도 둘 다 통과해 <b>나중 저장이 앞 저장을 말없이 덮는다</b>.
     *
     * <p>{@code OPTIMISTIC_FORCE_INCREMENT}를 쓰지 않는 이유는 그쪽 증가가 <b>커밋 시점</b>이라
     * 응답에 실어 보내는 버전이 한 박자 뒤처지기 때문이다 — FE가 그 값을 그대로 다음 저장에 쓰면
     * 자기 저장에 자기가 막힌다. 읽어서 비교하는 대신 DB가 한 번에 판정하게 두면 동시 요청에서도
     * 한쪽만 1행을 얻는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Contract c SET c.version = c.version + 1 WHERE c.id = :contractId AND c.version = :version")
    int bumpVersion(@Param("contractId") Long contractId, @Param("version") Long version);

    @Query("SELECT c.status, COUNT(c) FROM Contract c WHERE c.status <> showroomz.domain.contract.type.ContractStatus.DRAFT GROUP BY c.status")
    List<Object[]> countAdminStatuses();

    @Query("SELECT c FROM Contract c "
            + "LEFT JOIN FETCH c.creator cr "
            + "LEFT JOIN FETCH c.market m "
            + "WHERE c.id = :contractId")
    Optional<Contract> findDetailById(@Param("contractId") Long contractId);

    /**
     * 상태 전이의 경합 차단(설계서 3-3). 읽고-판정하고-저장하는 사이에 다른 요청이 상태를 바꿔도
     * DB가 한 번 더 막는다 — 브랜드의 [요청 취소]와 어드민의 [검토 통과]가 동시에 들어오면
     * 하나는 반드시 0행이 되어야 한다. 둘 다 통과하면 「작성중인데 서명 요청이 발송된 계약」이 생긴다.
     *
     * <p>status 컬럼만 건드리고 version은 올리지 않는다 — 전이에 딸린 나머지 필드는 같은 트랜잭션에서
     * 엔티티 변경 감지로 쓰이고, 그때 낙관적 락이 정상적으로 동작해야 한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.status = :to WHERE c.id = :contractId AND c.status = :from")
    int transitionStatus(@Param("contractId") Long contractId,
                         @Param("from") ContractStatus from,
                         @Param("to") ContractStatus to);

    /** 검토 요청·재요청은 출발 상태가 둘(DRAFT · REVIEW_REJECTED)이라 IN으로 받는다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.status = :to WHERE c.id = :contractId AND c.status IN :from")
    int transitionStatusFromAny(@Param("contractId") Long contractId,
                                @Param("from") List<ContractStatus> from,
                                @Param("to") ContractStatus to);

    /**
     * 고정 지급비 [지급 완료 기록](B5a). 0행이면 이미 기록됐거나 체결완료가 아니다.
     * 되돌리는 경로는 만들지 않는다(설계서 미결 #4).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.fixedFeePaidAt = :now "
            + "WHERE c.id = :contractId AND c.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED "
            + "AND c.fixedFeeAmount > 0 AND c.fixedFeePaidAt IS NULL")
    int markFixedFeePaid(@Param("contractId") Long contractId, @Param("now") LocalDateTime now);

    /**
     * 공구 생성 게이트(설계서 1-8). 계약 1건 = 공구 1건.
     * 0행이면 체결완료가 아니거나 이미 공구가 생성된 계약이다 —
     * B5a의 "공구가 이미 생성돼 있어 추가로 만들 수 없습니다"가 이 한 줄로 성립한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.groupBuyId = :groupBuyId "
            + "WHERE c.id = :contractId AND c.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED "
            + "AND c.groupBuyId IS NULL")
    int assignGroupBuy(@Param("contractId") Long contractId, @Param("groupBuyId") Long groupBuyId);

    /**
     * 공구 백필 대상 — 공구 모듈보다 먼저 체결된 계약(공구 설계서 2-3).
     * 체결 트랜잭션이 공구를 만들기 시작한 뒤로는 이 집합이 새로 생기지 않는다.
     */
    @Query("SELECT c.id FROM Contract c "
            + "WHERE c.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED AND c.groupBuyId IS NULL "
            + "ORDER BY c.concludedAt ASC, c.id ASC")
    List<Long> findConcludedIdsWithoutGroupBuy();

    /** 탭 카운트(설계서 4-4). 탭 묶음은 서버가 소유하므로 상태별 카운트를 받아 서비스가 묶는다. */
    @Query("SELECT c.status, COUNT(c) FROM Contract c WHERE c.market.id = :marketId GROUP BY c.status")
    List<Object[]> countByStatus(@Param("marketId") Long marketId);

    /**
     * GNB 배지 — 브랜드가 지금 조치해야 하는 건수(설계서 4-4).
     * 검토 반려(브랜드 조치로만 풀리는 유일한 진행 상태)와 B4c(상대만 서명 완료)만 센다.
     * 검토 대기·체결 처리 대기·B4는 공이 상대에게 있는 정상 대기라 넣지 않는다.
     */
    @Query("SELECT COUNT(c) FROM Contract c WHERE c.market.id = :marketId AND ("
            + "  c.status = showroomz.domain.contract.type.ContractStatus.REVIEW_REJECTED"
            + "  OR (c.status = showroomz.domain.contract.type.ContractStatus.SIGNING"
            + "      AND c.creatorSignedAt IS NOT NULL AND c.brandSignedAt IS NULL))")
    long countActionRequired(@Param("marketId") Long marketId);

    // ── §27 쇼룸 스튜디오 ────────────────────────────────────────────────────

    /**
     * 스튜디오 상세 조회 — <b>가시성 판정이 쿼리 안에 있다</b>(설계서 1-1).
     *
     * <p>판정 없는 {@code findByCreatorId} 류를 두지 않는다. 목록·상세·거절·재발송·문서·조항
     * 여섯 경로가 전부 이 판정을 통과해야 하고, 한 곳만 빠져도 브랜드가 아직 보내지도 않은 계약이
     * 인플루언서에게 나간다.
     *
     * <p>비어 있으면 <b>404</b>다 — 403이 아니다(설계서 1-2). 「있는데 못 본다」를 알리면
     * 인플루언서가 브랜드가 자기 앞으로 계약을 작성 중이라는 사실을, 반려된 계약이라면
     * 「나한테 보내려다 운영자에게 막혔다」까지 읽는다.
     */
    @Query("SELECT c FROM Contract c "
            + "JOIN FETCH c.market m "
            + "JOIN FETCH c.creator cr "
            + "WHERE c.id = :contractId AND cr.id = :creatorId "
            + "AND c.signatureRequestedAt IS NOT NULL "
            + "AND c.status IN :statuses")
    Optional<Contract> findReceivedByCreator(@Param("contractId") Long contractId,
                                             @Param("creatorId") Long creatorId,
                                             @Param("statuses") Collection<ContractStatus> statuses);

    /** 스튜디오 탭 카운트(설계서 3) — 탭 묶음은 서버가 소유하므로 상태별 카운트를 받아 서비스가 묶는다. */
    @Query("SELECT c.status, COUNT(c) FROM Contract c "
            + "WHERE c.creator.id = :creatorId AND c.signatureRequestedAt IS NOT NULL "
            + "AND c.status IN :statuses "
            + "GROUP BY c.status")
    List<Object[]> countByStatusForCreator(@Param("creatorId") Long creatorId,
                                           @Param("statuses") Collection<ContractStatus> statuses);

    /**
     * GNB 배지 — <b>내 서명이 필요한 계약 건수</b>(설계서 3-1). 파트너와 정의가 다르다.
     *
     * <p>{@code brandSignedAt}을 조건에 넣지 않는다. S3a는 「내 차례」가 아니라 「내 몫이 남은 것」이고,
     * 양측 미서명(S3)에서도 내 몫은 똑같이 남아 있다 — rev.2에서 서명 순서가 사라졌다(§27-2).
     * 파트너가 B4c(상대만 서명함) 하나만 배지에 넣은 것과 반대인데, 대칭이 깨져서가 아니라
     * 스튜디오가 그 순서 감각을 명시적으로 폐기했기 때문이다.
     */
    @Query("SELECT COUNT(c) FROM Contract c WHERE c.creator.id = :creatorId "
            + "AND c.signatureRequestedAt IS NOT NULL "
            + "AND c.status = showroomz.domain.contract.type.ContractStatus.SIGNING "
            + "AND c.creatorSignedAt IS NULL")
    long countCreatorActionRequired(@Param("creatorId") Long creatorId);

    /**
     * 인플루언서의 [거절](설계서 5-1) — 조건부 UPDATE에 <b>서명 조건까지</b> 건다.
     *
     * <p>{@code creatorSignedAt IS NULL}이 WHERE에 있어야 하는 이유: 내가 거절 모달을 열어 둔 사이에
     * 운영자가 내 서명을 체크할 수 있다. 서명한 계약이 거절로 종결되면
     * <b>모두싸인에는 내 서명이 남고 우리 시스템은 거절인</b> 상태가 된다.
     *
     * <p>{@code SIGNING}에서만 허용한다 — {@code CONCLUSION_PENDING}은 양측 서명이 끝난 계약이라
     * 409다. 0행이면 상태나 서명 여부가 읽은 뒤에 바뀐 것이다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Contract c "
            + "SET c.status = showroomz.domain.contract.type.ContractStatus.DECLINED, "
            + "    c.closedAt = :now, "
            + "    c.closeActorType = showroomz.domain.contract.type.ContractActorType.CREATOR, "
            + "    c.closeReasonCode = :reasonCode, "
            + "    c.closeReasonMemo = :memo "
            + "WHERE c.id = :contractId AND c.creator.id = :creatorId "
            + "AND c.status = showroomz.domain.contract.type.ContractStatus.SIGNING "
            + "AND c.creatorSignedAt IS NULL")
    int declineByCreator(@Param("contractId") Long contractId,
                         @Param("creatorId") Long creatorId,
                         @Param("reasonCode") String reasonCode,
                         @Param("memo") String memo,
                         @Param("now") LocalDateTime now);

    /**
     * 열람 기록(설계서 5-3) — 상세 GET의 <b>부수 효과</b>다. 별도 API를 만들지 않는다.
     *
     * <p>FE가 {@code POST /view}를 부르는 안은, 호출을 빠뜨리거나 순서를 바꾸면 브랜드 화면이
     * 「열람 안 함」(B7)으로 거짓말을 한다. 열람 여부는 FE가 선택할 사실이 아니다.
     *
     * <p>GET이 쓰기를 한다는 점이 걸리지만 <b>최초 1회 CAS라 멱등이다</b> —
     * 두 번째 호출부터는 0행이고 시각이 덮어써지지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Contract c SET c.creatorViewedAt = :now "
            + "WHERE c.id = :contractId AND c.creatorViewedAt IS NULL")
    int markCreatorViewed(@Param("contractId") Long contractId, @Param("now") LocalDateTime now);
}
