package showroomz.domain.notice.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.type.NoticeStatus;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static showroomz.domain.notice.entity.QNotice.notice;

@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Notice> findAdminNoticeList(NoticeStatus status, String keyword, Pageable pageable) {
        List<Notice> content = queryFactory
                .selectFrom(notice)
                .where(
                        eqStatus(status),
                        containsKeyword(keyword)
                )
                .orderBy(defaultOrder())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(notice.count())
                .from(notice)
                .where(
                        eqStatus(status),
                        containsKeyword(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Map<NoticeStatus, Long> countByStatusGroup(String keyword) {
        List<Tuple> rows = queryFactory
                .select(notice.status, notice.count())
                .from(notice)
                .where(containsKeyword(keyword))
                .groupBy(notice.status)
                .fetch();

        Map<NoticeStatus, Long> counts = new EnumMap<>(NoticeStatus.class);
        for (Tuple row : rows) {
            NoticeStatus rowStatus = row.get(notice.status);
            Long count = row.get(notice.count());
            counts.put(rowStatus, count == null ? 0L : count);
        }
        return counts;
    }

    @Override
    public long countPinned(NoticeStatus status, String keyword) {
        Long count = queryFactory
                .select(notice.count())
                .from(notice)
                .where(
                        notice.pinned.isTrue(),
                        eqStatus(status),
                        containsKeyword(keyword)
                )
                .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public void changeStatus(Long noticeId, NoticeStatus status, LocalDateTime endedAt) {
        entityManager.flush();

        JPAUpdateClause update = queryFactory.update(notice)
                .set(notice.status, status);

        if (endedAt == null) {
            update.setNull(notice.endedAt);
        } else {
            update.set(notice.endedAt, endedAt);
        }

        update.where(notice.id.eq(noticeId)).execute();

        entityManager.clear();
    }

    /** 중요 고정 상단 → 등록일 최신순 → ID 순 (기획 §20-3) */
    private OrderSpecifier<?>[] defaultOrder() {
        return new OrderSpecifier<?>[]{
                notice.pinned.desc(),
                notice.createdAt.desc(),
                notice.id.desc()
        };
    }

    private BooleanExpression eqStatus(NoticeStatus status) {
        return status == null ? null : notice.status.eq(status);
    }

    /** 검색은 제목 단일 대상이다 (기획 §20-3) */
    private BooleanExpression containsKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return notice.title.containsIgnoreCase(keyword.trim());
    }
}
