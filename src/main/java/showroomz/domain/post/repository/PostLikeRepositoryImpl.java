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
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.LikedPostSort;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostType;

import java.util.List;

import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;
import static showroomz.domain.post.entity.QPost.post;
import static showroomz.domain.post.entity.QPostLike.postLike;

@Repository
@RequiredArgsConstructor
public class PostLikeRepositoryImpl implements PostLikeRepositoryCustom {

    /** 공구 게시물을 앞으로 보내기 위한 정렬 키 — 0이 공구, 1이 그 외 */
    private static final NumberExpression<Integer> GROUP_BUY_RANK = new CaseBuilder()
            .when(post.postType.eq(PostType.GROUP_BUY)).then(0)
            .otherwise(1);

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
        List<Post> content = queryFactory
                .selectFrom(post)
                .join(postLike).on(postLike.post.eq(post))
                .join(post.creator, creator).fetchJoin()
                .join(creator.user, users).fetchJoin()
                .where(
                        postLike.user.id.eq(userId),
                        post.status.eq(PostStatus.PUBLISHED)
                )
                .orderBy(orderOf(sort))
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
    private static OrderSpecifier<?>[] orderOf(LikedPostSort sort) {
        LikedPostSort resolved = sort != null ? sort : LikedPostSort.DEFAULT;

        return switch (resolved) {
            case LIKED_OLDEST -> new OrderSpecifier<?>[]{postLike.createdAt.asc()};
            case MOST_LIKED -> new OrderSpecifier<?>[]{post.likeCount.desc(), postLike.createdAt.desc()};
            case GROUP_BUY_FIRST -> new OrderSpecifier<?>[]{GROUP_BUY_RANK.asc(), postLike.createdAt.desc()};
            case DEFAULT -> new OrderSpecifier<?>[]{postLike.createdAt.desc()};
        };
    }
}
