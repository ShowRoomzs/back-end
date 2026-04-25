package showroomz.domain.faq.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;

import static showroomz.domain.faq.entity.QFaq.faq;

@RequiredArgsConstructor
public class FaqRepositoryImpl implements FaqRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Faq> findAdminFaqList(FaqCategory category, String keyword, Pageable pageable) {
        List<Faq> content = queryFactory
                .selectFrom(faq)
                .where(
                        eqCategory(category),
                        containsKeyword(keyword)
                )
                .orderBy(faq.displayOrder.asc(), faq.id.asc())
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

    private BooleanExpression eqCategory(FaqCategory category) {
        return category != null ? faq.category.eq(category) : null;
    }

    private BooleanExpression containsKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return faq.question.containsIgnoreCase(keyword)
                .or(faq.answer.containsIgnoreCase(keyword));
    }
}
