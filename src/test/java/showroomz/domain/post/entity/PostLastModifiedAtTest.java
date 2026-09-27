package showroomz.domain.post.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.post.type.PostStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소비자 상세의 「마지막 수정 시각」 — 게시 후 본문 수정만 센다. 좋아요·노출 수·노출 상태는 수정이 아니다.
 */
@DisplayName("게시물 마지막 수정 시각 — getLastModifiedAt")
class PostLastModifiedAtTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 9, 20, 12, 0);

    @Test
    @DisplayName("게시 후 수정이 없으면 게시 시각이다")
    void notEditedFallsBackToPublishedAt() {
        Post post = Post.published(null, "본문", null, PUBLISHED_AT);

        assertThat(post.getEditedAt()).isNull();
        assertThat(post.getLastModifiedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    @DisplayName("게시 후 본문을 고치면 그 시각이다")
    void editAfterPublishIsStamped() {
        Post post = Post.published(null, "본문", null, PUBLISHED_AT);
        LocalDateTime editedAt = PUBLISHED_AT.plusHours(3);

        post.updateContent("고친 본문", null, editedAt);

        assertThat(post.getLastModifiedAt()).isEqualTo(editedAt);
    }

    @Test
    @DisplayName("게시 전 임시저장 수정은 세지 않는다 — 게시하면 게시 시각이다")
    void editBeforePublishIsNotStamped() {
        Post post = Post.draft(null, "초안", null);

        post.updateContent("다듬은 초안", null, PUBLISHED_AT.minusHours(1));
        post.publish(PUBLISHED_AT);

        assertThat(post.getEditedAt()).isNull();
        assertThat(post.getLastModifiedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    @DisplayName("공구 게시물 — 오픈 전 수정은 세지 않고, 오픈 뒤 수정만 센다. 좋아요·노출 수·숨김은 바꾸지 않는다")
    void groupBuyPostCountsOnlyEditsAfterOpen() {
        Post post = Post.groupBuyDraft(null, "제출 본문");
        post.updateContent("승인 후 오픈 전 수정", null, PUBLISHED_AT.minusDays(1));

        post.changeGroupBuyExposure(PostStatus.PUBLISHED, PUBLISHED_AT);
        post.increaseLikeCount();
        post.increaseImpressionCount();
        assertThat(post.getLastModifiedAt()).isEqualTo(PUBLISHED_AT);

        LocalDateTime editedAt = PUBLISHED_AT.plusDays(1);
        post.updateContent("오픈 뒤 수정", null, editedAt);
        post.changeGroupBuyExposure(PostStatus.DRAFT, editedAt.plusHours(1));
        post.changeGroupBuyExposure(PostStatus.PUBLISHED, editedAt.plusHours(2));

        assertThat(post.getLastModifiedAt()).isEqualTo(editedAt);
    }
}
