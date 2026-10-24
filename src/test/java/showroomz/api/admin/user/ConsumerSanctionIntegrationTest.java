package showroomz.api.admin.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.UserAdminMemo;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserAdminMemoRepository;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.service.UserSanctionService;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.entity.DeviceToken;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;
import showroomz.domain.notification.type.DevicePlatform;
import showroomz.global.config.properties.MemberProperties;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 51-6 소비자 계정 이용 정지(C20) · 어드민 소비자 제재 · 메모 · 이력 — 통합 테스트(설계서 12절).
 *
 * <p>정지는 「로그인 허용 · 새로 벌이는 일만 차단」이다(§51-1). 그래서 화면이 끄는 버튼마다 <b>서버도 막는지</b>, 그리고 진행 중 주문 ·
 * 1:1 문의처럼 막으면 안 되는 길이 <b>열려 있는지</b>를 함께 본다. 앱의 「1회 노출」 두 가지와 정지 중 탈퇴 → 재가입의 정지 복귀도 본다.
 */
@DisplayName("[통합] 51-6 소비자 계정 이용 정지 · 어드민 제재 · 메모 · 이력")
class ConsumerSanctionIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN = "/v1/admin/users";
    private static final String ACCOUNT_STATUS = "/v1/user/account-status";
    private static final String INQUIRIES = "/v1/user/inquiries";
    private static final String WITHDRAWAL_INFO = "/v1/user/withdrawal";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSanctionRepository sanctionRepository;
    @Autowired
    private UserStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private UserAdminMemoRepository memoRepository;
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;
    @Autowired
    private UserSanctionService sanctionService;
    @Autowired
    private MemberProperties memberProperties;

    /** 소비자 정지 · 해제 알림은 실제로 보낸다(0-10) — 발송 여부와 문구를 본다. */
    @MockitoBean
    private PushSender pushSender;

    private Seller admin;
    private String adminToken;
    private String otherAdminToken;
    private Users mia;
    private String miaToken;

    @BeforeEach
    void setUpConsumer() {
        admin = fixture.createAdmin("admin@showroomz.test", "김운영");
        adminToken = adminToken(admin);
        otherAdminToken = adminToken(fixture.createAdmin("admin2@showroomz.test", "박운영"));
        mia = createUser("mia", "미아", RoleType.USER, UserStatus.NORMAL);
        miaToken = bearerToken(mia.getUsername(), RoleType.USER, mia.getId());
        deviceTokenRepository.save(new DeviceToken(mia, "mia-device-token", DevicePlatform.ANDROID, LocalDateTime.now()));
        given(pushSender.send(anyList(), any())).willReturn(new PushResult(1, 0, List.of()));
    }

    // ------------------------------------------------------------------ 정지 · 해제

    @Test
    @DisplayName("정지하면 정지 행 · 회원 상태 · 전이 로그 · 처리 이력이 함께 바뀌고 정지 푸시가 나간다")
    void suspendRecordsSanctionAndNotifies() throws Exception {
        suspend(mia, "ABUSIVE_LANGUAGE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCard.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusCard.sanction.reason").value("ABUSIVE_LANGUAGE"))
                .andExpect(jsonPath("$.statusCard.sanction.reasonLabel").value("욕설·비방"))
                .andExpect(jsonPath("$.statusCard.sanction.suspendedByName").value("김운영"))
                .andExpect(jsonPath("$.statusCard.actions.canSuspend").value(false))
                .andExpect(jsonPath("$.statusCard.actions.canRelease").value(true))
                .andExpect(jsonPath("$.histories.content[0].eventType").value("SUSPENDED"))
                .andExpect(jsonPath("$.histories.content[0].actorName").value("김운영"));

        assertThat(userRepository.findById(mia.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(sanctionRepository.findByUserIdAndReleasedAtIsNull(mia.getId())).isPresent();
        List<UserStatusHistory> transitions = statusHistoryRepository.findByUser_IdOrderByCreatedAtDesc(mia.getId());
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).getPreviousStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(transitions.get(0).getNewStatus()).isEqualTo(UserStatus.SUSPENDED);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, atLeastOnce()).send(anyList(), message.capture());
        assertThat(message.getValue().title()).isEqualTo("계정 이용이 정지되었어요");
        assertThat(message.getValue().data()).containsEntry("type", "ACCOUNT_STATUS").containsEntry("event", "SUSPENDED");
    }

    @Test
    @DisplayName("기타 사유는 상세가 필수이고, 상세 사유는 앱에 내려가지 않는다")
    void otherReasonNeedsDetailButAppNeverSeesIt() throws Exception {
        suspend(mia, "OTHER", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_SUSPEND_DETAIL_REQUIRED"));

        suspend(mia, "OTHER", "고객센터 상담원에게 반복적인 폭언").andExpect(status().isOk());

        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspension.reasonCode").value("OTHER"))
                .andExpect(jsonPath("$.suspension.reasonLabel").value("기타"))
                .andExpect(jsonPath("$.suspension.reasonDetail").doesNotExist());
    }

    @Test
    @DisplayName("이중 정지 · 탈퇴 회원 · 소비자가 아닌 계정은 정지할 수 없다")
    void suspendRejectsWrongTargets() throws Exception {
        suspend(mia, "RETURN_ABUSE", null).andExpect(status().isOk());
        suspend(mia, "RETURN_ABUSE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_SUSPENDED"));

        Users withdrawn = createUser("gone", "떠난이", RoleType.USER, UserStatus.WITHDRAWN);
        suspend(withdrawn, "RETURN_ABUSE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SANCTION_WITHDRAWN"));

        // 인플루언서는 creator.status 축으로 §53 이 정지한다 — users.status 를 바꾸면 쇼룸이 사라진다(0-1)
        Users creator = createUser("jenny", "제니", RoleType.CREATOR, UserStatus.NORMAL);
        suspend(creator, "RETURN_ABUSE", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("해제는 사유가 필수이고, 해제하면 앱이 토스트를 한 번만 띄운다")
    void releaseRequiresReasonAndShowsToastOnce() throws Exception {
        suspend(mia, "MULTIPLE_REPORTS", null).andExpect(status().isOk());

        release(mia, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_RELEASE_REASON_REQUIRED"));
        release(mia, "WITHDRAWN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT")); // 브랜드 · 인플루언서 전용 「탈퇴 종결」은 소비자 enum 에 없다

        clearInvocations(pushSender);
        release(mia, "RESOLVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCard.status").value("NORMAL"))
                .andExpect(jsonPath("$.statusCard.sanction").doesNotExist())
                .andExpect(jsonPath("$.statusCard.actions.canSuspend").value(true))
                .andExpect(jsonPath("$.histories.content[0].eventType").value("SUSPENSION_RELEASED"))
                .andExpect(jsonPath("$.histories.content[0].detail").value("소명 확인 완료"));

        UserSanction released = sanctionRepository.findFirstByUserIdOrderBySuspendedAtDescIdDesc(mia.getId()).orElseThrow();
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(released.getReleasedByName()).isEqualTo("김운영");
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(anyList(), message.capture());
        assertThat(message.getValue().title()).isEqualTo("계정 이용 정지가 해제되었어요");

        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.status").value("NORMAL"))
                .andExpect(jsonPath("$.suspension").doesNotExist())
                .andExpect(jsonPath("$.releaseNotice.releasedAt").exists());
        mockMvc.perform(post(ACCOUNT_STATUS + "/release-notice/ack").header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.releaseNotice").doesNotExist());

        release(mia, "RESOLVED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_SUSPENDED"));
    }

    @Test
    @DisplayName("정지 안내 시트는 확인하면 다시 뜨지 않는다")
    void suspensionNoticeIsShownOnce() throws Exception {
        suspend(mia, "ABUSIVE_LANGUAGE", null).andExpect(status().isOk());

        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspension.reasonLabel").value("욕설·비방"))
                .andExpect(jsonPath("$.suspension.suspendedAt").exists())
                .andExpect(jsonPath("$.suspension.noticePending").value(true));

        mockMvc.perform(post(ACCOUNT_STATUS + "/suspension-notice/ack").header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.suspension.noticePending").value(false));
    }

    @Test
    @DisplayName("사유 기록 없이 정지된 옛 회원도 앱에 정지로 보이고, 어드민이 풀 수 있다")
    void legacySuspendedUserWithoutSanctionRow() throws Exception {
        Users legacy = createUser("legacy", "옛정지", RoleType.USER, UserStatus.SUSPENDED);
        String legacyToken = bearerToken(legacy.getUsername(), RoleType.USER, legacy.getId());

        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, legacyToken))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspension.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.suspension.noticePending").value(false));

        mockMvc.perform(get(ADMIN + "/" + legacy.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCard.sanction").doesNotExist())
                .andExpect(jsonPath("$.statusCard.actions.canRelease").value(true))
                .andExpect(jsonPath("$.adminMemo").doesNotExist())
                .andExpect(jsonPath("$.statusHistory").doesNotExist());

        release(legacy, "MISTAKE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCard.status").value("NORMAL"));
    }

    // ------------------------------------------------------------------ 앱 차단 가드

    @Test
    @DisplayName("정지 중에는 구매 · 상품 문의 · 팔로우 · 좋아요 · 신고 · 프로필 변경을 서버가 409 로 막는다")
    void suspendedConsumerIsBlockedFromNewActions() throws Exception {
        suspend(mia, "ABUSIVE_LANGUAGE", null).andExpect(status().isOk());

        Map<MockHttpServletRequestBuilder, String> blocked = Map.of(
                post("/v1/user/cart").content("[{\"productId\":1,\"variantId\":1,\"groupBuyId\":1,\"quantity\":1}]"),
                "이용 정지 중에는 장바구니에 담을 수 없어요.",
                post("/v1/user/orders/checkout").content("{\"cartItemIds\":[1]}"),
                "이용 정지 중에는 구매할 수 없어요.",
                post("/v1/user/products/999/inquiries").content("{\"type\":\"OPTION\",\"content\":\"민감성인데 아침에도 써도 될까요?\"}"),
                "이용 정지 중에는 문의할 수 없어요.",
                post("/v1/user/showrooms/999/follow"),
                "이용 정지 중에는 팔로우할 수 없어요.",
                post("/v1/user/wishlist/999"),
                "이용 정지 중에는 좋아요를 누를 수 없어요.",
                post("/v1/user/showrooms/posts/999/wishlist"),
                "이용 정지 중에는 좋아요를 누를 수 없어요.",
                post("/v1/user/posts/999/reports").content("{\"reasonCode\":\"SPAM\"}"),
                "이용 정지 중에는 게시물을 신고할 수 없어요.",
                patch("/v1/user/me").content("{\"nickname\":\"새닉네임\"}"),
                "이용 정지 중에는 닉네임 · 프로필 사진을 바꿀 수 없어요.");

        for (Map.Entry<MockHttpServletRequestBuilder, String> entry : blocked.entrySet()) {
            mockMvc.perform(entry.getKey().contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, miaToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USER_SUSPENDED"))
                    .andExpect(jsonPath("$.message").value(entry.getValue()));
        }
    }

    @Test
    @DisplayName("정지 중에도 1:1 문의 · 바꿀 것 없는 프로필 저장은 그대로 된다 — 소명 창구가 막히면 안 된다")
    void suspendedConsumerKeepsAllowedActions() throws Exception {
        suspend(mia, "ABUSIVE_LANGUAGE", null).andExpect(status().isOk());

        mockMvc.perform(post(INQUIRIES).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{\"type\":\"DELIVERY\",\"content\":\"지난주 주문 배송이 언제 오나요?\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/v1/user/me").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 1:1 문의 소명 유형

    @Test
    @DisplayName("정지 소명 문의는 정지 중에만 · 유형 계정 · 주문 없이 · 답변 전 1건이고, 어드민 상태 카드에 소명 문의로 붙는다")
    void sanctionAppealInquiry() throws Exception {
        appeal(miaToken, "{\"type\":\"ACCOUNT\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANCTION_APPEAL_NOT_AVAILABLE"));

        suspend(mia, "ABUSIVE_LANGUAGE", null).andExpect(status().isOk());

        appeal(miaToken, "{\"type\":\"ACCOUNT\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true,\"orderId\":1}")
                .andExpect(status().isBadRequest());
        appeal(miaToken, "{\"type\":\"DELIVERY\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true}")
                .andExpect(status().isBadRequest());

        JsonNode created = objectMapper.readTree(
                appeal(miaToken, "{\"type\":\"ACCOUNT\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true}")
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());
        long inquiryId = created.get("inquiryId").asLong();

        appeal(miaToken, "{\"type\":\"ACCOUNT\",\"content\":\"한 번 더 남깁니다\",\"sanctionAppeal\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANCTION_APPEAL_ALREADY_EXISTS"));

        mockMvc.perform(get(ACCOUNT_STATUS).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.suspension.pendingAppealInquiryId").value(inquiryId));

        mockMvc.perform(get(INQUIRIES + "/" + inquiryId).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ACCOUNT"))
                .andExpect(jsonPath("$.typeName").value("계정 이용 정지"))
                .andExpect(jsonPath("$.sanctionAppeal").value(true));

        mockMvc.perform(get(ADMIN + "/" + mia.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.statusCard.sanction.appealInquiries[0].inquiryId").value(inquiryId))
                .andExpect(jsonPath("$.statusCard.sanction.appealInquiries[0].answered").value(false));

        mockMvc.perform(get("/v1/admin/inquiries/" + inquiryId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeName").value("계정 이용 정지"))
                .andExpect(jsonPath("$.sanctionAppeal").value(true));
    }

    // ------------------------------------------------------------------ 탈퇴 · 재가입

    @Test
    @DisplayName("정지 중에도 탈퇴할 수 있고, 같은 계정으로 돌아오면 정지로 복귀해 안내 시트를 다시 본다")
    void suspensionSurvivesWithdrawalAndRejoin() throws Exception {
        suspend(mia, "PAYMENT_FRAUD_SUSPECTED", null).andExpect(status().isOk());
        mockMvc.perform(post(ACCOUNT_STATUS + "/suspension-notice/ack").header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/v1/user/auth/withdraw").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{\"agreeConsent\":true}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(userRepository.findById(mia.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(sanctionRepository.findByUserIdAndReleasedAtIsNull(mia.getId())).isPresent();

        // 탈퇴 회원에는 운영자 조치가 없다(§25-5)
        release(mia, "RESOLVED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SANCTION_WITHDRAWN"));

        // SocialLoginService.loginOrSignup 의 탈퇴 회원 복구 분기가 부르는 지점
        UserStatus restored = transactionTemplate.execute(status ->
                sanctionService.restoreOnRejoin(userRepository.findById(mia.getId()).orElseThrow()));
        assertThat(restored).isEqualTo(UserStatus.SUSPENDED);
        assertThat(userRepository.findById(mia.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(sanctionRepository.findByUserIdAndReleasedAtIsNull(mia.getId()).orElseThrow().isNoticePending()).isTrue();
        assertThat(statusHistoryRepository.findByUser_IdOrderByCreatedAtDesc(mia.getId()))
                .anySatisfy(h -> {
                    assertThat(h.getPreviousStatus()).isEqualTo(UserStatus.WITHDRAWN);
                    assertThat(h.getNewStatus()).isEqualTo(UserStatus.SUSPENDED);
                    assertThat(h.getReason()).isEqualTo(UserSanctionService.REJOIN_REASON);
                });
    }

    @Test
    @DisplayName("정지 없이 탈퇴한 회원은 재가입하면 정상으로 돌아온다")
    void rejoinWithoutSanctionRestoresNormal() {
        Users gone = createUser("gone", "떠난이", RoleType.USER, UserStatus.WITHDRAWN);

        UserStatus restored = transactionTemplate.execute(status ->
                sanctionService.restoreOnRejoin(userRepository.findById(gone.getId()).orElseThrow()));

        assertThat(restored).isEqualTo(UserStatus.NORMAL);
    }

    @Test
    @DisplayName("55 계획서 S11 — 정지 중 탈퇴 설정을 끄면 탈퇴 화면 withdrawable false + 사유 · 탈퇴 409 USER_SUSPENDED · 켜면 탈퇴된다")
    void suspendedWithdrawalFollowsSetting() throws Exception {
        suspend(mia, "PAYMENT_FRAUD_SUSPECTED", null).andExpect(status().isOk());
        String message = "이용 정지 중에는 탈퇴할 수 없어요.";

        memberProperties.getSanction().setAllowWithdrawalWhileSuspended(false);
        try {
            mockMvc.perform(get(WITHDRAWAL_INFO).header(HttpHeaders.AUTHORIZATION, miaToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.withdrawable").value(false))
                    .andExpect(jsonPath("$.suspensionBlockedReason").value(message));
            withdraw(miaToken).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USER_SUSPENDED"))
                    .andExpect(jsonPath("$.message").value(message));
        } finally {
            memberProperties.getSanction().setAllowWithdrawalWhileSuspended(true);
        }
        assertThat(userRepository.findById(mia.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);

        mockMvc.perform(get(WITHDRAWAL_INFO).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.withdrawable").value(true))
                .andExpect(jsonPath("$.suspensionBlockedReason").doesNotExist());
        withdraw(miaToken).andExpect(status().is2xxSuccessful());
        assertThat(userRepository.findById(mia.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }

    // ------------------------------------------------------------------ 모달 사유 목록(55 계획서 M8)

    @Test
    @DisplayName("사유 목록 전수 — 정지 6종(기타만 상세 필수 · 지급 정지 없음) · 해제 3종(TERM_ENDED 유지 · 상세란 없음)")
    void reasonOptions() throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(get(ADMIN + "/reason-options")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(texts(body.path("suspensionReasons"), "code")).containsExactly("ABUSIVE_LANGUAGE", "RETURN_ABUSE",
                "FRAUDULENT_ORDER_SUSPECTED", "PAYMENT_FRAUD_SUSPECTED", "MULTIPLE_REPORTS", "OTHER");
        assertThat(texts(body.path("suspensionReasons"), "label")).containsExactly("욕설·비방", "반품 남용", "부정 주문 의심",
                "결제 도용 의심", "신고 다수 접수", "기타(직접 입력)");
        assertThat(texts(body.path("suspensionReasons"), "detailRequired"))
                .containsExactly("false", "false", "false", "false", "false", "true");
        assertThat(texts(body.path("releaseReasons"), "code")).containsExactly("RESOLVED", "MISTAKE", "TERM_ENDED");
        assertThat(texts(body.path("releaseReasons"), "label")).containsExactly("소명 확인 완료", "오처리 정정", "제재 기간 종료");
        assertThat(texts(body.path("releaseReasons"), "detailRequired")).containsOnly("false");
        for (String list : List.of("suspensionReasons", "releaseReasons")) {
            body.path(list).forEach(r -> assertThat(r.path("holdsPayout").isNull()).as(list).isTrue());
        }

        // 목록의 해제 사유는 해제 API 가 실제로 받는 값이다
        suspend(mia, body.path("suspensionReasons").get(0).path("code").asText(), null).andExpect(status().isOk());
        release(mia, body.path("releaseReasons").get(2).path("code").asText()).andExpect(status().isOk());
        assertThat(sanctionRepository.findFirstByUserIdOrderBySuspendedAtDescIdDesc(mia.getId()).orElseThrow()
                .getReleaseReason().name()).isEqualTo("TERM_ENDED");
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asText()));
        return values;
    }

    // ------------------------------------------------------------------ 메모 · 처리 이력

    @Test
    @DisplayName("메모는 누적 · 300자 · 작성자 본인 24시간 내에만 고치고, 옛 단일 메모는 누구도 고치지 못한다")
    void memoRules() throws Exception {
        JsonNode memo = objectMapper.readTree(memo(post(ADMIN + "/" + mia.getId() + "/memos"), adminToken, "반품 사유 반복 — 기준 미달이라 넘어감")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("김운영"))
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString());
        long memoId = memo.get("memoId").asLong();

        memo(post(ADMIN + "/" + mia.getId() + "/memos"), adminToken, "가".repeat(301))
                .andExpect(status().isBadRequest());
        memo(patch(ADMIN + "/" + mia.getId() + "/memos/" + memoId), otherAdminToken, "남의 메모 수정")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_MEMO_NOT_AUTHOR"));
        memo(patch(ADMIN + "/" + mia.getId() + "/memos/" + memoId), adminToken, "오타 정정")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifiedAt").exists());

        UserAdminMemo old = memoRepository.save(UserAdminMemo.write(mia.getId(), admin.getId(), "김운영", "어제 메모",
                LocalDateTime.now().minusHours(25)));
        memo(patch(ADMIN + "/" + mia.getId() + "/memos/" + old.getId()), adminToken, "늦은 수정")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_MEMO_EDIT_WINDOW_CLOSED"));

        UserAdminMemo legacy = memoRepository.save(UserAdminMemo.write(mia.getId(), null, "이전 단일 메모(작성자 미기록)",
                "블랙리스트 의심 — 지속 모니터링", LocalDateTime.now()));
        memo(patch(ADMIN + "/" + mia.getId() + "/memos/" + legacy.getId()), adminToken, "이관 메모 수정")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_MEMO_NOT_AUTHOR"));

        mockMvc.perform(get(ADMIN + "/" + mia.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.memos.total").value(3))
                .andExpect(jsonPath("$.memos.content[0].memoId").value(legacy.getId()))
                .andExpect(jsonPath("$.memos.content[0].editable").value(false));

        mockMvc.perform(delete(ADMIN + "/" + mia.getId() + "/memos/" + memoId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ADMIN + "/" + mia.getId() + "/memos").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @DisplayName("처리 이력은 정지 · 소명 접수 · 해제 · 메모 · 가입을 한 목록 최신순으로 합치고, 정지 · 해제를 두 줄로 세지 않는다")
    void historiesMergeStoredAndDerivedEvents() throws Exception {
        suspend(mia, "ABUSIVE_LANGUAGE", null).andExpect(status().isOk());
        appeal(miaToken, "{\"type\":\"ACCOUNT\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true}")
                .andExpect(status().isCreated());
        release(mia, "RESOLVED").andExpect(status().isOk());
        memo(post(ADMIN + "/" + mia.getId() + "/memos"), adminToken, "소명 확인 후 해제").andExpect(status().isOk());

        mockMvc.perform(get(ADMIN + "/" + mia.getId() + "/histories").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.content[0].eventType").value("MEMO_CREATED"))
                .andExpect(jsonPath("$.content[1].eventType").value("SUSPENSION_RELEASED"))
                .andExpect(jsonPath("$.content[2].eventType").value("APPEAL_RECEIVED"))
                .andExpect(jsonPath("$.content[2].refType").value("INQUIRY"))
                .andExpect(jsonPath("$.content[3].eventType").value("SUSPENDED"))
                .andExpect(jsonPath("$.content[4].eventType").value("SIGNED_UP"));
    }

    // ------------------------------------------------------------------ 헬퍼

    private ResultActions suspend(Users target, String reason, String detail) throws Exception {
        String body = detail == null
                ? "{\"reason\":\"" + reason + "\"}"
                : "{\"reason\":\"" + reason + "\",\"reasonDetail\":\"" + detail + "\"}";
        return mockMvc.perform(post(ADMIN + "/" + target.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, adminToken).content(body));
    }

    private ResultActions release(Users target, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return mockMvc.perform(post(ADMIN + "/" + target.getId() + "/release").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, adminToken).content(body));
    }

    private ResultActions withdraw(String token) throws Exception {
        return mockMvc.perform(delete("/v1/user/auth/withdraw").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token).content("{\"agreeConsent\":true}"));
    }

    private ResultActions appeal(String token, String body) throws Exception {
        return mockMvc.perform(post(INQUIRIES).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token).content(body));
    }

    private ResultActions memo(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token).content(toJson(Map.of("content", content))));
    }

    private Users createUser(String username, String nickname, RoleType roleType, UserStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.KAKAO, roleType, now, now);
        user.setStatus(status);
        return userRepository.save(user);
    }
}
