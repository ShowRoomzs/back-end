package showroomz.domain.groupbuy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("공구 게시물 노출 투영 — 종료 후 3일 동안 마감으로 남긴다(공구 게시물 설계 4-1)")
class GroupBuyPostExposureTest {

    private static final LocalDateTime OPENED = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final LocalDateTime ENDED = LocalDateTime.of(2026, 9, 20, 23, 59, 59);

    @Test
    @DisplayName("진행 중 · 승인 · 숨김 아님 → PUBLISHED")
    void exposedWhileSelling() {
        GroupBuyPost post = approvedPost();
        GroupBuy groupBuy = selling();

        GroupBuyPostExposure.sync(groupBuy, post, OPENED);

        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("기간 종료 직후 · 72시간 직전 → PUBLISHED 유지")
    void retainedWithinRetention() {
        GroupBuyPost post = publishedPost();
        GroupBuy groupBuy = ended(GroupBuyCloseType.COMPLETED, ENDED);

        assertThat(GroupBuyPostExposure.sync(groupBuy, post, ENDED.plusMinutes(1))).isFalse();
        assertThat(GroupBuyPostExposure.sync(groupBuy, post, ENDED.plusHours(72).minusSeconds(1))).isFalse();
        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("72시간 경과 → DRAFT")
    void retiredAfterRetention() {
        GroupBuyPost post = publishedPost();
        GroupBuy groupBuy = ended(GroupBuyCloseType.COMPLETED, ENDED);

        assertThat(GroupBuyPostExposure.sync(groupBuy, post, ENDED.plusHours(72))).isTrue();
        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
        // 두 번째 실행은 바꿀 것이 없다 — 스케줄러 중복 실행에도 한 번만 전이한다
        assertThat(GroupBuyPostExposure.sync(groupBuy, post, ENDED.plusHours(73))).isFalse();
    }

    @Test
    @DisplayName("조기 마감은 end_at이 아니라 실제로 끝난 시각(ended_at) 기준이다")
    void earlyCloseUsesEndedAt() {
        GroupBuyPost post = publishedPost();
        LocalDateTime closedAt = ENDED.minusDays(10);
        GroupBuy groupBuy = ended(GroupBuyCloseType.EARLY_CLOSED, closedAt);

        GroupBuyPostExposure.sync(groupBuy, post, closedAt.plusHours(72));

        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("직권 중단 → 즉시 DRAFT")
    void suspendedRetiresImmediately() {
        GroupBuyPost post = publishedPost();
        GroupBuy groupBuy = GroupBuy.builder()
                .id(1L).status(GroupBuyStatus.SUSPENDED).closeType(GroupBuyCloseType.SUSPENDED)
                .startAt(OPENED).endAt(ENDED).endedAt(ENDED.minusDays(3))
                .build();

        GroupBuyPostExposure.sync(groupBuy, post, ENDED.minusDays(3).plusMinutes(1));

        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("숨긴 채 종료 → 숨긴 채로 남는다(종료가 숨김 해제가 되면 안 된다)")
    void hiddenStaysHidden() {
        GroupBuyPost post = publishedPost();
        post.hide("AD_VIOLATION", "사유", 1, 99L, ENDED.minusDays(1));
        GroupBuyPostExposure.sync(selling(), post, ENDED.minusDays(1));
        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);

        GroupBuyPostExposure.sync(ended(GroupBuyCloseType.COMPLETED, ENDED), post, ENDED.plusMinutes(1));

        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("한 번도 노출되지 않은 게시물(승인 전 종료)은 남기지 않는다")
    void neverPublishedStaysDraft() {
        GroupBuyPost post = approvedPost();

        GroupBuyPostExposure.sync(ended(GroupBuyCloseType.COMPLETED, ENDED), post, ENDED.plusMinutes(1));

        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getPost().getPublishedAt()).isNull();
    }

    // ------------------------------------------------------------------ fixture

    private static GroupBuy selling() {
        return GroupBuy.builder()
                .id(1L).status(GroupBuyStatus.IN_PROGRESS)
                .startAt(OPENED).endAt(ENDED).openedAt(OPENED)
                .build();
    }

    private static GroupBuy ended(GroupBuyCloseType closeType, LocalDateTime endedAt) {
        return GroupBuy.builder()
                .id(1L).status(GroupBuyStatus.ENDED).closeType(closeType)
                .startAt(OPENED).endAt(ENDED).openedAt(OPENED).endedAt(endedAt)
                .build();
    }

    private static GroupBuyPost approvedPost() {
        return GroupBuyPost.builder()
                .post(Post.groupBuyDraft(null, "본문"))
                .title("제목")
                .reviewStatus(GroupBuyPostReviewStatus.APPROVED)
                .build();
    }

    /** 오픈으로 한 번 노출된 게시물 */
    private static GroupBuyPost publishedPost() {
        GroupBuyPost post = approvedPost();
        GroupBuyPostExposure.sync(selling(), post, OPENED);
        return post;
    }
}
