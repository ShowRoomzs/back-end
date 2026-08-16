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
     * C1 추천 — 팔로우하지 않은 쇼룸의 게시중 게시물 (§C1 "회원님을 위한 추천" · 팔로잉 0 발견 피드).
     *
     * <p>팔로잉 피드의 <b>여집합</b>이라 두 목록에 같은 게시물이 겹치지 않는다. 겹치면 "새 게시물을
     * 모두 확인했어요" 구분선 아래에 방금 본 게시물이 다시 나와 구분 자체가 무의미해진다.
     *
     * @param excludedCreatorIds 제외할 쇼룸 — 팔로우 중인 쇼룸과 본인 쇼룸. 비어 있으면 전체가 대상이다
     */
    Page<Post> findRecommendedPosts(List<Long> excludedCreatorIds, Pageable pageable);

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
