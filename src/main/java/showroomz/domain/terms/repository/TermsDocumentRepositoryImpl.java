package showroomz.domain.terms.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static showroomz.domain.terms.entity.QTermsDocument.termsDocument;
import static showroomz.domain.terms.entity.QTermsVersion.termsVersion;

@RequiredArgsConstructor
public class TermsDocumentRepositoryImpl implements TermsDocumentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<TermsDocument> findAdminDocumentList(TermsType type, String keyword, Pageable pageable) {
        List<TermsDocument> content = queryFactory
                .selectFrom(termsDocument)
                .where(
                        eqType(type),
                        containsKeyword(keyword)
                )
                .orderBy(defaultOrder())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(termsDocument.count())
                .from(termsDocument)
                .where(
                        eqType(type),
                        containsKeyword(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Map<TermsType, Long> countByTypeGroup(String keyword) {
        List<Tuple> rows = queryFactory
                .select(termsDocument.type, termsDocument.count())
                .from(termsDocument)
                .where(containsKeyword(keyword))
                .groupBy(termsDocument.type)
                .fetch();

        Map<TermsType, Long> counts = new EnumMap<>(TermsType.class);
        for (Tuple row : rows) {
            TermsType rowType = row.get(termsDocument.type);
            Long count = row.get(termsDocument.count());
            counts.put(rowType, count == null ? 0L : count);
        }
        return counts;
    }

    /**
     * 시행 예정 문서 = 아직 시행중 버전이 없고 시행 예정 버전만 있는 문서다.
     * 시행중 문서에 개정 버전을 얹은 경우는 목록 표시 버전이 시행중이므로 여기 세지 않는다 (기획 §21-3).
     */
    @Override
    public long countScheduledDocuments(TermsType type, String keyword) {
        Long count = queryFactory
                .select(termsDocument.count())
                .from(termsDocument)
                .where(
                        eqType(type),
                        containsKeyword(keyword),
                        termsDocument.superseded.isFalse(),
                        hasVersionWithStatus(TermsVersionStatus.SCHEDULED),
                        hasVersionWithStatus(TermsVersionStatus.EFFECTIVE).not()
                )
                .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public long countSupersededDocuments(TermsType type, String keyword) {
        Long count = queryFactory
                .select(termsDocument.count())
                .from(termsDocument)
                .where(
                        eqType(type),
                        containsKeyword(keyword),
                        termsDocument.superseded.isTrue()
                )
                .fetchOne();
        return count == null ? 0L : count;
    }

    private BooleanExpression hasVersionWithStatus(TermsVersionStatus status) {
        return JPAExpressions
                .selectOne()
                .from(termsVersion)
                .where(
                        termsVersion.document.eq(termsDocument),
                        termsVersion.status.eq(status)
                )
                .exists();
    }

    /** 유형(탭 순서) → 등록 순. 목록에 정렬 컨트롤을 두지 않으므로 순서를 고정한다 (기획 §21-3) */
    private OrderSpecifier<?>[] defaultOrder() {
        return new OrderSpecifier<?>[]{
                typeOrder().asc(),
                termsDocument.id.asc()
        };
    }

    /** 유형은 enum 이름으로 저장되므로 선언 순서를 SQL 정렬 값으로 환산한다 */
    private NumberExpression<Integer> typeOrder() {
        TermsType[] types = TermsType.values();
        CaseBuilder.Cases<Integer, NumberExpression<Integer>> cases =
                new CaseBuilder().when(termsDocument.type.eq(types[0])).then(0);
        for (int i = 1; i < types.length; i++) {
            cases = cases.when(termsDocument.type.eq(types[i])).then(i);
        }
        return cases.otherwise(types.length);
    }

    private BooleanExpression eqType(TermsType type) {
        return type == null ? null : termsDocument.type.eq(type);
    }

    /** 검색은 문서명 단일 대상이다 (기획 §21-3) */
    private BooleanExpression containsKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return termsDocument.name.containsIgnoreCase(keyword.trim());
    }
}
