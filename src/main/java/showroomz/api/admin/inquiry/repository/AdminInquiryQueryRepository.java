package showroomz.api.admin.inquiry.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.admin.inquiry.type.AdminInquiryStatusFilter;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.entity.QOneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.member.user.entity.QUsers;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AdminInquiryQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QOneToOneInquiry inquiry = QOneToOneInquiry.oneToOneInquiry;
    private static final QUsers user = QUsers.users;

    /**
     * 목록 (§17-2) — 접수일시 최신순, 유형 필터 + 작성자·문의 내용 통합 검색.
     *
     * @param userId 작성한 소비자 — 소비자 상세 「1:1 문의 전체 보기」 · 진행 중 칩 「열린 문의」 진입(56 C1). null 이면 전체
     */
    public Page<OneToOneInquiry> search(AdminInquiryStatusFilter statusFilter, CsCategory type,
                                        String keyword, Long userId, Pageable pageable) {
        BooleanBuilder where = createWhere(statusFilter, type, keyword, userId);

        List<OneToOneInquiry> content = queryFactory
                .selectFrom(inquiry)
                .join(inquiry.user, user).fetchJoin()
                .where(where)
                .orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .join(inquiry.user, user)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /** 상세의 ‹ 이전 · 다음 › — 현재 탭·필터의 목록 순서를 따른다 (§17-3) */
    public List<Long> findOrderedIds(AdminInquiryStatusFilter statusFilter, CsCategory type, String keyword,
                                     Long userId) {
        return queryFactory
                .select(inquiry.id)
                .from(inquiry)
                .join(inquiry.user, user)
                .where(createWhere(statusFilter, type, keyword, userId))
                .orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
                .fetch();
    }

    /** 탭 건수 — 상태 조건만 제외하고 유형·검색어 · 소비자는 그대로 반영한다 */
    public Map<InquiryStatus, Long> countByStatus(CsCategory type, String keyword, Long userId) {
        List<Tuple> rows = queryFactory
                .select(inquiry.status, inquiry.count())
                .from(inquiry)
                .join(inquiry.user, user)
                .where(createWhere(AdminInquiryStatusFilter.ALL, type, keyword, userId))
                .groupBy(inquiry.status)
                .fetch();

        Map<InquiryStatus, Long> counts = new EnumMap<>(InquiryStatus.class);
        for (InquiryStatus status : InquiryStatus.values()) {
            counts.put(status, 0L);
        }
        for (Tuple row : rows) {
            InquiryStatus status = row.get(inquiry.status);
            Long count = row.get(inquiry.count());
            if (status != null) {
                counts.put(status, count != null ? count : 0L);
            }
        }
        return counts;
    }

    private BooleanBuilder createWhere(AdminInquiryStatusFilter statusFilter, CsCategory type, String keyword,
                                       Long userId) {
        BooleanBuilder where = new BooleanBuilder();

        if (userId != null) {
            where.and(inquiry.user.id.eq(userId));
        }
        if (statusFilter != null && statusFilter.getStatus() != null) {
            where.and(inquiry.status.eq(statusFilter.getStatus()));
        }
        if (type != null) {
            where.and(inquiry.type.eq(type));
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            where.and(inquiry.content.containsIgnoreCase(trimmed)
                    .or(user.name.containsIgnoreCase(trimmed))
                    .or(user.nickname.containsIgnoreCase(trimmed)));
        }
        return where;
    }
}
