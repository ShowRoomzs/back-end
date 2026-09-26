package showroomz.domain.post.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.entity.Post;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 공구 게시물 좋아요 — 노출중 ∧ 종료 시각 전만 받는다. 마감 3일 동안은 잠기고(해제만), 품절은 막지 않는다(공구 게시물 설계 4-4).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("공구 게시물 좋아요 정책 — canLike")
class GroupBuyPostPolicyCanLikeTest {

    private static final long POST_ID = 7L;

    @Mock
    private GroupBuyPostRepository groupBuyPostRepository;
    @InjectMocks
    private GroupBuyPostPolicy policy;

    @Test
    @DisplayName("진행 중 · 승인 · 숨김 아님 · 종료 전 → 받는다")
    void exposedAccepts() {
        assertThat(canLike(groupBuy(GroupBuyStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1)), approved(false)))
                .isTrue();
    }

    @Test
    @DisplayName("중단 예정도 아직 팔리므로 받는다")
    void suspensionScheduledAccepts() {
        assertThat(canLike(groupBuy(GroupBuyStatus.SUSPENSION_SCHEDULED, LocalDateTime.now().plusDays(1)),
                approved(false))).isTrue();
    }

    @Test
    @DisplayName("종료 시각이 지났는데 아직 IN_PROGRESS(스케줄러 지연) → 거절")
    void endAtPassedRejects() {
        assertThat(canLike(groupBuy(GroupBuyStatus.IN_PROGRESS, LocalDateTime.now().minusSeconds(1)), approved(false)))
                .isFalse();
    }

    @Test
    @DisplayName("종결(마감 3일 보존 중) → 거절")
    void closedRejects() {
        GroupBuy ended = GroupBuy.builder().id(1L).status(GroupBuyStatus.ENDED).closeType(GroupBuyCloseType.COMPLETED)
                .startAt(LocalDateTime.now().minusDays(7)).endAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().minusHours(1)).build();

        assertThat(canLike(ended, approved(false))).isFalse();
    }

    @Test
    @DisplayName("숨김 · 준비완료(예약) → 거절")
    void hiddenOrScheduledRejects() {
        assertThat(canLike(groupBuy(GroupBuyStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1)), approved(true)))
                .isFalse();
        assertThat(canLike(groupBuy(GroupBuyStatus.READY, LocalDateTime.now().plusDays(5)), approved(false)))
                .isFalse();
    }

    @Test
    @DisplayName("확장 행이 없으면 거절")
    void missingRejects() {
        given(groupBuyPostRepository.findById(POST_ID)).willReturn(Optional.empty());

        assertThat(policy.canLike(post())).isFalse();
    }

    // ------------------------------------------------------------------ fixture

    private boolean canLike(GroupBuy groupBuy, GroupBuyPost.GroupBuyPostBuilder builder) {
        GroupBuyPost groupBuyPost = builder.groupBuy(groupBuy).post(post()).build();
        given(groupBuyPostRepository.findById(POST_ID)).willReturn(Optional.of(groupBuyPost));
        return policy.canLike(post());
    }

    private static GroupBuy groupBuy(GroupBuyStatus status, LocalDateTime endAt) {
        return GroupBuy.builder().id(1L).status(status).startAt(endAt.minusDays(7)).endAt(endAt).build();
    }

    private static GroupBuyPost.GroupBuyPostBuilder approved(boolean hidden) {
        return GroupBuyPost.builder()
                .postId(POST_ID)
                .title("제목")
                .reviewStatus(GroupBuyPostReviewStatus.APPROVED)
                .hiddenAt(hidden ? LocalDateTime.now().minusHours(1) : null);
    }

    private static Post post() {
        Post post = Post.groupBuyDraft(null, "본문");
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }
}
