package showroomz.api.common.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.NotificationToggle;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.entity.NotificationPushOutbox;
import showroomz.domain.notification.repository.NotificationPushOutboxRepository;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림함 세부(1017 설계서 1-0 · 1-6 · 1-7 · 9절) — 광고성 동의 · 하루 묶음 · 수신자 범위 · 정지 소비자 열람 · 안 읽음 필터.
 * 발생 지점별 문구는 {@link NotificationInboxIntegrationTest}가 본다 — 여기는 저장 · 조회 규칙만.
 */
@DisplayName("1017 알림함 세부 — 동의 · 묶음 · 범위 · 정지 열람")
class NotificationDetailIntegrationTest extends SettlementTestSupport {

    @Autowired private NotificationPublisher publisher;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPushOutboxRepository outboxRepository;

    @Test
    @DisplayName("ND-01 광고성 — 토글이 꺼진 소비자는 행 · 지연 푸시가 없고, 켜면 행 + 지연 푸시(제목 「(광고)」) · 거래 알림은 토글과 무관")
    void advertisingNeedsToggle() {
        publisher.publish(followGroupBuy(consumer));
        assertThat(rows(consumer, NotificationType.FOLLOW_GROUP_BUY_OPENED)).isEmpty();

        consentTo(consumer, NotificationToggle.FOLLOW_GROUP_BUY, true);
        publisher.publish(followGroupBuy(consumer));
        List<Notification> written = rows(consumer, NotificationType.FOLLOW_GROUP_BUY_OPENED);
        assertThat(written).hasSize(1);
        assertThat(written.get(0).isAdvertising()).isTrue();
        NotificationPushOutbox outbox = outboxRepository.findByNotificationId(written.get(0).getId()).orElseThrow();
        assertThat(outbox.getTitle()).startsWith("(광고) ");
        assertThat(outbox.getSentAt()).isNull();

        publisher.publish(NotificationCommand.toUser(consumer.getId(), NotificationType.ORDER_SHIPPED).title("발송되었어요")
                .targetType(NotificationTargetType.ORDER_GROUP).targetId(1L).build());
        assertThat(rows(consumer, NotificationType.ORDER_SHIPPED)).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1); // 거래 알림은 지연 대기열에 오르지 않는다
    }

    @Test
    @DisplayName("ND-02 하루 묶음 — 같은 쇼룸 · 같은 날 3건은 대표 1행 「새 게시물 3개」 · 지연 푸시 1건 · 다른 날은 새 행")
    void digestThreeIntoOne() {
        consentTo(consumer, NotificationToggle.FOLLOW_POST, true);
        String today = LocalDate.now().toString();
        for (int i = 0; i < 3; i++) {
            publisher.publish(followPost(consumer, today));
        }

        List<Notification> written = rows(consumer, NotificationType.FOLLOW_POST_PUBLISHED);
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getDigestCount()).isEqualTo(3);
        assertThat(written.get(0).getTitle()).isEqualTo(creator.getShowroomName() + " 새 게시물 3개");
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll().get(0).getTitle()).endsWith("새 게시물 3개");

        publisher.publish(followPost(consumer, LocalDate.now().plusDays(1).toString()));
        assertThat(rows(consumer, NotificationType.FOLLOW_POST_PUBLISHED)).hasSize(2);
    }

    @Test
    @DisplayName("ND-03 탈퇴 소비자 — 광고성은 동의가 남아 있어도 쓰지 않는다")
    void withdrawnUserGetsNoAdvertising() {
        consentTo(consumer, NotificationToggle.FOLLOW_GROUP_BUY, true);
        jdbc.update("UPDATE USERS SET STATUS = 'WITHDRAWN' WHERE USER_ID = ?", consumer.getId());

        publisher.publish(followGroupBuy(consumer));

        assertThat(rows(consumer, NotificationType.FOLLOW_GROUP_BUY_OPENED)).isEmpty();
    }

    @Test
    @DisplayName("ND-04 정지 소비자 — 알림함 · 읽음 · 배지 확인은 그대로 된다(정지는 거래를 막지 열람을 막지 않는다)")
    void suspendedConsumerCanReadInbox() throws Exception {
        publisher.publish(NotificationCommand.toUser(consumer.getId(), NotificationType.ORDER_SHIPPED).title("발송되었어요")
                .targetType(NotificationTargetType.ORDER_GROUP).targetId(1L).build());
        jdbc.update("UPDATE USERS SET STATUS = 'SUSPENDED' WHERE USER_ID = ?", consumer.getId());

        JsonNode list = json(consumerGet("/v1/user/notifications").andExpect(status().isOk()));
        Long id = list.at("/content/0/notificationId").asLong();
        mockMvc.perform(post("/v1/user/notifications/" + id + "/read").header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/user/notifications/badge/seen").header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ND-05 파트너 범위 — 안 읽음 필터 · 요약 · 모두 읽음은 내 브랜드 행만 · 다른 브랜드 알림 읽음은 404")
    void sellerScope() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "다른랩");
        String otherToken = sellerToken(other.seller());
        publisher.publish(sellerRow(brand.marketId(), "첫 알림"));
        publisher.publish(sellerRow(brand.marketId(), "둘째 알림"));
        publisher.publish(sellerRow(other.marketId(), "다른 브랜드 알림"));

        JsonNode mine = json(sellerGet("/v1/seller/notifications").andExpect(status().isOk()));
        assertThat(mine.get("content")).hasSize(2);
        Long firstId = mine.at("/content/1/notificationId").asLong();
        sellerPost("/v1/seller/notifications/" + firstId + "/read", Map.of()).andExpect(status().isNoContent());
        sellerGet("/v1/seller/notifications?unreadOnly=true").andExpect(jsonPath("$.content.length()").value(1));
        sellerGet("/v1/seller/notifications/summary").andExpect(jsonPath("$.unreadCount").value(1));

        // 다른 브랜드는 내 알림을 읽을 수 없다
        sellerPost("/v1/seller/notifications/" + firstId + "/read", Map.of(), otherToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        sellerPost("/v1/seller/notifications/read-all", Map.of()).andExpect(status().isNoContent());
        sellerGet("/v1/seller/notifications/summary").andExpect(jsonPath("$.unreadCount").value(0));
        sellerGet("/v1/seller/notifications/summary", otherToken).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("ND-06 스튜디오 범위 — 다른 인플루언서는 내 알림을 보지도 읽지도 못한다 · 모두 읽음은 내 행만")
    void creatorScope() throws Exception {
        Creator otherCreator = createCreator("다른쇼룸", "other-room");
        String otherToken = bearerToken(otherCreator.getUser().getUsername(), RoleType.CREATOR, otherCreator.getUser().getId());
        publisher.publish(NotificationCommand.toCreator(creator.getId(), NotificationType.CONTRACT_SIGNATURE_REQUESTED)
                .title("서명을 요청했어요").targetType(NotificationTargetType.CONTRACT).targetId(1L).build());
        publisher.publish(NotificationCommand.toCreator(otherCreator.getId(), NotificationType.CONTRACT_SIGNATURE_REQUESTED)
                .title("다른 인플루언서 알림").targetType(NotificationTargetType.CONTRACT).targetId(2L).build());

        JsonNode mine = json(creatorGet("/v1/creator/notifications").andExpect(status().isOk()));
        assertThat(mine.get("content")).hasSize(1);
        Long id = mine.at("/content/0/notificationId").asLong();
        mockMvc.perform(post("/v1/creator/notifications/" + id + "/read").header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/creator/notifications/read-all").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNoContent());
        creatorGet("/v1/creator/notifications/summary").andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(get("/v1/creator/notifications/summary").header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    // ------------------------------------------------------------------ 픽스처

    private NotificationCommand followGroupBuy(Users user) {
        return NotificationCommand.toUser(user.getId(), NotificationType.FOLLOW_GROUP_BUY_OPENED)
                .title(creator.getShowroomName() + "의 새 공구가 열렸어요").targetType(NotificationTargetType.GROUP_BUY)
                .targetId(groupBuy.getId()).build();
    }

    private NotificationCommand followPost(Users user, String day) {
        return NotificationCommand.toUser(user.getId(), NotificationType.FOLLOW_POST_PUBLISHED)
                .title(creator.getShowroomName() + " 새 게시물").targetType(NotificationTargetType.SHOWROOM)
                .targetId(creator.getId()).dedupKey("FOLLOW_POST:" + user.getId() + ":" + creator.getId() + ":" + day)
                .digest(true).digestTitleFormat(creator.getShowroomName() + " 새 게시물 %d개").build();
    }

    private static NotificationCommand sellerRow(Long marketId, String title) {
        return NotificationCommand.toSeller(marketId, NotificationType.CONTRACT_REVIEW_PASSED).title(title)
                .targetType(NotificationTargetType.CONTRACT).targetId(1L).build();
    }

    private void consentTo(Users user, NotificationToggle toggle, boolean on) {
        transactionTemplate.executeWithoutResult(tx -> userRepository.findById(user.getId()).orElseThrow()
                .updateNotificationToggle(toggle, on, LocalDateTime.now()));
    }

    private List<Notification> rows(Users user, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == NotificationRecipientType.USER && n.getRecipientId().equals(user.getId())
                        && n.getType() == type)
                .toList();
    }

    private ResultActions consumerGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }
}
