package showroomz.domain.contract.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.QContract;
import showroomz.domain.contract.type.ContractSortType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.ContractTab;
import showroomz.domain.contract.type.CreatorContractSortType;
import showroomz.domain.contract.type.CreatorContractTab;
import showroomz.domain.market.entity.QMarket;
import showroomz.domain.member.creator.entity.QCreator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class ContractRepositoryImpl implements ContractRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Contract> searchForSeller(Long marketId,
                                          ContractTab tab,
                                          String keyword,
                                          LocalDate startDate,
                                          LocalDate endDate,
                                          ContractSortType sort,
                                          Pageable pageable) {
        QContract contract = QContract.contract;
        QCreator creator = QCreator.creator;

        BooleanBuilder where = new BooleanBuilder();
        where.and(contract.market.id.eq(marketId));

        // ALL은 상태 조건을 걸지 않는다 — IN 절에 9종을 모두 나열할 이유가 없다.
        if (tab != null && !tab.isAll()) {
            where.and(contract.status.in(tab.getStatuses()));
        }

        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            where.and(contract.title.containsIgnoreCase(trimmed)
                    .or(contract.creator.showroomName.containsIgnoreCase(trimmed)));
        }

        // 공구 기간이 조회 구간과 겹치는 계약. 기간이 비어 있는 작성중 행은 기간 조건을 걸면 빠진다 —
        // 「기간으로 찾는다」는 요구 자체가 기간이 있는 계약을 대상으로 하므로 그대로 둔다.
        if (startDate != null) {
            where.and(contract.groupBuyEndAt.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            where.and(contract.groupBuyStartAt.loe(LocalDateTime.of(endDate, LocalTime.MAX)));
        }

        List<Contract> content = queryFactory
                .selectFrom(contract)
                .leftJoin(contract.creator, creator).fetchJoin()
                .where(where)
                .orderBy(orderOf(sort, contract))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(contract.count())
                .from(contract)
                .leftJoin(contract.creator, creator)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    /**
     * 「공구 시작일순」에서 기간이 비어 있는 작성중 계약을 맨 앞으로 끌어올리지 않는다 —
     * MySQL의 ASC는 NULL을 먼저 놓는데, 목록 첫 화면이 (공구명 미입력) 행으로 채워지면
     * 정렬을 바꾼 의미가 없다.
     */
    private OrderSpecifier<?>[] orderOf(ContractSortType sort, QContract contract) {
        if (sort == ContractSortType.START_AT_ASC) {
            return new OrderSpecifier<?>[]{
                    contract.groupBuyStartAt.asc().nullsLast(),
                    contract.id.desc()
            };
        }
        return new OrderSpecifier<?>[]{
                contract.createdAt.desc(),
                contract.id.desc()
        };
    }

    // ── §27 쇼룸 스튜디오 ────────────────────────────────────────────────────

    /**
     * NULL을 정렬 뒤로 보내기 위한 치환값. {@code nullsLast()} 대신 COALESCE를 쓰는 이유는
     * 이웃 계산(prev/next)이 정렬 키의 <b>부등호 비교</b>를 그대로 써야 하는데, NULL이 섞이면
     * {@code key < :cur}가 NULL 행을 통째로 떨어뜨려 이웃이 건너뛰어지기 때문이다.
     * 정렬과 비교가 같은 식을 보도록 양쪽 모두 치환된 키를 쓴다.
     */
    private static final LocalDateTime SORT_NULLS_LAST = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @Override
    public Page<Contract> searchForCreator(Long creatorId,
                                           CreatorContractTab tab,
                                           String keyword,
                                           CreatorContractSortType sort,
                                           Pageable pageable) {
        QContract contract = QContract.contract;
        QMarket market = QMarket.market;

        CreatorContractSortType sortType = sort == null ? CreatorContractSortType.RECEIVED_DESC : sort;
        BooleanBuilder where = receivedByCreator(creatorId, tab, keyword, contract);

        List<Contract> content = queryFactory
                .selectFrom(contract)
                .join(contract.market, market).fetchJoin()
                .where(where)
                .orderBy(orderOfCreator(sortType, contract, false))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(contract.count())
                .from(contract)
                .join(contract.market, market)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    @Override
    public Long findNeighborForCreator(Long creatorId,
                                       Contract current,
                                       CreatorContractTab tab,
                                       String keyword,
                                       CreatorContractSortType sort,
                                       boolean forward) {
        QContract contract = QContract.contract;
        QMarket market = QMarket.market;

        CreatorContractSortType sortType = sort == null ? CreatorContractSortType.RECEIVED_DESC : sort;
        BooleanBuilder where = receivedByCreator(creatorId, tab, keyword, contract);
        where.and(contract.id.ne(current.getId()));
        where.and(neighborBoundary(sortType, contract, current, forward));

        return queryFactory
                .select(contract.id)
                .from(contract)
                .join(contract.market, market)
                .where(where)
                .orderBy(orderOfCreator(sortType, contract, !forward))
                .limit(1)
                .fetchOne();
    }

    /**
     * 「도착한 계약」의 판정식(설계서 1-1) — 목록과 이웃이 같은 식을 쓴다.
     *
     * <p>상태 집합만으로도 논리적으로는 충분하다(전이표상 종결 3종은 이미 서명 요청 이후다).
     * 그럼에도 {@code signature_requested_at IS NOT NULL}을 함께 거는 이유는, 전이표가 나중에
     * 늘어날 때 이 판정이 조용히 틀리는 것을 막기 위해서다 — 검토 대기 단계의 종결 경로가
     * 하나라도 추가되면 서명 요청도 받지 않은 계약이 인플루언서 목록에 종결 상태로 나타난다.
     */
    private BooleanBuilder receivedByCreator(Long creatorId, CreatorContractTab tab,
                                             String keyword, QContract contract) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(contract.creator.id.eq(creatorId));
        where.and(contract.signatureRequestedAt.isNotNull());

        // ALL이어도 상태 조건을 빼지 않는다 — 스튜디오의 ALL은 「전량」이 아니라 「도착한 것 전량」이다.
        Set<ContractStatus> statuses =
                tab == null ? ContractStatus.RECEIVED_BY_CREATOR : tab.getStatuses();
        where.and(contract.status.in(statuses));

        // 브랜드명은 스냅샷이 아니라 market 조인으로 본다 — 계약에 브랜드명 컬럼이 없다(설계서 2-1).
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            where.and(contract.title.containsIgnoreCase(trimmed)
                    .or(contract.market.marketName.containsIgnoreCase(trimmed)));
        }
        return where;
    }

    /** 정렬 키 — NULL이 뒤로 가도록 치환된 식. 목록 정렬과 이웃 비교가 이 하나를 공유한다. */
    private DateTimeExpression<LocalDateTime> sortKey(CreatorContractSortType sort, QContract contract) {
        return switch (sort) {
            // 도착한 계약은 signature_requested_at이 반드시 차 있으므로 치환이 걸릴 일이 없다.
            case RECEIVED_DESC -> contract.signatureRequestedAt;
            case DEADLINE_ASC -> contract.signatureDeadlineAt.coalesce(SORT_NULLS_LAST);
            case START_AT_ASC -> contract.groupBuyStartAt.coalesce(SORT_NULLS_LAST);
        };
    }

    private LocalDateTime sortKeyValue(CreatorContractSortType sort, Contract contract) {
        LocalDateTime raw = switch (sort) {
            case RECEIVED_DESC -> contract.getSignatureRequestedAt();
            case DEADLINE_ASC -> contract.getSignatureDeadlineAt();
            case START_AT_ASC -> contract.getGroupBuyStartAt();
        };
        return raw == null ? SORT_NULLS_LAST : raw;
    }

    /** 받은 순만 내림차순이다. 나머지 둘은 「빠른 것부터」라 오름차순이다. */
    private boolean isKeyDescending(CreatorContractSortType sort) {
        return sort == CreatorContractSortType.RECEIVED_DESC;
    }

    /**
     * @param reversed 이웃 조회에서 목록 순서를 뒤집어야 할 때(이전 1건) true.
     *                 같은 식을 반대로 정렬해 LIMIT 1로 붙잡는다.
     */
    private OrderSpecifier<?>[] orderOfCreator(CreatorContractSortType sort, QContract contract, boolean reversed) {
        DateTimeExpression<LocalDateTime> key = sortKey(sort, contract);
        boolean desc = isKeyDescending(sort) != reversed;
        // id는 목록에서 항상 내림차순이므로 뒤집힌 방향에서는 오름차순이 된다.
        return new OrderSpecifier<?>[]{
                desc ? key.desc() : key.asc(),
                reversed ? contract.id.asc() : contract.id.desc()
        };
    }

    /**
     * 정렬 키 + id 2단 튜플 비교. 동률 키에서도 이웃이 건너뛰어지지 않도록 tie-break 컬럼까지
     * 비교에 넣는다 — 같은 시각에 발송된 계약 두 건은 서로의 이웃이어야 한다.
     */
    private BooleanExpression neighborBoundary(CreatorContractSortType sort, QContract contract,
                                               Contract current, boolean forward) {
        DateTimeExpression<LocalDateTime> key = sortKey(sort, contract);
        LocalDateTime currentKey = sortKeyValue(sort, current);
        boolean desc = isKeyDescending(sort);

        // 목록 순서상 「뒤」는 내림차순 키에서는 더 작은 값, 오름차순 키에서는 더 큰 값이다.
        BooleanExpression keyPastCurrent = forward == desc ? key.lt(currentKey) : key.gt(currentKey);
        // id는 항상 내림차순이라 「뒤」는 더 작은 id다.
        BooleanExpression idPastCurrent = forward
                ? contract.id.lt(current.getId())
                : contract.id.gt(current.getId());

        return keyPastCurrent.or(key.eq(currentKey).and(idPastCurrent));
    }
}
