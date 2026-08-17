package showroomz.api.app.showroom.service;

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
import showroomz.api.app.showroom.DTO.FollowingShowroomResponse;
import showroomz.api.app.showroom.type.FollowingShowroomSort;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.service.PostAttributionService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 쇼룸 팔로우 — 팔로우 대상은 쇼룸뿐이다(마켓 팔로우는 폐지).
 *
 * <p>두 가지를 지킨다. 첫째 <b>멱등성</b>: 이미 팔로우 중인데 다시 누르면 행이 하나 더 생기면 안 되고,
 * 팔로우하지 않은 상태에서 취소를 눌러도 조용히 지나가야 한다. 앱은 낙관적 토글이라 같은 요청이
 * 두 번 오는 일이 흔하다.
 *
 * <p>둘째 <b>정렬</b>: 기본 정렬이 "최근 게시물을 올린 쇼룸 순"이고 게시물이 없는 쇼룸은 뒤로 밀린다.
 * 정렬이 DB 밖에서 끝나므로 페이징도 손으로 자르며, 그 슬라이싱 경계까지 함께 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowroomFollowServiceTest {

    private static final String USERNAME = "mia";
    private static final long SHOWROOM_ID = 5L;

    @Mock
    private CreatorFollowRepository creatorFollowRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private PostAttributionService postAttributionService;

    @InjectMocks
    private ShowroomFollowService showroomFollowService;

    private Users user;

    private Users givenUser() {
        LocalDateTime now = LocalDateTime.now();
        user = new Users(USERNAME, "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", 7L);
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        return user;
    }

    private Creator showroom(long id, String showroomName) {
        Users owner = new Users("creator" + id, showroomName, "c" + id + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, LocalDateTime.now(), LocalDateTime.now());
        return Creator.builder().id(id).user(owner).showroomName(showroomName).build();
    }

    private CreatorFollow follow(Creator creator, LocalDateTime followedAt) {
        CreatorFollow follow = new CreatorFollow(user, creator);
        ReflectionTestUtils.setField(follow, "createdAt", followedAt);
        return follow;
    }

    private PagingRequest paging(int page, int size) {
        PagingRequest request = new PagingRequest();
        request.setPage(page);
        request.setSize(size);
        return request;
    }

    @Nested
    @DisplayName("팔로우 · 취소")
    class Toggle {

        @Test
        @DisplayName("처음 누르면 팔로우가 저장된다")
        void firstFollowIsSaved() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(false);

            showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID);

            verify(creatorFollowRepository).save(any(CreatorFollow.class));
        }

        /** 앱이 낙관적 토글이라 같은 요청이 두 번 오는 일이 흔하다 — 행이 늘면 팔로워 수가 부풀고 목록이 중복된다. */
        @Test
        @DisplayName("이미 팔로우 중이면 다시 저장하지 않는다")
        void duplicateFollowIsIgnored() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(true);

            showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID);

            verify(creatorFollowRepository, never()).save(any());
        }

        /** §24-7 — 이 팔로우가 어떤 게시물을 보고 누른 것인지 지금 정해 태그로 굳힌다. */
        @Test
        @DisplayName("팔로우에는 직전에 본 게시물이 귀속으로 붙는다")
        void followCarriesAttribution() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(false);
            given(postAttributionService.resolveAttributedPostId(anyString(), anyLong(), any()))
                    .willReturn(99L);

            showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID);

            ArgumentCaptor<CreatorFollow> captor = ArgumentCaptor.forClass(CreatorFollow.class);
            verify(creatorFollowRepository).save(captor.capture());
            assertThat(captor.getValue().getAttributedPostId()).isEqualTo(99L);
        }

        /** 팔로우는 로그인 행동이라 조회 키는 언제나 사용자 기준이다. */
        @Test
        @DisplayName("귀속 조회 키는 사용자 기준이다")
        void attributionUsesUserKey() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(false);

            showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID);

            verify(postAttributionService).resolveAttributedPostId(
                    org.mockito.ArgumentMatchers.eq("u:7"),
                    org.mockito.ArgumentMatchers.eq(SHOWROOM_ID),
                    any());
        }

        @Test
        @DisplayName("귀속할 게시물이 없어도 팔로우는 저장된다")
        void followSurvivesMissingAttribution() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(false);
            given(postAttributionService.resolveAttributedPostId(anyString(), anyLong(), any()))
                    .willReturn(null);

            showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID);

            ArgumentCaptor<CreatorFollow> captor = ArgumentCaptor.forClass(CreatorFollow.class);
            verify(creatorFollowRepository).save(captor.capture());
            assertThat(captor.getValue().getAttributedPostId()).isNull();
        }

        @Test
        @DisplayName("팔로우 중이면 취소된다")
        void unfollowRemovesFollow() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(true);

            showroomFollowService.unfollowShowroom(USERNAME, SHOWROOM_ID);

            verify(creatorFollowRepository).deleteByUserAndCreator(user, creator);
        }

        @Test
        @DisplayName("팔로우하지 않은 쇼룸을 취소해도 조용히 지나간다")
        void unfollowWithoutFollowIsIgnored() {
            givenUser();
            Creator creator = showroom(SHOWROOM_ID, "소연 뷰티");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(creatorFollowRepository.existsByUserAndCreator(user, creator)).willReturn(false);

            showroomFollowService.unfollowShowroom(USERNAME, SHOWROOM_ID);

            verify(creatorFollowRepository, never()).deleteByUserAndCreator(any(), any());
        }

        @Test
        @DisplayName("없는 쇼룸은 팔로우할 수 없다")
        void unknownShowroomIsRejected() {
            givenUser();
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CREATOR_NOT_FOUND);

            verify(creatorFollowRepository, never()).save(any());
        }

        @Test
        @DisplayName("없는 회원이면 404를 낸다")
        void unknownUserIsRejected() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> showroomFollowService.followShowroom(USERNAME, SHOWROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("팔로잉 목록 정렬")
    class Sorting {

        private Creator early;
        private Creator middle;
        private Creator late;

        private void givenThreeFollows() {
            givenUser();
            early = showroom(1L, "가장 먼저 팔로우");
            middle = showroom(2L, "두 번째로 팔로우");
            late = showroom(3L, "가장 최근에 팔로우");

            given(creatorFollowRepository.findAllByUserWithCreator(user)).willReturn(List.of(
                    follow(early, LocalDateTime.now().minusDays(30)),
                    follow(middle, LocalDateTime.now().minusDays(7)),
                    follow(late, LocalDateTime.now().minusHours(3))));
            given(connectionRepository.findCreatorIdsWithOngoingGroupBuy(any())).willReturn(List.of());
        }

        /** C2 기본 정렬 — 팔로우 시각이 아니라 쇼룸의 최근 게시물 시각이 기준이다. */
        @Test
        @DisplayName("기본 정렬은 최근에 게시물을 올린 쇼룸 순이다")
        void defaultSortIsLatestPost() {
            givenThreeFollows();
            given(postRepository.findLatestPostCreatedAtByCreatorIds(any())).willReturn(List.of(
                    new Object[]{1L, LocalDateTime.now().minusHours(1)},
                    new Object[]{3L, LocalDateTime.now().minusDays(5)}));

            PageResponse<FollowingShowroomResponse> result =
                    showroomFollowService.getFollowedShowrooms(USERNAME, FollowingShowroomSort.DEFAULT, paging(1, 20));

            assertThat(result.getContent()).extracting(FollowingShowroomResponse::getShowroomId)
                    .containsExactly(1L, 3L, 2L);
        }

        /** 게시물이 없는 쇼룸을 앞에 두면 목록 첫 화면이 빈 쇼룸으로 채워진다. */
        @Test
        @DisplayName("게시물이 없는 쇼룸은 뒤로 밀리고 그 안에서는 팔로우 최신순이다")
        void showroomsWithoutPostsGoLast() {
            givenThreeFollows();
            given(postRepository.findLatestPostCreatedAtByCreatorIds(any())).willReturn(List.of());

            PageResponse<FollowingShowroomResponse> result =
                    showroomFollowService.getFollowedShowrooms(USERNAME, FollowingShowroomSort.DEFAULT, paging(1, 20));

            assertThat(result.getContent()).extracting(FollowingShowroomResponse::getShowroomId)
                    .containsExactly(3L, 2L, 1L);
        }

        @Test
        @DisplayName("팔로우 최신순은 가장 최근에 팔로우한 쇼룸부터다")
        void followLatestSort() {
            givenThreeFollows();

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_LATEST, paging(1, 20));

            assertThat(result.getContent()).extracting(FollowingShowroomResponse::getShowroomId)
                    .containsExactly(3L, 2L, 1L);
        }

        @Test
        @DisplayName("팔로우 오래된순은 가장 먼저 팔로우한 쇼룸부터다")
        void followOldestSort() {
            givenThreeFollows();

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_OLDEST, paging(1, 20));

            assertThat(result.getContent()).extracting(FollowingShowroomResponse::getShowroomId)
                    .containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("정렬을 지정하지 않으면 기본 정렬을 쓴다")
        void nullSortFallsBackToDefault() {
            givenThreeFollows();
            given(postRepository.findLatestPostCreatedAtByCreatorIds(any())).willReturn(List.of());

            PageResponse<FollowingShowroomResponse> result =
                    showroomFollowService.getFollowedShowrooms(USERNAME, null, paging(1, 20));

            assertThat(result.getContent()).extracting(FollowingShowroomResponse::getShowroomId)
                    .containsExactly(3L, 2L, 1L);
        }
    }

    @Nested
    @DisplayName("팔로잉 목록 페이징 (수동 슬라이싱)")
    class Paging {

        private void givenThreeFollows() {
            givenUser();
            given(creatorFollowRepository.findAllByUserWithCreator(user)).willReturn(List.of(
                    follow(showroom(1L, "A"), LocalDateTime.now().minusDays(30)),
                    follow(showroom(2L, "B"), LocalDateTime.now().minusDays(7)),
                    follow(showroom(3L, "C"), LocalDateTime.now().minusHours(3))));
            given(connectionRepository.findCreatorIdsWithOngoingGroupBuy(any())).willReturn(List.of());
        }

        @Test
        @DisplayName("페이지를 잘라도 전체 개수는 그대로다")
        void totalSurvivesPaging() {
            givenThreeFollows();

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_OLDEST, paging(1, 2));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getPageInfo().getTotalResults()).isEqualTo(3);
        }

        @Test
        @DisplayName("마지막 페이지는 남은 만큼만 준다")
        void lastPageReturnsRemainder() {
            givenThreeFollows();

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_OLDEST, paging(2, 2));

            assertThat(result.getContent()).extracting(FollowingShowroomResponse::getShowroomId)
                    .containsExactly(3L);
        }

        /** 손으로 자르는 코드라 범위를 넘는 페이지에서 IndexOutOfBounds가 나기 쉽다. */
        @Test
        @DisplayName("범위를 넘는 페이지는 빈 목록을 준다 — 예외로 터지지 않는다")
        void pageBeyondRangeIsEmpty() {
            givenThreeFollows();

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_OLDEST, paging(9, 2));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getPageInfo().getTotalResults()).isEqualTo(3);
        }

        @Test
        @DisplayName("팔로우한 쇼룸이 없으면 빈 목록을 준다")
        void noFollowsReturnsEmpty() {
            givenUser();
            given(creatorFollowRepository.findAllByUserWithCreator(user)).willReturn(List.of());

            PageResponse<FollowingShowroomResponse> result =
                    showroomFollowService.getFollowedShowrooms(USERNAME, null, paging(1, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getPageInfo().getTotalResults()).isZero();
        }
    }

    @Nested
    @DisplayName("목록 항목 조립")
    class ItemMapping {

        @Test
        @DisplayName("공구 진행 중인 쇼룸만 표시가 붙는다 — 아바타 링의 근거다")
        void onlyOngoingGroupBuyIsFlagged() {
            givenUser();
            given(creatorFollowRepository.findAllByUserWithCreator(user)).willReturn(List.of(
                    follow(showroom(1L, "공구 중"), LocalDateTime.now().minusDays(2)),
                    follow(showroom(2L, "공구 없음"), LocalDateTime.now().minusDays(1))));
            given(connectionRepository.findCreatorIdsWithOngoingGroupBuy(any())).willReturn(List.of(1L));

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_OLDEST, paging(1, 20));

            assertThat(result.getContent().get(0).getHasOngoingGroupBuy()).isTrue();
            assertThat(result.getContent().get(1).getHasOngoingGroupBuy()).isFalse();
        }

        /** 등록을 마치지 않아 쇼룸명이 없으면 계정 닉네임으로 대신한다 — 목록에 빈 칸이 남으면 안 된다. */
        @Test
        @DisplayName("쇼룸명이 없으면 계정 닉네임으로 대신 표시한다")
        void missingShowroomNameFallsBackToNickname() {
            givenUser();
            Creator unnamed = showroom(1L, null);
            ReflectionTestUtils.setField(unnamed.getUser(), "nickname", "미등록 쇼룸");
            given(creatorFollowRepository.findAllByUserWithCreator(user))
                    .willReturn(List.of(follow(unnamed, LocalDateTime.now())));
            given(connectionRepository.findCreatorIdsWithOngoingGroupBuy(any())).willReturn(List.of());

            PageResponse<FollowingShowroomResponse> result = showroomFollowService.getFollowedShowrooms(
                    USERNAME, FollowingShowroomSort.FOLLOW_LATEST, paging(1, 20));

            assertThat(result.getContent().get(0).getShowroomName()).isEqualTo("미등록 쇼룸");
        }
    }
}
