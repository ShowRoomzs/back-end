package showroomz.domain.post.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.LikedPostSort;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostType;

import java.time.LocalDateTime;
import java.util.List;

import static showroomz.domain.groupbuy.entity.QGroupBuy.groupBuy;
import static showroomz.domain.groupbuy.entity.QGroupBuyPost.groupBuyPost;
import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;
import static showroomz.domain.post.entity.QPost.post;
import static showroomz.domain.post.entity.QPostLike.postLike;

@Repository
@RequiredArgsConstructor
public class PostLikeRepositoryImpl implements PostLikeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 좋아요 목록.
     *
     * <p>쇼룸(크리에이터)과 그 계정을 함께 읽는다 — 카드마다 쇼룸명·프로필이 붙는데 지연 로딩에
     * 맡기면 한 페이지에 쿼리가 페이지 크기만큼 더 나간다. 컬렉션이 아니라 {@code ManyToOne}이라
     * 페이징과 같이 써도 안전하다.
     */
    @Override
    public Page<Post> findLikedPostsByUserId(Long userId, LikedPostSort sort, Pageable pageable) {
        // 진행 중 공구 판정에 공구 테이블이 필요하다 — 일반 게시물은 확장 행이 없어 LEFT JOIN이다(1:1이라 행이 늘지 않는다)
        List<Post> content = queryFactory
                .selectFrom(post)
                .join(postLike).on(postLike.post.eq(post))
                .join(post.creator, creator).fetchJoin()
                .join(creator.user, users).fetchJoin()
                .leftJoin(groupBuyPost).on(groupBuyPost.postId.eq(post.id))
                .leftJoin(groupBuyPost.groupBuy, groupBuy)
                .where(
                        postLike.user.id.eq(userId),
                        post.status.eq(PostStatus.PUBLISHED)
                )
                .orderBy(orderOf(sort, LocalDateTime.now()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(postLike.count())
                .from(postLike)
                .join(postLike.post, post)
                .where(
                        postLike.user.id.eq(userId),
                        post.status.eq(PostStatus.PUBLISHED)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 어떤 기준으로 고르든 <b>좋아요한 시각</b>을 마지막 키로 붙인다. 좋아요 수나 게시물 타입은
     * 값이 겹치는 게 정상이라 그것만으로 정렬하면 페이지 경계에서 순서가 흔들려 같은 게시물이
     * 두 번 보이거나 건너뛰어진다.
     */
    private static OrderSpecifier<?>[] orderOf(LikedPostSort sort, LocalDateTime now) {
        LikedPostSort resolved = sort != null ? sort : LikedPostSort.DEFAULT;

        return switch (resolved) {
            case LIKED_OLDEST -> new OrderSpecifier<?>[]{postLike.createdAt.asc()};
            case MOST_LIKED -> new OrderSpecifier<?>[]{post.likeCount.desc(), postLike.createdAt.desc()};
            case GROUP_BUY_FIRST -> new OrderSpecifier<?>[]{ongoingGroupBuyRank(now).asc(), postLike.createdAt.desc()};
            case DEFAULT -> new OrderSpecifier<?>[]{postLike.createdAt.desc()};
        };
    }

    /**
     * 진행 중 공구를 앞으로 보내는 정렬 키 — 0이 진행 중 공구, 1이 그 외(공구 게시물 설계 6-1). 마감 게시물(종료 3일 이내)은
     * 일반 게시물과 같은 줄에서 좋아요한 시각으로 섞인다 — 시안이 「진행 중 공구를 위로 모아서」다.
     */
    private static NumberExpression<Integer> ongoingGroupBuyRank(LocalDateTime now) {
        return new CaseBuilder()
                .when(post.postType.eq(PostType.GROUP_BUY)
                        .and(groupBuy.status.in(GroupBuyStatus.SELLING))
                        .and(groupBuy.endAt.gt(now))).then(0)
                .otherwise(1);
    }
}
