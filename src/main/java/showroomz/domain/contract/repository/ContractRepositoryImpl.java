package showroomz.domain.contract.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.QContract;
import showroomz.domain.contract.type.ContractSortType;
import showroomz.domain.contract.type.ContractTab;
import showroomz.domain.member.creator.entity.QCreator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
}
