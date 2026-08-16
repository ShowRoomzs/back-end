package showroomz.domain.post.repository;

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

import static showroomz.domain.post.entity.QPost.post;
import static showroomz.domain.post.entity.QPostLike.postLike;

@Repository
@RequiredArgsConstructor
public class PostLikeRepositoryImpl implements PostLikeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findLikedPostsByUserId(Long userId, Pageable pageable) {
        List<Post> content = queryFactory
                .select(postLike.post)
                .from(postLike)
                .join(postLike.post, post)
                .where(
                        postLike.user.id.eq(userId),
                        post.status.eq(PostStatus.PUBLISHED)
                )
                .orderBy(postLike.createdAt.desc())
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
}
