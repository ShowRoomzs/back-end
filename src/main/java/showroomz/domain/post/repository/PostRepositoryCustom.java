package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.util.List;

public interface PostRepositoryCustom {

    /** 소비자 전체 피드 — 게시중만 */
    Page<Post> findDisplayedPosts(Pageable pageable);

    /** 소비자 쇼룸 피드 — 게시중만 */
    Page<Post> findDisplayedPostsByCreatorId(Long creatorId, Pageable pageable);

    Page<Post> findDisplayedPostsByCreatorIds(List<Long> creatorIds, Pageable pageable);

    /**
     * 스튜디오 목록 — 탭(전체·게시중·노출중지·작성중)에 대응한다.
     *
     * @param status {@code null}이면 전체 탭. 어느 탭에서도 삭제 게시물은 보이지 않는다(§24-6)
     */
    Page<Post> findStudioPosts(Long creatorId, PostStatus status, Pageable pageable);

    /**
     * 운영자 콘솔 목록 — <b>삭제·보관분을 포함</b>한다.
     *
     * <p>스튜디오 목록과 굳이 나눠 둔 이유가 여기 있다. §24-6은 삭제된 게시물을 "운영자 콘솔에서만"
     * 조회되게 하라고 요구하므로, 삭제분이 새는 실수를 막으려면 두 경로의 조건이 애초에 달라야 한다.
     *
     * @param creatorId {@code null}이면 전체 쇼룸
     * @param status    {@code null}이면 전체 상태
     */
    Page<Post> findAdminPosts(Long creatorId, PostStatus status, Pageable pageable);
}
