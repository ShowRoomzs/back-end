package showroomz.domain.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.repository.DeviceTokenRow;
import showroomz.domain.notification.service.DeviceTokenService;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostNotificationEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * 이력 한 건이 <b>누구에게</b> 가는지를 검증한다.
 *
 * <p>이 클래스의 존재 이유가 그 갈림길이다 — 이력에는 {@code creatorId}밖에 없지만, 노출 중지는
 * 크리에이터 본인에게 가고 신규 게시물은 팔로워 전원에게 간다. 두 경로가 뒤바뀌면 팔로워 수만
 * 명에게 "당신의 게시물이 중지되었습니다"가 날아간다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushPostNotificationSenderTest {

    private static final long CREATOR_ID = 5L;
    private static final long CREATOR_USER_ID = 77L;
    private static final long POST_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 10, 0);

    /** 프로덕션 CHUNK_SIZE와 같아야 한다 — 이 값이 어긋나면 페이징 검증이 의미를 잃는다 */
    private static final int CHUNK_SIZE = 500;

    @Mock
    private PostRepository postRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock
    private PushSender pushSender;

    private PushPostNotificationSender sender;
    private Creator creator;

    @BeforeEach
    void setUp() {
        Users creatorUser = new Users();
        creatorUser.setId(CREATOR_USER_ID);
        creator = Creator.builder().id(CREATOR_ID).user(creatorUser).showroomName("뷰티 소연").build();

        sender = new PushPostNotificationSender(
                postRepository, creatorRepository, deviceTokenRepository, deviceTokenService,
                pushSender, new PostPushMessageFactory());

        given(creatorRepository.findById(CREATOR_ID)).willReturn(Optional.of(creator));
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(
                Post.published(creator, "오늘의 코디", null, NOW)));
        given(pushSender.send(anyList(), any())).willReturn(new PushResult(1, 0, List.of()));
    }

    @Nested
    @DisplayName("수신자 갈림길")
    class Recipients {

        @Test
        @DisplayName("신규 게시물은 팔로워에게 가고, 크리에이터 본인 토큰은 조회조차 하지 않는다")
        void publishedGoesToFollowers() {
            givenFollowerTokens(rows(1, 3));

            sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS));

            then(deviceTokenRepository).should()
                    .findFollowerTokens(eq(CREATOR_ID), eq(UserStatus.NORMAL), eq(0L), any(Pageable.class));
            then(deviceTokenRepository).should(never()).findUserTokens(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("노출 중지는 크리에이터 본인에게만 가고, 팔로워는 조회조차 하지 않는다")
        void suspendedGoesToCreatorOnly() {
            given(deviceTokenRepository.findUserTokens(eq(CREATOR_USER_ID), eq(0L), any(Pageable.class)))
                    .willReturn(rows(1, 1));

            sender.send(log(PostNotificationEvent.SUSPENDED));

            then(deviceTokenRepository).should().findUserTokens(eq(CREATOR_USER_ID), eq(0L), any(Pageable.class));
            then(deviceTokenRepository).should(never())
                    .findFollowerTokens(anyLong(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("본인이 지운 게시물은 이력만 남기고 푸시하지 않는다")
        void selfDeleteIsNotPushed() {
            boolean delivered = sender.send(log(PostNotificationEvent.DELETED_BY_SELF));

            assertThat(delivered).isFalse();
            then(pushSender).should(never()).send(anyList(), any());
        }
    }

    @Nested
    @DisplayName("팔로워 팬아웃")
    class FanOut {

        @Test
        @DisplayName("한 묶음이 꽉 차면 마지막 id 다음부터 이어 읽는다")
        void keysetPagingContinuesAfterFullChunk() {
            List<DeviceTokenRow> full = rows(1, CHUNK_SIZE);
            given(deviceTokenRepository.findFollowerTokens(eq(CREATOR_ID), eq(UserStatus.NORMAL), eq(0L), any()))
                    .willReturn(full);
            given(deviceTokenRepository.findFollowerTokens(
                    eq(CREATOR_ID), eq(UserStatus.NORMAL), eq((long) CHUNK_SIZE), any()))
                    .willReturn(rows(CHUNK_SIZE + 1, CHUNK_SIZE + 2));

            sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS));

            // 두 번째 묶음이 500개 미만이라 세 번째 조회는 없다
            then(deviceTokenRepository).should(times(2))
                    .findFollowerTokens(eq(CREATOR_ID), eq(UserStatus.NORMAL), anyLong(), any());
            then(pushSender).should(times(2)).send(anyList(), any());
        }

        @Test
        @DisplayName("팔로워가 없으면 발송을 시도하지 않는다")
        void noFollowersNoSend() {
            givenFollowerTokens(List.of());

            boolean delivered = sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS));

            assertThat(delivered).isFalse();
            then(pushSender).should(never()).send(anyList(), any());
        }

        @Test
        @DisplayName("죽은 토큰은 그 묶음이 끝나는 즉시 지운다 — 다음 발송에서 같은 실패를 반복하지 않게")
        void invalidTokensArePurged() {
            givenFollowerTokens(rows(1, 2));
            given(pushSender.send(anyList(), any()))
                    .willReturn(new PushResult(1, 1, List.of("token-2")));

            sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS));

            then(deviceTokenService).should().purgeInvalid(List.of("token-2"));
        }
    }

    @Nested
    @DisplayName("전달 여부")
    class Delivered {

        @Test
        @DisplayName("한 명이라도 받으면 전달로 본다")
        void anySuccessIsDelivered() {
            givenFollowerTokens(rows(1, 3));
            given(pushSender.send(anyList(), any())).willReturn(new PushResult(1, 2, List.of()));

            assertThat(sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS))).isTrue();
        }

        @Test
        @DisplayName("전원 실패면 전달로 보지 않는다 — 이력에 false로 남아야 무엇이 안 나갔는지 읽힌다")
        void allFailureIsNotDelivered() {
            givenFollowerTokens(rows(1, 3));
            given(pushSender.send(anyList(), any())).willReturn(new PushResult(0, 3, List.of()));

            assertThat(sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS))).isFalse();
        }
    }

    @Test
    @DisplayName("팔로워 알림의 제목은 쇼룸 이름이다 — 목록에서 누가 올렸는지가 보여야 한다")
    void followerMessageIsTitledWithShowroomName() {
        givenFollowerTokens(rows(1, 1));

        sender.send(log(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS));

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        then(pushSender).should().send(anyList(), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("뷰티 소연");
        assertThat(captor.getValue().data())
                .containsEntry("type", "PUBLISHED_TO_FOLLOWERS")
                .containsEntry("postId", String.valueOf(POST_ID));
    }

    @Test
    @DisplayName("파기된 게시물이어도 통지는 나간다 — 문구가 게시물에 의존하지 않는다")
    void purgedPostStillNotifies() {
        given(postRepository.findById(POST_ID)).willReturn(Optional.empty());
        given(deviceTokenRepository.findUserTokens(eq(CREATOR_USER_ID), eq(0L), any())).willReturn(rows(1, 1));

        assertThat(sender.send(log(PostNotificationEvent.DELETED_BY_EXPIRE))).isTrue();
    }

    private void givenFollowerTokens(List<DeviceTokenRow> rows) {
        given(deviceTokenRepository.findFollowerTokens(eq(CREATOR_ID), eq(UserStatus.NORMAL), eq(0L), any()))
                .willReturn(rows);
    }

    private static List<DeviceTokenRow> rows(int fromId, int toId) {
        List<DeviceTokenRow> rows = new ArrayList<>();
        IntStream.rangeClosed(fromId, toId)
                .forEach(id -> rows.add(new DeviceTokenRow((long) id, "token-" + id)));
        return rows;
    }

    private static PostNotificationLog log(PostNotificationEvent event) {
        PostNotificationLog notificationLog =
                new PostNotificationLog(POST_ID, CREATOR_ID, event, null, NOW);
        ReflectionTestUtils.setField(notificationLog, "id", 1L);
        return notificationLog;
    }
}
