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
import static showroomz.domain.post.entity.QPostWishlist.postWishlist;

@Repository
@RequiredArgsConstructor
public class PostWishlistRepositoryImpl implements PostWishlistRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findWishlistedPostsByUserId(Long userId, Pageable pageable) {
        List<Post> content = queryFactory
                .select(postWishlist.post)
                .from(postWishlist)
                .join(postWishlist.post, post).fetchJoin()
                .where(
                        postWishlist.user.id.eq(userId),
                        post.isDisplay.eq(true)
                )
                .orderBy(postWishlist.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(postWishlist.count())
                .from(postWishlist)
                .join(postWishlist.post, post)
                .where(
                        postWishlist.user.id.eq(userId),
                        post.isDisplay.eq(true)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
