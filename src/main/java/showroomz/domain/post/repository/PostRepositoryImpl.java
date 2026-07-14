package showroomz.domain.post.repository;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.post.entity.Post;

import java.util.List;

import static showroomz.domain.post.entity.QPost.post;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findDisplayedPosts(Pageable pageable) {
        List<Post> content = queryFactory
                .selectFrom(post)
                .where(post.isDisplay.eq(true))
                .orderBy(post.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(post.isDisplay.eq(true));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<Post> findDisplayedPostsByCreatorId(Long creatorId, Pageable pageable) {
        List<Post> content = queryFactory
                .selectFrom(post)
                .where(
                        post.creator.id.eq(creatorId),
                        post.isDisplay.eq(true)
                )
                .orderBy(post.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.creator.id.eq(creatorId),
                        post.isDisplay.eq(true)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<Post> findDisplayedPostsByCreatorIds(List<Long> creatorIds, Pageable pageable) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .where(
                        post.creator.id.in(creatorIds),
                        post.isDisplay.eq(true)
                )
                .orderBy(post.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.creator.id.in(creatorIds),
                        post.isDisplay.eq(true)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
