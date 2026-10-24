package showroomz.api.common.notification;

import static org.hamcrest.Matchers.hasItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.history.repository.UserConsentHistoryRepository;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.NotificationToggle;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationPushOutboxRepository;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.service.NotificationBroadcastService;
import showroomz.domain.notification.service.NotificationDeadlineNoticeService;
import showroomz.domain.notification.service.NotificationMaintenanceService;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderDeliveryGroup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1017 설계서 P2 · P3 — 알림함(파트너센터 16 · 쇼룸 스튜디오 14 H1a · 소비자 C11) · 알림 설정(C15).
 *
 * <ul>
 *   <li>NT-01 파트너 — 운영자 계약 반려가 알림함에 「운영팀」 줄로 · 안 읽은 수 · 읽음 · 모두 읽음</li>
 *   <li>NT-02 스튜디오 — 브랜드 연장 요청이 「공구」 분류 · 브랜드 이름으로 · 내가 한 일은 오지 않는다</li>
 *   <li>NT-03 소비자 — 발송 알림(썸네일 · 굵게) · 홈 종 배지 · 목록 진입 초기화 · 다른 사람 알림 읽음 404</li>
 *   <li>NT-04 광고성 — 팔로우 쇼룸 새 공구는 토글을 켠 팔로워에게만 · 지연 푸시 적재</li>
 *   <li>NT-05 C15 — 토글 4행 · 켜면 동의 일시 · 동의 이력 · 처리 결과 알림</li>
 *   <li>NT-06 정지 — 본인 정지 알림은 운영팀 채널 · 상대는 사유 없이 「상대 계정 사정」</li>
 *   <li>NT-07 D-1 — 공구 마감 임박(장바구니) · 두 번 돌아도 한 건 · 서명 기한 D-1</li>
 *   <li>NT-08 보관 · 약관 개정 공지</li>
 * </ul>
 */
@DisplayName("1017 P2 · P3 — 알림함 · 알림 설정")
class NotificationInboxIntegrationTest extends SettlementTestSupport {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPushOutboxRepository outboxRepository;
    @Autowired private NotificationDeadlineNoticeService deadlineNoticeService;
    @Autowired private NotificationMaintenanceService maintenanceService;
    @Autowired private NotificationBroadcastService broadcastService;
    @Autowired private CreatorFollowRepository creatorFollowRepository;
    @Autowired private UserConsentHistoryRepository consentHistoryRepository;

    // ------------------------------------------------------------------ NT-01 파트너

    @Test
    @DisplayName("NT-01 파트너 — 계약 반려는 「운영팀」 줄 · 계약 상세로 이동 · 안 읽은 수 · 읽음 · 모두 읽음")
    void sellerInbox() throws Exception {
        Contract contract = seedReviewPending();
        adminPost("/v1/admin/contracts/" + contract.getId() + "/review/reject",
                Map.of("reasonCode", "INFO_MISMATCH", "reasonDetail", "리워드율 표기 확인 필요"))
                .andExpect(status().isOk());

        sellerGet("/v1/seller/notifications/summary").andExpect(jsonPath("$.unreadCount").value(1));
        String body = sellerGet("/v1/seller/notifications").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CONTRACT_REVIEW_REJECTED"))
                .andExpect(jsonPath("$.content[0].category").value("계약"))
                .andExpect(jsonPath("$.content[0].actorLabel").value("운영팀"))
                .andExpect(jsonPath("$.content[0].title").value(containsString("리워드율 표기 확인 필요")))
                .andExpect(jsonPath("$.content[0].target.type").value("CONTRACT"))
                .andExpect(jsonPath("$.content[0].target.id").value(contract.getId()))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).at("/content/0/notificationId").asLong();

        sellerPost("/v1/seller/notifications/" + id + "/read", Map.of()).andExpect(status().isNoContent());
        sellerGet("/v1/seller/notifications/summary").andExpect(jsonPath("$.unreadCount").value(0));
        sellerGet("/v1/seller/notifications?unreadOnly=true").andExpect(jsonPath("$.content.length()").value(0));

        // 스튜디오에는 오지 않는다(반려는 브랜드에게만)
        creatorGet("/v1/creator/notifications").andExpect(jsonPath("$.content.length()").value(0));

        adminPost("/v1/admin/brands/" + brand.marketId() + "/suspend", Map.of("reason", "REPEATED_SHIPPING_DELAY"))
                .andExpect(status().isOk());
        sellerGet("/v1/seller/notifications/summary").andExpect(jsonPath("$.unreadCount").value(1));
        sellerPost("/v1/seller/notifications/read-all", Map.of()).andExpect(status().isNoContent());
        sellerGet("/v1/seller/notifications/summary").andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ------------------------------------------------------------------ NT-02 스튜디오

    @Test
    @DisplayName("NT-02 스튜디오 — 브랜드 연장 요청은 「공구」 · 브랜드 이름 · 공구 상세 / 요청한 브랜드에게는 오지 않는다")
    void creatorInbox() throws Exception {
        sellerPost("/v1/seller/group-buys/" + groupBuy.getId() + "/extension-request", Map.of("extensionDays", 3))
                .andExpect(status().isOk());

        creatorGet("/v1/creator/notifications").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("GROUP_BUY_EXTENSION_REQUESTED"))
                .andExpect(jsonPath("$.content[0].category").value("공구"))
                .andExpect(jsonPath("$.content[0].actorType").value("BRAND"))
                .andExpect(jsonPath("$.content[0].actorLabel").value(brand.market().getMarketName()))
                .andExpect(jsonPath("$.content[0].target.type").value("GROUP_BUY"))
                .andExpect(jsonPath("$.content[0].target.id").value(groupBuy.getId()));
        creatorGet("/v1/creator/notifications/summary").andExpect(jsonPath("$.unreadCount").value(1));
        sellerGet("/v1/seller/notifications").andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------------ NT-03 소비자

    @Test
    @DisplayName("NT-03 소비자 — 발송 알림(썸네일 · 굵게 · 주문 상세) · 홈 종 배지 · 목록 진입 초기화 · 남의 알림 404")
    void consumerInbox() throws Exception {
        OrderDeliveryGroup group = preparing(paidGroup());
        registerShipment(group, "CJ", nextTrackingNumber()).andExpect(status().isOk());

        consumerGet("/v1/user/home/summary").andExpect(jsonPath("$.notificationBadgeCount").value(1));
        String body = consumerGet("/v1/user/notifications").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("ORDER_SHIPPED"))
                .andExpect(jsonPath("$.content[0].imageKind").value("THUMB"))
                .andExpect(jsonPath("$.content[0].title").value(containsString("발송되었어요")))
                .andExpect(jsonPath("$.content[0].emphasis").isNotEmpty())
                .andExpect(jsonPath("$.content[0].target.type").value("ORDER_GROUP"))
                .andExpect(jsonPath("$.content[0].target.id").value(group.getId()))
                .andExpect(jsonPath("$.content[0].advertising").value(false))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).at("/content/0/notificationId").asLong();

        consumerPost("/v1/user/notifications/badge/seen").andExpect(status().isNoContent());
        consumerGet("/v1/user/home/summary").andExpect(jsonPath("$.notificationBadgeCount").value(0));
        // 배지는 0이어도 로즈 점(읽음)은 탭해야 사라진다
        consumerGet("/v1/user/notifications").andExpect(jsonPath("$.content[0].read").value(false));
        consumerPost("/v1/user/notifications/" + id + "/read").andExpect(status().isNoContent());
        consumerGet("/v1/user/notifications").andExpect(jsonPath("$.content[0].read").value(true));

        // 다른 소비자는 이 알림을 읽을 수 없다
        Users other = createConsumer("other-consumer", "다른소비자");
        mockMvc.perform(post("/v1/user/notifications/" + id + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other.getUsername(),
                                showroomz.api.app.auth.entity.RoleType.USER, other.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ NT-04 광고성

    @Test
    @DisplayName("NT-04 팔로우 쇼룸 새 공구 — 토글을 켠 팔로워에게만 · 광고 표기 · 지연 푸시 적재 · 같은 공구 두 번 열려도 한 건")
    void followGroupBuyOpenedRespectsConsent() throws Exception {
        Users optedIn = createConsumer("opted-in", "동의함");
        Users optedOut = createConsumer("opted-out", "미동의");
        follow(optedIn);
        follow(optedOut);
        consentTo(optedIn, NotificationToggle.FOLLOW_GROUP_BUY, true);

        var next = seed(brand, creator, "새 공구", LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(5));
        moveTo(next.getId(), showroomz.domain.groupbuy.type.GroupBuyStatus.READY);
        assertThat(lifecycleService.open(next.getId(), LocalDateTime.now())).isTrue();

        List<Notification> rows = userRows(NotificationType.FOLLOW_GROUP_BUY_OPENED);
        assertThat(rows).singleElement().satisfies(n -> {
            assertThat(n.getRecipientId()).isEqualTo(optedIn.getId());
            assertThat(n.isAdvertising()).isTrue();
            assertThat(n.getTitle()).contains("새 공구가 열렸어요");
        });
        assertThat(outboxRepository.findAll()).hasSize(1);
    }

    // ------------------------------------------------------------------ NT-05 C15

    @Test
    @DisplayName("NT-05 C15 — 토글 4행 · 켜면 수신 동의 일시 · 동의 이력 · 처리 결과 알림 / 같은 값 재전송은 이력 없음")
    void notificationSettings() throws Exception {
        consumerGet("/v1/user/settings/notifications").andExpect(status().isOk())
                .andExpect(jsonPath("$.toggles.length()").value(4))
                .andExpect(jsonPath("$.toggles[0].key").value("FOLLOW_GROUP_BUY"))
                .andExpect(jsonPath("$.toggles[0].label").value("팔로우 쇼룸 새 공구"))
                .andExpect(jsonPath("$.toggles[3].key").value("BENEFIT"));

        consumerPatch("/v1/user/settings/notifications",
                Map.of("followGroupBuyPushAgree", true, "groupBuyEndingPushAgree", true))
                .andExpect(status().isNoContent());
        consumerGet("/v1/user/settings/notifications")
                .andExpect(jsonPath("$.followGroupBuyPushAgree").value(true))
                .andExpect(jsonPath("$.toggles[0].enabled").value(true))
                .andExpect(jsonPath("$.toggles[0].agreedAt").isNotEmpty())
                .andExpect(jsonPath("$.toggles[2].enabled").value(true));

        long histories = consentHistoryRepository.findAll().stream()
                .filter(h -> h.getConsentType() == ConsentType.MARKETING_FOLLOW_GROUP_BUY
                        || h.getConsentType() == ConsentType.MARKETING_GROUP_BUY_ENDING).count();
        assertThat(histories).isEqualTo(2);
        assertThat(userRows(NotificationType.MARKETING_CONSENT_CHANGED)).singleElement()
                .satisfies(n -> assertThat(n.getTitle()).contains("팔로우 쇼룸 새 공구 수신에 동의")
                        .contains("공구 마감 임박 수신에 동의"));

        consumerPatch("/v1/user/settings/notifications", Map.of("followGroupBuyPushAgree", true))
                .andExpect(status().isNoContent());
        assertThat(userRows(NotificationType.MARKETING_CONSENT_CHANGED)).hasSize(1);
    }

    // ------------------------------------------------------------------ NT-06 정지

    @Test
    @DisplayName("NT-06 인플루언서 정지 — 본인은 운영팀 채널 정지 카드로 · 상대 브랜드는 사유 없이 「상대 인플루언서 계정 사정」")
    void sanctionNotifications() throws Exception {
        Contract signing = seedSigning();
        adminPost("/v1/admin/creators/" + creator.getId() + "/suspend",
                Map.of("reason", "AD_LAW_VIOLATION_REPEATED", "reasonDetail", "효능 표현"))
                .andExpect(status().isOk());

        creatorGet("/v1/creator/notifications")
                .andExpect(jsonPath("$.content[?(@.type == 'ACCOUNT_SUSPENDED')].target.type").value("THREAD"));
        sellerGet("/v1/seller/notifications").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'COUNTERPARTY_HOLD_STARTED' && @.target.id == "
                        + signing.getId() + ")].title").value(hasItem(containsString("상대 인플루언서 계정 사정"))))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString())
                        .doesNotContain("표시광고법").doesNotContain("효능 표현"));

        adminPost("/v1/admin/creators/" + creator.getId() + "/release", Map.of("reason", "RESOLVED"))
                .andExpect(status().isOk());
        sellerGet("/v1/seller/notifications")
                .andExpect(jsonPath("$.content[?(@.type == 'COUNTERPARTY_HOLD_RESUMED')]").isNotEmpty());
    }

    // ------------------------------------------------------------------ NT-07 D-1

    @Test
    @DisplayName("NT-07 D-1 — 공구 마감 임박은 장바구니에 담고 토글을 켠 소비자에게 · 두 번 돌아도 한 건 · 서명 기한 D-1 은 미서명 당사자에게")
    void deadlineNotices() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                now.toLocalDate().plusDays(1).atTime(23, 55), groupBuy.getId());
        cartItem(creamVariant, 1);
        consentTo(consumer, NotificationToggle.GROUP_BUY_ENDING, true);

        deadlineNoticeService.noticeGroupBuyEnding(now);
        deadlineNoticeService.noticeGroupBuyEnding(now);
        assertThat(userRows(NotificationType.GROUP_BUY_ENDING_D1)).singleElement()
                .satisfies(n -> assertThat(n.getTitle()).startsWith("장바구니에 담은 ").endsWith("공구가 내일 마감돼요"));

        Contract signing = seedSigning();
        jdbc.update("UPDATE contract SET signature_deadline_at = ?, brand_signed_at = ? WHERE contract_id = ?",
                now.toLocalDate().plusDays(1).atTime(18, 0), now.minusHours(1), signing.getId());
        deadlineNoticeService.noticeSignatureDeadline(now);
        creatorGet("/v1/creator/notifications")
                .andExpect(jsonPath("$.content[?(@.type == 'CONTRACT_SIGN_DEADLINE_D1')]").isNotEmpty());
        sellerGet("/v1/seller/notifications")
                .andExpect(jsonPath("$.content[?(@.type == 'CONTRACT_SIGN_DEADLINE_D1')]").isEmpty());
    }

    // ------------------------------------------------------------------ NT-08 보관 · 약관

    @Test
    @DisplayName("NT-08 보관 — 파트너 30일 · 소비자 90일 지난 행 정리 / 약관 개정은 전 소비자 알림함에 한 줄")
    void retentionAndTerms() throws Exception {
        broadcastService.termsChanged(1L, "개인정보 처리방침", LocalDate.of(2026, 11, 1));
        consumerGet("/v1/user/notifications")
                .andExpect(jsonPath("$.content[0].type").value("TERMS_CHANGED"))
                .andExpect(jsonPath("$.content[0].title").value("개인정보 처리방침이 11.01부터 바뀌어요"));

        sellerPost("/v1/seller/group-buys/" + groupBuy.getId() + "/extension-request", Map.of("extensionDays", 3))
                .andExpect(status().isOk());
        jdbc.update("UPDATE notification SET created_at = ? WHERE recipient_type = 'CREATOR'",
                LocalDateTime.now().minusDays(31));
        jdbc.update("UPDATE notification SET created_at = ? WHERE recipient_type = 'USER'",
                LocalDateTime.now().minusDays(31));
        creatorGet("/v1/creator/notifications").andExpect(jsonPath("$.content.length()").value(0));

        maintenanceService.purge(LocalDateTime.now());
        assertThat(notificationRepository.findAll()).allMatch(n -> n.getRecipientType() == NotificationRecipientType.USER);
        consumerGet("/v1/user/notifications").andExpect(jsonPath("$.content[0].type").value("TERMS_CHANGED"))
                .andExpect(jsonPath("$.content[0].title").value(not("")));
    }

    // ------------------------------------------------------------------ helpers

    private Contract seedReviewPending() {
        return seedContract(ContractStatus.REVIEW_PENDING);
    }

    private Contract seedSigning() {
        return seedContract(ContractStatus.SIGNING);
    }

    private Contract seedContract(ContractStatus status) {
        Contract saved = transactionTemplate.execute(tx -> contractRepository.saveAndFlush(Contract.createDraft(
                marketRepository.findById(brand.marketId()).orElseThrow(),
                creatorRepository.findById(creator.getId()).orElseThrow(), connection)));
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE contract SET status = ?, title = ?, review_requested_at = ?, signature_requested_at = ?, "
                        + "signature_deadline_at = ? WHERE contract_id = ?",
                status.name(), "아이크림 신제품 공구", now.minusDays(1),
                status == ContractStatus.SIGNING ? now.minusHours(5) : null,
                status == ContractStatus.SIGNING ? now.plusDays(5) : null, saved.getId());
        return contractRepository.findById(saved.getId()).orElseThrow();
    }

    private void follow(Users user) {
        transactionTemplate.executeWithoutResult(tx -> creatorFollowRepository.save(new CreatorFollow(
                userRepository.findById(user.getId()).orElseThrow(),
                creatorRepository.findById(creator.getId()).orElseThrow())));
    }

    private void consentTo(Users user, NotificationToggle toggle, boolean on) {
        transactionTemplate.executeWithoutResult(tx -> userRepository.findById(user.getId()).orElseThrow()
                .updateNotificationToggle(toggle, on, LocalDateTime.now()));
    }

    private List<Notification> userRows(NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == NotificationRecipientType.USER && n.getType() == type).toList();
    }

    private ResultActions consumerGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions consumerPost(String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions consumerPatch(String url, Object body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
