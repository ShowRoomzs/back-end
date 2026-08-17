package showroomz.api.app.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.post.DTO.PostImpressionRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 게시물 노출 적재 (§24-7) — 인사이트 3단이 전부 이 로그 위에 선다.
 *
 * <p>이 서비스에서 가장 빨리 불어나는 데이터라 <b>적재 시점에</b> 접는 것이 계약이다.
 * 중복은 쇼룸 방문(§22-4)과 같은 30분 세션 규칙으로 거르고, 한 요청이 실을 수 있는 건수도 제한한다.
 * 상한이 없으면 노출 적재가 곧 대량 쓰기 창구가 된다.
 *
 * <p>카운터와 로그를 <b>같은 자리에서</b> 올려야 목록의 숫자와 인사이트의 숫자가 갈리지 않는다 —
 * 저장과 카운터 증가가 함께 일어나는지도 함께 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostImpressionServiceTest {

    private static final String USERNAME = "mia";
    private static final long POST_ID = 42L;
    private static final long SHOWROOM_ID = 5L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImpressionRepository postImpressionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostImpressionService postImpressionService;

    private Users user(long id) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(USERNAME, "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Post visiblePost(long id) {
        Creator showroom = Creator.builder().id(SHOWROOM_ID).showroomName("소연 뷰티").build();
        Post post = Post.published(showroom, "본문", new BigDecimal("0.8000"), LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private PostImpressionRequest request(List<Long> postIds, String visitorId) {
        PostImpressionRequest request = new PostImpressionRequest();
        ReflectionTestUtils.setField(request, "postIds", postIds);
        ReflectionTestUtils.setField(request, "visitorId", visitorId);
        return request;
    }

    /** 세션 안에 이미 본 기록이 없다고 응답하게 해 둔다 — 중복 차단을 검증하는 테스트만 이를 뒤집는다. */
    private void givenNoRecentImpression() {
        given(postImpressionRepository.existsByPost_IdAndViewerKeyAndViewedAtAfter(anyLong(), anyString(), any()))
                .willReturn(false);
    }

    @Nested
    @DisplayName("조회자 식별")
    class ViewerKey {

        @Test
        @DisplayName("로그인 조회는 사용자 기준으로 적재된다 — 디바이스 식별자는 무시한다")
        void loggedInImpressionIsKeyedByUser() {
            Users viewer = user(7L);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(viewer));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(visiblePost(POST_ID)));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(USERNAME, request(List.of(POST_ID), "device-abc"));

            ArgumentCaptor<PostImpression> captor = ArgumentCaptor.forClass(PostImpression.class);
            verify(postImpressionRepository).save(captor.capture());
            assertThat(captor.getValue().getViewerKey()).isEqualTo("u:7");
            assertThat(captor.getValue().getUser()).isSameAs(viewer);
        }

        /** 비로그인 조회도 노출에 포함된다 — 다만 표본은 "미확인"으로 분류된다. */
        @Test
        @DisplayName("비로그인 조회는 디바이스 기준으로 적재되고 사용자는 비어 있다")
        void anonymousImpressionIsKeyedByDevice() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(visiblePost(POST_ID)));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(null, request(List.of(POST_ID), "device-abc"));

            ArgumentCaptor<PostImpression> captor = ArgumentCaptor.forClass(PostImpression.class);
            verify(postImpressionRepository).save(captor.capture());
            assertThat(captor.getValue().getViewerKey()).isEqualTo("d:device-abc");
            assertThat(captor.getValue().getUser()).isNull();
        }

        /** 식별자가 없으면 같은 사람의 재노출을 접을 수 없어 노출 수가 부풀고 귀속도 성립하지 않는다. */
        @Test
        @DisplayName("비로그인인데 디바이스 식별자가 없으면 거절한다")
        void anonymousWithoutDeviceIdIsRejected() {
            assertThatThrownBy(() -> postImpressionService.recordImpressions(
                    null, request(List.of(POST_ID), null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(postImpressionRepository, never()).save(any());
        }

        /** 토큰이 유효해도 그 사이 탈퇴했으면 사용자를 못 찾는다 — 디바이스 식별자로 이어받는다. */
        @Test
        @DisplayName("토큰의 사용자를 못 찾으면 디바이스 식별자로 적재한다")
        void unknownUserFallsBackToDevice() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(visiblePost(POST_ID)));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(USERNAME, request(List.of(POST_ID), "device-abc"));

            ArgumentCaptor<PostImpression> captor = ArgumentCaptor.forClass(PostImpression.class);
            verify(postImpressionRepository).save(captor.capture());
            assertThat(captor.getValue().getViewerKey()).isEqualTo("d:device-abc");
        }
    }

    @Nested
    @DisplayName("중복 접기")
    class Deduplication {

        @Test
        @DisplayName("30분 세션 안의 재노출은 적재하지 않고 카운터도 올리지 않는다")
        void revisitWithinSessionIsSkipped() {
            Post post = visiblePost(POST_ID);
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            given(postImpressionRepository.existsByPost_IdAndViewerKeyAndViewedAtAfter(
                    anyLong(), anyString(), any())).willReturn(true);

            postImpressionService.recordImpressions(null, request(List.of(POST_ID), "device-abc"));

            verify(postImpressionRepository, never()).save(any());
            assertThat(post.getImpressionCount()).isZero();
        }

        /** 스크롤 중 같은 카드가 여러 번 담겨 오는 일이 흔하다 — DB에 가기 전에 접는다. */
        @Test
        @DisplayName("한 요청 안의 중복 ID는 한 번만 적재한다")
        void duplicateIdsWithinRequestAreCollapsed() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(visiblePost(POST_ID)));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(null,
                    request(List.of(POST_ID, POST_ID, POST_ID), "device-abc"));

            verify(postImpressionRepository, times(1)).save(any());
            verify(postRepository, times(1)).findById(POST_ID);
        }

        @Test
        @DisplayName("null이 섞여 와도 건너뛰고 나머지를 적재한다")
        void nullIdsAreSkipped() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(visiblePost(POST_ID)));
            givenNoRecentImpression();

            List<Long> ids = new ArrayList<>();
            ids.add(null);
            ids.add(POST_ID);
            postImpressionService.recordImpressions(null, request(ids, "device-abc"));

            verify(postImpressionRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("배치 상한")
    class BatchLimit {

        /**
         * 배치의 목적은 요청 수를 줄이는 것이지 한 요청을 무한정 키우는 게 아니다 —
         * 상한이 없으면 이 경로가 대량 쓰기 창구가 된다.
         */
        @Test
        @DisplayName("한 요청은 50건까지만 적재한다")
        void batchIsCappedAtFifty() {
            given(postRepository.findById(anyLong()))
                    .willAnswer(invocation -> Optional.of(visiblePost(invocation.getArgument(0))));
            givenNoRecentImpression();

            List<Long> ids = new ArrayList<>();
            for (long id = 1; id <= 80; id++) {
                ids.add(id);
            }

            postImpressionService.recordImpressions(null, request(ids, "device-abc"));

            verify(postImpressionRepository, times(50)).save(any());
        }

        @Test
        @DisplayName("상한을 넘긴 요청도 예외 없이 앞쪽 50건을 처리한다 — 피드 스크롤이 멈추면 안 된다")
        void overLimitRequestDoesNotFail() {
            given(postRepository.findById(anyLong()))
                    .willAnswer(invocation -> Optional.of(visiblePost(invocation.getArgument(0))));
            givenNoRecentImpression();

            List<Long> ids = new ArrayList<>();
            for (long id = 1; id <= 51; id++) {
                ids.add(id);
            }

            postImpressionService.recordImpressions(null, request(ids, "device-abc"));

            verify(postRepository, never()).findById(51L);
        }
    }

    @Nested
    @DisplayName("적재 대상 판정")
    class Eligibility {

        /** 화면에 떠 있던 카드가 그 사이 내려갔을 뿐이다 — 적재 실패로 스크롤이 멈출 이유는 없다. */
        @Test
        @DisplayName("없는 게시물은 조용히 버린다")
        void unknownPostIsSilentlyIgnored() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.empty());
            givenNoRecentImpression();

            postImpressionService.recordImpressions(null, request(List.of(POST_ID), "device-abc"));

            verify(postImpressionRepository, never()).save(any());
        }

        @Test
        @DisplayName("내려간 게시물의 노출은 적재하지 않는다")
        void suspendedPostIsNotRecorded() {
            Post post = visiblePost(POST_ID);
            post.suspend();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(null, request(List.of(POST_ID), "device-abc"));

            verify(postImpressionRepository, never()).save(any());
        }

        /** 목록의 숫자와 인사이트의 숫자가 갈리지 않도록 로그와 카운터를 같은 자리에서 올린다. */
        @Test
        @DisplayName("적재와 카운터 증가가 함께 일어난다")
        void logAndCounterMoveTogether() {
            Post post = visiblePost(POST_ID);
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(null, request(List.of(POST_ID), "device-abc"));

            verify(postImpressionRepository).save(any());
            assertThat(post.getImpressionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("적재 로그에는 쇼룸 귀속이 함께 남는다 — 인사이트가 쇼룸 단위로 집계된다")
        void impressionCarriesShowroomId() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(visiblePost(POST_ID)));
            givenNoRecentImpression();

            postImpressionService.recordImpressions(null, request(List.of(POST_ID), "device-abc"));

            ArgumentCaptor<PostImpression> captor = ArgumentCaptor.forClass(PostImpression.class);
            verify(postImpressionRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatorId()).isEqualTo(SHOWROOM_ID);
        }
    }
}
