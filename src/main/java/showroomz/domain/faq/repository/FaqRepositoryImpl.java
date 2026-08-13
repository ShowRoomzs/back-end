package showroomz.domain.faq.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.faq.entity.Faq;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static showroomz.domain.faq.entity.QFaq.faq;

@RequiredArgsConstructor
public class FaqRepositoryImpl implements FaqRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Faq> findAdminFaqList(CsCategory category, String keyword, Pageable pageable) {
        List<Faq> content = queryFactory
                .selectFrom(faq)
                .where(
                        eqCategory(category),
                        containsKeyword(keyword)
                )
                .orderBy(defaultOrder())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(faq.count())
                .from(faq)
                .where(
                        eqCategory(category),
                        containsKeyword(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<Faq> findAppFaqList(CsCategory category, String keyword) {
        return queryFactory
                .selectFrom(faq)
                .where(
                        eqCategory(category),
                        containsKeyword(keyword)
                )
                .orderBy(defaultOrder())
                .fetch();
    }

    @Override
    public Map<CsCategory, Long> countByCategoryGroup(String keyword) {
        List<Tuple> rows = queryFactory
                .select(faq.category, faq.count())
                .from(faq)
                .where(containsKeyword(keyword))
                .groupBy(faq.category)
                .fetch();

        Map<CsCategory, Long> counts = new EnumMap<>(CsCategory.class);
        for (Tuple row : rows) {
            CsCategory category = row.get(faq.category);
            Long count = row.get(faq.count());
            counts.put(category, count == null ? 0L : count);
        }
        return counts;
    }

    @Override
    public void shiftOrderUpForInsert(CsCategory category) {
        entityManager.flush();

        queryFactory.update(faq)
                .set(faq.displayOrder, faq.displayOrder.add(1))
                .where(faq.category.eq(category))
                .execute();

        entityManager.clear();
    }

    @Override
    public void shiftOrderDownAfterDelete(CsCategory category, Integer deletedOrder) {
        entityManager.flush();

        queryFactory.update(faq)
                .set(faq.displayOrder, faq.displayOrder.subtract(1))
                .where(
                        faq.category.eq(category),
                        faq.displayOrder.gt(deletedOrder)
                )
                .execute();

        entityManager.clear();
    }

    /** 카테고리(노출 순서) → 카테고리 내 노출 순서 → ID 순 */
    private OrderSpecifier<?>[] defaultOrder() {
        return new OrderSpecifier<?>[]{
                categoryOrder().asc(),
                faq.displayOrder.asc(),
                faq.id.asc()
        };
    }

    /** 카테고리는 enum 이름으로 저장되므로 선언 순서를 SQL 정렬 값으로 환산한다 */
    private NumberExpression<Integer> categoryOrder() {
        CsCategory[] categories = CsCategory.values();
        CaseBuilder.Cases<Integer, NumberExpression<Integer>> cases =
                new CaseBuilder().when(faq.category.eq(categories[0])).then(0);
        for (int i = 1; i < categories.length; i++) {
            cases = cases.when(faq.category.eq(categories[i])).then(i);
        }
        return cases.otherwise(categories.length);
    }

    private BooleanExpression eqCategory(CsCategory category) {
        return category != null ? faq.category.eq(category) : null;
    }

    /** 검색은 질문 단일 대상이다 (기획 §19-2) */
    private BooleanExpression containsKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return faq.question.containsIgnoreCase(keyword.trim());
    }
}
