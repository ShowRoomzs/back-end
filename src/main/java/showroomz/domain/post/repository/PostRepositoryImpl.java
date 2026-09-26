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
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.GroupBuyScope;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostType;

import java.time.LocalDateTime;
import java.util.List;

import static showroomz.domain.groupbuy.entity.QGroupBuy.groupBuy;
import static showroomz.domain.groupbuy.entity.QGroupBuyPost.groupBuyPost;
import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;
import static showroomz.domain.post.entity.QPost.post;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findDisplayedPosts(Pageable pageable) {
        return findPublished(null, GroupBuyScope.GENERAL_AND_ONGOING, pageable);
    }

    @Override
    public Page<Post> findDisplayedPostsByCreatorId(Long creatorId, Pageable pageable) {
        return findPublished(post.creator.id.eq(creatorId), GroupBuyScope.GENERAL_AND_CLOSED, pageable);
    }

    @Override
    public Page<Post> findDisplayedPostsByCreatorIds(List<Long> creatorIds, Pageable pageable) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return findPublished(post.creator.id.in(creatorIds), GroupBuyScope.GENERAL_AND_ONGOING, pageable);
    }

    @Override
    public Page<Post> findRecommendedPosts(List<Long> excludedCreatorIds, Pageable pageable) {
        if (excludedCreatorIds == null || excludedCreatorIds.isEmpty()) {
            return findPublished(null, GroupBuyScope.GENERAL_AND_ONGOING, pageable);
        }
        return findPublished(post.creator.id.notIn(excludedCreatorIds), GroupBuyScope.GENERAL_AND_ONGOING, pageable);
    }

    /**
     * C4 고정 섹션 — 페이징 없음. 한 쇼룸의 동시 진행 공구는 계약 수로 제한돼 작다.
     * 정렬은 공구 시작일 최신순이다(C4 남은 결정 ③).
     */
    @Override
    public List<Post> findOngoingGroupBuyPostsByCreatorId(Long creatorId) {
        BooleanBuilder where = new BooleanBuilder(post.status.eq(PostStatus.PUBLISHED))
                .and(post.creator.id.eq(creatorId))
                .and(scopeCondition(GroupBuyScope.ONGOING_ONLY, LocalDateTime.now()));

        return queryFactory
                .selectFrom(post)
                .join(post.creator, creator).fetchJoin()
                .join(creator.user, users).fetchJoin()
                .leftJoin(groupBuyPost).on(groupBuyPost.postId.eq(post.id))
                .leftJoin(groupBuyPost.groupBuy, groupBuy)
                .where(where)
                .orderBy(groupBuy.openedAt.desc(), post.id.desc())
                .fetch();
    }

    @Override
    public Page<Post> findStudioPosts(Long creatorId, PostStatus status, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder()
                .and(post.creator.id.eq(creatorId))
                // 삭제 게시물은 어느 탭에도 나타나지 않는다 — 운영자 콘솔에서만 조회된다(§24-6)
                .and(post.status.ne(PostStatus.DELETED))
                // 일반 게시물과 공구 게시물을 한 목록에 섞지 않는다(§24) — 카운트 쿼리와 같은 조건이어야 한다(§24-1)
                .and(post.postType.eq(PostType.GENERAL));
        if (status != null) {
            // 탭 하나가 상태 둘을 담는 경우가 있다 — 「노출 중지」 탭은 심사 중까지 포함한다(§24-5).
            // 탭 개수를 세는 쪽과 같은 규칙을 써야 숫자와 목록이 어긋나지 않는다.
            where.and(post.status.in(status.tabMembers()));
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
        // 공구 게시물은 일반 게시물 콘솔에 섞지 않는다 — 여기서 노출 중지를 누르면 7일 뒤 원문이 지워진다(31 설계 0-6 ③).
        BooleanBuilder where = new BooleanBuilder(post.postType.eq(PostType.GENERAL));
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
     * <p>공구 게시물은 {@link GroupBuyScope}로 범위를 정한다(공구 게시물 설계 6절). 일반 게시물은 확장 행이 없으므로
     * 공구 테이블은 반드시 LEFT JOIN이다 — 1:1이라 행이 늘지 않고, 카운트 쿼리도 같은 조인·조건을 쓴다.
     *
     * <p>쇼룸(크리에이터)과 그 계정을 함께 읽는다 — 카드마다 쇼룸명·프로필이 붙는데 지연 로딩에
     * 맡기면 한 페이지에 쿼리가 쇼룸 수만큼 더 나가고, 쇼룸명이 아직 없는 계정은 닉네임을 읽느라
     * 한 번 더 나간다. 컬렉션이 아니라 {@code ManyToOne}이라 페이징과 같이 써도 안전하다
     * (좋아요 목록 쿼리와 같은 방식이다).
     */
    private Page<Post> findPublished(BooleanExpression extraCondition, GroupBuyScope scope, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder(post.status.eq(PostStatus.PUBLISHED))
                .and(scopeCondition(scope, LocalDateTime.now()));
        if (extraCondition != null) {
            where.and(extraCondition);
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.creator, creator).fetchJoin()
                .join(creator.user, users).fetchJoin()
                .leftJoin(groupBuyPost).on(groupBuyPost.postId.eq(post.id))
                .leftJoin(groupBuyPost.groupBuy, groupBuy)
                .where(where)
                .orderBy(post.publishedAt.desc(), post.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .leftJoin(groupBuyPost).on(groupBuyPost.postId.eq(post.id))
                .leftJoin(groupBuyPost.groupBuy, groupBuy)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /** {@code groupBuyPost}·{@code groupBuy}가 LEFT JOIN된 쿼리에서만 쓴다. */
    private static BooleanExpression scopeCondition(GroupBuyScope scope, LocalDateTime now) {
        BooleanExpression general = post.postType.eq(PostType.GENERAL);
        BooleanExpression ongoing = post.postType.eq(PostType.GROUP_BUY)
                .and(groupBuy.status.in(GroupBuyStatus.SELLING))
                .and(groupBuy.endAt.gt(now));
        BooleanExpression closed = post.postType.eq(PostType.GROUP_BUY)
                .and(groupBuy.status.notIn(GroupBuyStatus.SELLING).or(groupBuy.endAt.loe(now)));
        return switch (scope) {
            case GENERAL_AND_ONGOING -> general.or(ongoing);
            case GENERAL_AND_CLOSED -> general.or(closed);
            case ONGOING_ONLY -> ongoing;
        };
    }
}
