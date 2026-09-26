package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.time.LocalDateTime;

/**
 * 공구 게시물의 노출 투영 — {@code post.status}를 파생 상태에 맞춘다(31 설계 0-3 · 2-9).
 *
 * <pre>
 * post.status = PUBLISHED  ⇔  GroupBuyPostStatus.of(post, groupBuy) == EXPOSED
 *               DRAFT      그 밖의 전부
 * </pre>
 *
 * <p>소비자 쪽 코드(상세·좋아요·신고)는 {@code post.status == PUBLISHED}로 노출을 판정한다. 이 값을 투영하지 않으면
 * 오픈된 공구의 게시물이 소비자에게 404다. {@code SUSPENDED}를 쓰지 않는다 — 7일 뒤 원문이 영구 삭제된다.
 *
 * <p>노출을 바꾸는 <b>모든 전이가 같은 트랜잭션에서</b> 부른다 — 오픈 · 종료(스케줄러), 조기 마감·중단 승인 ·
 * 직권 중단 집행 · 숨김·해제(어드민). 직권 중단 통지·철회와 스튜디오 쓰기는 노출을 바꾸지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyPostExposure {

    private final GroupBuyPostRepository postRepository;

    /** 전이 직후 호출한다. 게시물이 아직 없으면 할 일이 없다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(GroupBuy groupBuy, LocalDateTime now) {
        postRepository.findByGroupBuyId(groupBuy.getId()).ifPresent(post -> sync(groupBuy, post, now));
    }

    public static void sync(GroupBuy groupBuy, GroupBuyPost groupBuyPost, LocalDateTime now) {
        Post post = groupBuyPost.getPost();
        PostStatus target = GroupBuyPostStatus.of(groupBuyPost, groupBuy) == GroupBuyPostStatus.EXPOSED
                ? PostStatus.PUBLISHED : PostStatus.DRAFT;
        if (post.getStatus() != target) {
            post.changeGroupBuyExposure(target, now);
        }
    }
}
