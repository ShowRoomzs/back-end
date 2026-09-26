package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 공구 게시물의 노출 투영 — {@code post.status}를 파생 상태에 맞춘다(31 설계 0-3 · 2-9 · 공구 게시물 설계 4-1).
 *
 * <pre>
 * post.status = PUBLISHED  ⇔  of(post, groupBuy) == EXPOSED
 *                              ∨ (of(post, groupBuy) == CLOSED
 *                                 ∧ post.publishedAt != null ∧ !숨김 ∧ closeType != SUSPENDED
 *                                 ∧ now < endedAt + CLOSED_POST_RETENTION)
 *               DRAFT      그 밖의 전부
 * </pre>
 *
 * <p>소비자 쪽 코드(상세·좋아요·신고)는 {@code post.status == PUBLISHED}로 노출을 판정한다. 이 값을 투영하지 않으면
 * 오픈된 공구의 게시물이 소비자에게 404다. {@code SUSPENDED}를 쓰지 않는다 — 7일 뒤 원문이 영구 삭제된다.
 *
 * <p><b>끝난 공구의 게시물은 종료 후 3일 동안 마감 상태로 남는다</b>(2026-09-26 확정). 한 번이라도 노출된 게시물만
 * 남고, 숨긴 채 끝났으면 숨긴 채로, 직권 중단은 즉시 내린다(중단 사유가 게시물 자체일 수 있다 — 미결 ②).
 * 저장된 값이라 시간이 지나도 스스로 바뀌지 않으므로 3일 경과분은 스케줄러의 「마감 게시물 내리기」가 이 메서드로 내린다.
 *
 * <p>노출을 바꾸는 <b>모든 전이가 같은 트랜잭션에서</b> 부른다 — 오픈 · 종료(스케줄러), 조기 마감·중단 승인 ·
 * 직권 중단 집행 · 숨김·해제(어드민) · 마감 게시물 내리기(스케줄러). 직권 중단 통지·철회와 스튜디오 쓰기는 노출을 바꾸지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyPostExposure {

    /** 마감 게시물을 소비자에게 남기는 기간 — {@code ended_at} 기준 72시간(미결 ⑩ 기본값). */
    public static final Duration CLOSED_POST_RETENTION = Duration.ofHours(72);

    private final GroupBuyPostRepository postRepository;

    /**
     * 전이 직후 호출한다. 게시물이 아직 없으면 할 일이 없다.
     *
     * @return 노출 상태가 바뀌었는지
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean sync(GroupBuy groupBuy, LocalDateTime now) {
        return postRepository.findByGroupBuyId(groupBuy.getId())
                .map(post -> sync(groupBuy, post, now))
                .orElse(false);
    }

    /** @return 노출 상태가 바뀌었는지 */
    public static boolean sync(GroupBuy groupBuy, GroupBuyPost groupBuyPost, LocalDateTime now) {
        Post post = groupBuyPost.getPost();
        PostStatus target = isPublished(groupBuy, groupBuyPost, now) ? PostStatus.PUBLISHED : PostStatus.DRAFT;
        if (post.getStatus() == target) {
            return false;
        }
        post.changeGroupBuyExposure(target, now);
        return true;
    }

    /** 투영식 — 노출중이거나, 종료 3일 이내의 마감 게시물이다. */
    static boolean isPublished(GroupBuy groupBuy, GroupBuyPost groupBuyPost, LocalDateTime now) {
        GroupBuyPostStatus status = GroupBuyPostStatus.of(groupBuyPost, groupBuy);
        if (status == GroupBuyPostStatus.EXPOSED) {
            return true;
        }
        return status == GroupBuyPostStatus.CLOSED && retainsClosed(groupBuy, groupBuyPost, now);
    }

    /**
     * 끝난 공구의 게시물을 아직 남기는가.
     *
     * <ul>
     *   <li>{@code publishedAt != null} — 승인 전에 끝난 공구의 게시물은 소비자가 본 적이 없다</li>
     *   <li>숨김 — 종료가 숨김 해제가 되면 안 된다</li>
     *   <li>직권 중단 — 즉시 내린다(미결 ②)</li>
     *   <li>{@code endedAt} — 조기 마감·중단은 {@code end_at}보다 이르다. 실제로 끝난 시각 기준이다</li>
     * </ul>
     */
    private static boolean retainsClosed(GroupBuy groupBuy, GroupBuyPost groupBuyPost, LocalDateTime now) {
        LocalDateTime endedAt = groupBuy.getEndedAt();
        return groupBuyPost.getPost().getPublishedAt() != null
                && !groupBuyPost.isHidden()
                && groupBuy.getCloseType() != GroupBuyCloseType.SUSPENDED
                && endedAt != null
                && now.isBefore(endedAt.plus(CLOSED_POST_RETENTION));
    }
}
