package showroomz.api.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.entity.DeviceToken;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;
import showroomz.domain.notification.type.DevicePlatform;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.repository.PostNotificationLogRepository;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §24-3 "크리에이터가 게시하면 팔로워에게 알림이 간다"를 <b>HTTP 요청 한 번에서 FCM 호출까지</b>
 * 통째로 태운다.
 *
 * <p>단위 테스트로는 이 기능이 동작한다고 말할 수 없다. 게시 → 이력 적재 → 커밋 → 이벤트 →
 * 비동기 발송의 다섯 단계가 스프링의 서로 다른 장치(트랜잭션 이벤트·{@code @Async}·프록시)에
 * 얹혀 있어서, 각 조각이 다 통과해도 <b>사슬이 끊긴 채</b>일 수 있다. 실제로 여기서만 잡히는
 * 대표적 회귀가 "{@code @TransactionalEventListener}가 등록되지 않아 아무 일도 일어나지 않는"
 * 조용한 실패다.
 *
 * <p>발송 어댑터만 대역으로 바꾼다. FCM 서버를 부르지 않으면서 <b>누구에게 갔는지</b>는 그대로 본다.
 */
@DisplayName("[통합] §24-3 게시 → 팔로워 푸시")
class PostFollowerPushIntegrationTest extends IntegrationTestSupport {

    private static final String CREATOR_POSTS = "/v1/creator/posts";
    private static final String CDN = "https://cdn.example.com/";

    /** 비동기 발송을 기다리는 상한 — 넘으면 사슬이 끊긴 것으로 본다 */
    private static final long AWAIT_TIMEOUT_MILLIS = 5_000L;
    private static final long AWAIT_INTERVAL_MILLIS = 50L;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private CreatorFollowRepository creatorFollowRepository;
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;
    @Autowired
    private PostNotificationLogRepository postNotificationLogRepository;

    @MockitoBean
    private PushSender pushSender;

    /** 발송 스레드에서 채워지므로 동시성 안전한 목록을 쓴다 */
    private final List<String> pushedTokens = new CopyOnWriteArrayList<>();
    private final List<PushMessage> pushedMessages = new CopyOnWriteArrayList<>();

    private Creator showroom;
    private String creatorToken;

    @BeforeEach
    void setUpParticipants() {
        pushedTokens.clear();
        pushedMessages.clear();

        given(pushSender.send(anyList(), any())).willAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(0);
            pushedTokens.addAll(tokens);
            pushedMessages.add(invocation.getArgument(1));
            return new PushResult(tokens.size(), 0, List.of());
        });

        showroom = createShowroom("제니의 뷰티룸", "jenny");
        Users owner = showroom.getUser();
        creatorToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
    }

    @Test
    @DisplayName("게시하면 팔로워의 기기로 쇼룸 이름과 게시물 id가 실린 알림이 나간다")
    void publishPushesToFollowerDevices() throws Exception {
        Users follower = follower("mia", "미아");
        registerDevice(follower, "token-mia");

        Long postId = createPost("아침 루틴 정리했어요", "PUBLISH");

        awaitPush();

        assertThat(pushedTokens).containsExactly("token-mia");
        assertThat(pushedMessages).hasSize(1);
        assertThat(pushedMessages.get(0).title()).isEqualTo("제니의 뷰티룸");
        assertThat(pushedMessages.get(0).body()).isEqualTo("아침 루틴 정리했어요");
        assertThat(pushedMessages.get(0).data())
                .containsEntry("type", PostNotificationEvent.PUBLISHED_TO_FOLLOWERS.name())
                .containsEntry("postId", String.valueOf(postId));
    }

    /**
     * 한 명의 기기가 여러 대여도 전부 받아야 하고, 팔로우하지 않은 사람에게는 가면 안 된다.
     *
     * <p>후자가 특히 중요하다 — 팔로우 조건이 빠진 쿼리는 <b>전체 사용자에게 알림을 보낸다.</b>
     * 스테이징에서는 사용자가 몇 명뿐이라 눈에 띄지 않고, 운영에서 한 번에 터진다.
     */
    @Test
    @DisplayName("팔로워의 모든 기기로 가고, 팔로우하지 않은 사람에게는 가지 않는다")
    void pushesToEveryFollowerDeviceOnly() throws Exception {
        Users follower = follower("mia", "미아");
        registerDevice(follower, "token-mia-phone");
        registerDevice(follower, "token-mia-tablet");

        Users stranger = createUser("bob", "밥");
        registerDevice(stranger, "token-bob");

        createPost("팔로워에게만", "PUBLISH");

        awaitPush();

        assertThat(pushedTokens).containsExactlyInAnyOrder("token-mia-phone", "token-mia-tablet");
    }

    /** C15 알림 설정의 유일한 서비스 알림 토글이다 — 여기서 걸러지지 않으면 토글이 장식이 된다. */
    @Test
    @DisplayName("새 게시물 알림을 끈 팔로워는 제외된다")
    void followerWhoTurnedOffPushIsExcluded() throws Exception {
        Users optedOut = follower("nina", "니나");
        optedOut.updateNotificationSettings(false);
        userRepository.save(optedOut);
        registerDevice(optedOut, "token-nina");

        Users optedIn = follower("mia", "미아");
        registerDevice(optedIn, "token-mia");

        createPost("설정을 존중한다", "PUBLISH");

        awaitPush();

        assertThat(pushedTokens).containsExactly("token-mia");
    }

    @Test
    @DisplayName("탈퇴한 팔로워에게는 가지 않는다")
    void withdrawnFollowerIsExcluded() throws Exception {
        Users withdrawn = follower("gone", "떠난이");
        withdrawn.updateStatus(UserStatus.WITHDRAWN);
        userRepository.save(withdrawn);
        registerDevice(withdrawn, "token-gone");

        Users active = follower("mia", "미아");
        registerDevice(active, "token-mia");

        createPost("탈퇴자 제외", "PUBLISH");

        awaitPush();

        assertThat(pushedTokens).containsExactly("token-mia");
    }

    /**
     * 임시저장은 세상에 나온 것이 아니다 — 여기서 알림이 나가면 크리에이터가 다듬는 중인 글이
     * 팔로워에게 먼저 알려진다.
     */
    @Test
    @DisplayName("임시저장은 알림을 보내지 않는다")
    void draftDoesNotPush() throws Exception {
        registerDevice(follower("mia", "미아"), "token-mia");

        createPost("아직 다듬는 중", "DRAFT");

        Thread.sleep(300);
        assertThat(pushedTokens).isEmpty();
    }

    /**
     * 이력의 {@code delivered}는 운영에서 "알림이 안 왔다"는 신고를 받았을 때 되짚는 유일한 근거다.
     *
     * <p>발송이 성공했는데 이 값이 false로 남는 회귀는 눈에 띄지 않는다 — 알림은 잘 가고 있으니
     * 아무도 모르다가, 정작 신고가 들어왔을 때 이력을 믿을 수 없게 된다. 그래서 여기서 못 박는다.
     */
    @Test
    @DisplayName("발송에 성공하면 이력의 전달 여부가 true로 갱신된다")
    void deliveredFlagIsPersisted() throws Exception {
        registerDevice(follower("mia", "미아"), "token-mia");

        Long postId = createPost("이력까지 확인", "PUBLISH");

        awaitPush();
        awaitDelivered(postId);

        List<PostNotificationLog> logs = postNotificationLogRepository.findByPostIdOrderBySentAtDesc(postId);
        assertThat(logs).singleElement().satisfies(entry -> {
            assertThat(entry.getEventType()).isEqualTo(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS);
            assertThat(entry.getDelivered()).isTrue();
        });
    }

    /**
     * 앱을 지운 기기는 FCM이 "없는 토큰"으로 답한다. 지우지 않으면 그 쇼룸이 글을 올릴 때마다
     * 같은 실패를 영원히 반복하고, 팔로워가 늘수록 죽은 토큰 비중이 커져 발송이 느려진다.
     *
     * <p>같은 자리에서 <b>이력 갱신이 살아남는지</b>도 함께 본다. 토큰 정리는 발송을 감싼
     * 트랜잭션 안에서 일어나므로, 정리 쿼리가 영속성 컨텍스트를 비우면 뒤이은 전달 여부 갱신이
     * 조용히 사라진다 — 알림은 갔는데 이력에는 미전달로 남는 회귀다. 죽은 토큰이 있을 때만
     * 나타나기 때문에 이 테스트가 없으면 아무도 모른다.
     */
    @Test
    @DisplayName("FCM이 없는 토큰이라고 답한 기기는 정리되고, 전달 여부는 그대로 기록된다")
    void invalidTokensArePurgedWithoutLosingTheDeliveredFlag() throws Exception {
        Users follower = follower("mia", "미아");
        registerDevice(follower, "token-alive");
        registerDevice(follower, "token-dead");

        given(pushSender.send(anyList(), any())).willAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(0);
            pushedTokens.addAll(tokens);
            pushedMessages.add(invocation.getArgument(1));
            return new PushResult(1, 1, List.of("token-dead"));
        });

        Long postId = createPost("죽은 토큰 정리", "PUBLISH");

        awaitPush();
        awaitDelivered(postId);

        assertThat(deviceTokenRepository.findByToken("token-dead")).isEmpty();
        assertThat(deviceTokenRepository.findByToken("token-alive")).isPresent();

        List<PostNotificationLog> logs = postNotificationLogRepository.findByPostIdOrderBySentAtDesc(postId);
        assertThat(logs).singleElement()
                .satisfies(entry -> assertThat(entry.getDelivered()).isTrue());
    }

    // ------------------------------------------------------------------ 스텝

    private Long createPost(String content, String action) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("images", List.of(image()));
        payload.put("action", action);

        String body = mockMvc.perform(post(CREATOR_POSTS)
                        .header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("postId").asLong();
    }

    /** 발송은 커밋 이후 별도 스레드에서 일어난다 — 요청이 끝났다고 끝난 것이 아니다 */
    private void awaitPush() throws InterruptedException {
        await(() -> !pushedTokens.isEmpty(), "푸시가 발송되지 않았다");
    }

    private void awaitDelivered(Long postId) throws InterruptedException {
        await(() -> postNotificationLogRepository.findByPostIdOrderBySentAtDesc(postId).stream()
                        .anyMatch(PostNotificationLog::getDelivered),
                "이력의 전달 여부가 갱신되지 않았다");
    }

    private void await(java.util.function.BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(AWAIT_INTERVAL_MILLIS);
        }
        throw new AssertionError(message + " (" + AWAIT_TIMEOUT_MILLIS + "ms 대기)");
    }

    // ------------------------------------------------------------------ 픽스처

    private Users follower(String username, String nickname) {
        Users user = createUser(username, nickname);
        creatorFollowRepository.save(new CreatorFollow(user, showroom));
        return user;
    }

    private void registerDevice(Users user, String token) {
        deviceTokenRepository.save(
                new DeviceToken(user, token, DevicePlatform.ANDROID, LocalDateTime.now()));
    }

    private Users createUser(String username, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));
    }

    private Creator createShowroom(String showroomName, String handle) {
        Users owner = createUser("creator-" + handle, showroomName);
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + handle)
                .accountId(handle)
                .followerCount(1000)
                .businessEmail(handle + "@showroomz.test")
                .showroomName(showroomName)
                .build());
    }

    private static Map<String, Object> image() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("imageUrl", CDN + "cover.jpg");
        image.put("originalUrl", CDN + "origin-cover.jpg");
        image.put("width", 1080);
        image.put("height", 1350);
        image.put("fileSize", 2_048_000);
        return image;
    }
}
