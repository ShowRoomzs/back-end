package showroomz.api.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 · 스튜디오 알림 발생 지점(1017 설계서 1-4 · 1-5) — 정산 생성 · 확정, 공구 오픈 · 종료는 양측에 시스템 알림 ·
 * 연결 수락 · 거절은 요청한 브랜드에게 인플루언서 이름으로. 수신자 · 이동 대상 · 행위자만 본다(문구는 화면 몫).
 */
@DisplayName("1017 파트너 · 스튜디오 알림 발생 지점")
class PartnerNotificationIntegrationTest extends SettlementTestSupport {

    @Autowired private NotificationRepository notificationRepository;

    @Test
    @DisplayName("PN-01 정산 생성 · 확정 — 브랜드 · 인플루언서 각 한 건 · 정산 상세로 이동")
    void settlementCreatedAndConfirmed() {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement settlement = generate(LocalDateTime.now().withNano(0));

        assertThat(rows(NotificationRecipientType.SELLER, brand.marketId(), NotificationType.SETTLEMENT_CREATED))
                .singleElement().satisfies(n -> {
                    assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.SETTLEMENT);
                    assertThat(n.getTargetId()).isEqualTo(settlement.getId());
                });
        assertThat(rows(NotificationRecipientType.CREATOR, creator.getId(), NotificationType.SETTLEMENT_CREATED)).hasSize(1);

        confirm(settlement);
        assertThat(rows(NotificationRecipientType.SELLER, brand.marketId(), NotificationType.SETTLEMENT_CONFIRMED)).hasSize(1);
        assertThat(rows(NotificationRecipientType.CREATOR, creator.getId(), NotificationType.SETTLEMENT_CONFIRMED)).hasSize(1);
    }

    @Test
    @DisplayName("PN-02 공구 오픈 · 종료 — 양측에 시스템 알림 · 공구 상세로 이동")
    void groupBuyOpenedAndEnded() {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?", LocalDateTime.now().minusMinutes(1),
                ready.getId());
        assertThat(lifecycleService.open(ready.getId(), LocalDateTime.now())).isTrue();

        List<Notification> opened = rows(NotificationRecipientType.SELLER, brand.marketId(), NotificationType.GROUP_BUY_OPENED);
        assertThat(opened).singleElement().satisfies(n -> {
            assertThat(n.getActorType()).isEqualTo(NotificationActorType.SYSTEM);
            assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.GROUP_BUY);
            assertThat(n.getTargetId()).isEqualTo(ready.getId());
        });
        assertThat(rows(NotificationRecipientType.CREATOR, creator.getId(), NotificationType.GROUP_BUY_OPENED)).hasSize(1);

        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", LocalDateTime.now().minusMinutes(1),
                ready.getId());
        assertThat(lifecycleService.end(ready.getId(), LocalDateTime.now())).isTrue();
        assertThat(rows(NotificationRecipientType.SELLER, brand.marketId(), NotificationType.GROUP_BUY_ENDED)).hasSize(1);
        assertThat(rows(NotificationRecipientType.CREATOR, creator.getId(), NotificationType.GROUP_BUY_ENDED)).hasSize(1);
    }

    @Test
    @DisplayName("PN-03 연결 수락 · 거절 — 요청한 브랜드에게 인플루언서 이름으로 · 수락은 대화방으로 이동")
    void connectionDecisionsNotifyBrand() throws Exception {
        BrandFixture.Brand accepted = fixture.createBrand("accept@showroomz.test", "수락랩");
        BrandFixture.Brand rejected = fixture.createBrand("reject@showroomz.test", "거절랩");
        Connection toAccept = connectionRepository.save(Connection.requestPair(accepted.market(), creator));
        Connection toReject = connectionRepository.save(Connection.requestPair(rejected.market(), creator));

        mockMvc.perform(post("/v1/creator/connections/" + toAccept.getId() + "/accept")
                .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/v1/creator/connections/" + toReject.getId() + "/reject")
                .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().is2xxSuccessful());

        assertThat(rows(NotificationRecipientType.SELLER, accepted.marketId(), NotificationType.CONNECTION_ACCEPTED))
                .singleElement().satisfies(n -> {
                    assertThat(n.getActorType()).isEqualTo(NotificationActorType.CREATOR);
                    assertThat(n.getActorName()).isEqualTo(creator.getShowroomName());
                    assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.THREAD);
                });
        assertThat(rows(NotificationRecipientType.SELLER, rejected.marketId(), NotificationType.CONNECTION_REJECTED)).hasSize(1);
        assertThat(rows(NotificationRecipientType.SELLER, accepted.marketId(), NotificationType.CONNECTION_REJECTED)).isEmpty();
    }

    private List<Notification> rows(NotificationRecipientType recipientType, Long recipientId, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == recipientType && n.getRecipientId().equals(recipientId)
                        && n.getType() == type)
                .toList();
    }
}
