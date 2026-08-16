package showroomz.domain.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * §24-5의 세 번째 갈래 — <b>기한 내 미신청 → 영구 삭제</b>.
 *
 * <p>사람의 조작 없이 콘텐츠가 사라지는 유일한 경로라, 통지가 빠지면 "알리지 않고 사라지는 경우는 없다"가
 * 정확히 여기서 깨진다. 그래서 상태 전이와 통지를 함께 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostLifecycleServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 10, 0);

    @Mock
    private PostSuspensionRepository postSuspensionRepository;
    @Mock
    private PostNotificationService postNotificationService;

    private final PostProperties postProperties = new PostProperties();

    private PostLifecycleService postLifecycleService;
    private Creator creator;

    @BeforeEach
    void setUp() {
        creator = Creator.builder().id(5L).showroomName("뷰티 소연").build();
        postLifecycleService = new PostLifecycleService(
                postSuspensionRepository, postNotificationService, postProperties);
    }

    @Test
    @DisplayName("기한이 지나고 신청이 없으면 영구 삭제로 넘기고 통지 이력을 남긴다")
    void expiresIntoDeletion() {
        Post post = suspendedPost();
        PostSuspension suspension = overdueSuspension(post);
        given(postSuspensionRepository.findExpiredWithoutAppeal(eq(NOW), any(Pageable.class)))
                .willReturn(List.of(suspension));

        int expired = postLifecycleService.expireOverdueSuspensions(NOW, 100);

        assertThat(expired).isEqualTo(1);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(post.getDeleteReason()).isEqualTo(PostDeleteReason.APPEAL_EXPIRED);
        assertThat(post.getPurgeAt()).isEqualTo(NOW.plusMonths(postProperties.getRetentionMonths()));
        assertThat(suspension.getResolution()).isEqualTo(SuspensionResolution.DELETED_BY_EXPIRE);

        then(postNotificationService).should()
                .notify(eq(post), eq(PostNotificationEvent.DELETED_BY_EXPIRE), any());
    }

    @Test
    @DisplayName("그 사이 본인이 지운 게시물은 상태를 덮지 않고 조치만 닫는다")
    void doesNotOverwriteAlreadyDeletedPost() {
        Post post = suspendedPost();
        post.softDelete(PostDeleteReason.SELF, NOW.minusDays(1), NOW.plusMonths(6));
        PostSuspension suspension = overdueSuspension(post);
        given(postSuspensionRepository.findExpiredWithoutAppeal(eq(NOW), any(Pageable.class)))
                .willReturn(List.of(suspension));

        postLifecycleService.expireOverdueSuspensions(NOW, 100);

        assertThat(post.getDeleteReason()).isEqualTo(PostDeleteReason.SELF);
        assertThat(suspension.getResolution()).isEqualTo(SuspensionResolution.DELETED_BY_SELF);
        then(postNotificationService).shouldHaveNoInteractions();
    }

    private Post suspendedPost() {
        Post post = Post.published(creator, "3주 루틴 기록", new BigDecimal("0.8000"), NOW.minusDays(20));
        ReflectionTestUtils.setField(post, "id", 301L);
        post.suspend();
        return post;
    }

    private PostSuspension overdueSuspension(Post post) {
        PostSuspension suspension = new PostSuspension(post, PostSuspensionReason.AD_DISCLOSURE, null,
                "운영정책 제12조", 9L, NOW.minusDays(8), NOW.minusDays(1));
        ReflectionTestUtils.setField(suspension, "id", 7L);
        return suspension;
    }
}
