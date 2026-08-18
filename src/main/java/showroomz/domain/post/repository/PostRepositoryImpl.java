package showroomz.domain.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.util.List;

import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;
import static showroomz.domain.post.entity.QPost.post;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findDisplayedPosts(Pageable pageable) {
        return findPublished(null, pageable);
    }

    @Override
    public Page<Post> findDisplayedPostsByCreatorId(Long creatorId, Pageable pageable) {
        return findPublished(post.creator.id.eq(creatorId), pageable);
    }

    @Override
    public Page<Post> findDisplayedPostsByCreatorIds(List<Long> creatorIds, Pageable pageable) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return findPublished(post.creator.id.in(creatorIds), pageable);
    }

    @Override
    public Page<Post> findRecommendedPosts(List<Long> excludedCreatorIds, Pageable pageable) {
        if (excludedCreatorIds == null || excludedCreatorIds.isEmpty()) {
            return findPublished(null, pageable);
        }
        return findPublished(post.creator.id.notIn(excludedCreatorIds), pageable);
    }

    @Override
    public Page<Post> findStudioPosts(Long creatorId, PostStatus status, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder()
                .and(post.creator.id.eq(creatorId))
                // 삭제 게시물은 어느 탭에도 나타나지 않는다 — 운영자 콘솔에서만 조회된다(§24-6)
                .and(post.status.ne(PostStatus.DELETED));
        if (status != null) {
            where.and(post.status.eq(status));
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .where(where)
                .orderBy(post.createdAt.desc(), post.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<Post> findAdminPosts(Long creatorId, PostStatus status, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        if (creatorId != null) {
            where.and(post.creator.id.eq(creatorId));
        }
        if (status != null) {
            where.and(post.status.eq(status));
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .where(where)
                .orderBy(post.createdAt.desc(), post.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 소비자에게 나가는 목록은 예외 없이 게시중만이다.
     *
     * <p>예전에는 {@code isDisplay = true} 하나로 걸렀는데, 그 조건은 작성중(임시저장)과 노출 중지를
     * 구분하지 못했다. 상태가 5종으로 갈린 지금은 <b>게시중과의 일치</b>로만 판정한다 — 부정 조건
     * ("삭제가 아닌")으로 쓰면 상태가 늘어날 때마다 소비자 화면에 새 상태가 새어 나간다.
     *
     * <p>쇼룸(크리에이터)과 그 계정을 함께 읽는다 — 카드마다 쇼룸명·프로필이 붙는데 지연 로딩에
     * 맡기면 한 페이지에 쿼리가 쇼룸 수만큼 더 나가고, 쇼룸명이 아직 없는 계정은 닉네임을 읽느라
     * 한 번 더 나간다. 컬렉션이 아니라 {@code ManyToOne}이라 페이징과 같이 써도 안전하다
     * (좋아요 목록 쿼리와 같은 방식이다).
     */
    private Page<Post> findPublished(BooleanExpression extraCondition, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder(post.status.eq(PostStatus.PUBLISHED));
        if (extraCondition != null) {
            where.and(extraCondition);
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.creator, creator).fetchJoin()
                .join(creator.user, users).fetchJoin()
                .where(where)
                .orderBy(post.publishedAt.desc(), post.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
