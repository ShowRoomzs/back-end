package showroomz.domain.productannouncement.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import showroomz.domain.productannouncement.entity.ProductAnnouncement;
import showroomz.domain.productannouncement.entity.QProductAnnouncement;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductAnnouncementRepositoryImpl implements ProductAnnouncementRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ProductAnnouncement> search(
            Pageable pageable,
            String keyword,
            String category,
            ProductAnnouncementDisplayStatus displayStatus,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    ) {
        QProductAnnouncement pa = QProductAnnouncement.productAnnouncement;
        BooleanBuilder where = new BooleanBuilder();

        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            where.and(
                    pa.title.containsIgnoreCase(k)
                            .or(pa.content.containsIgnoreCase(k))
            );
        }
        if (StringUtils.hasText(category)) {
            where.and(pa.category.eq(category.trim()));
        }
        if (displayStatus != null) {
            where.and(pa.displayStatus.eq(displayStatus));
        }
        if (createdFrom != null) {
            where.and(pa.createdAt.goe(createdFrom));
        }
        if (createdTo != null) {
            where.and(pa.createdAt.loe(createdTo));
        }

        JPAQuery<ProductAnnouncement> query = queryFactory
                .selectFrom(pa)
                .where(where)
                .orderBy(pa.createdAt.desc());

        Long total = queryFactory
                .select(pa.count())
                .from(pa)
                .where(where)
                .fetchOne();
        long totalCount = total == null ? 0L : total;

        if (totalCount == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<ProductAnnouncement> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, totalCount);
    }
}
