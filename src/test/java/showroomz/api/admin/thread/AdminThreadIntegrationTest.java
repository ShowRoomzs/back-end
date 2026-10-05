package showroomz.api.admin.thread;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.contract.AdminContractTestSupport;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCancelRequestChannel;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ui-admin-20a-channels(rev.1) — 어드민 소통 스레드 · 운영팀 1:1 채널(36 설계서).
 *
 * <p>브랜드 「글로우랩」 · 인플루언서 「뷰티_하윤」의 운영팀 채널을 깔고, 채널 대화 · 팀 공용 읽음 ·
 * 재발송 요청 카드(A6-0 → A6) · 직권 취소 결과 카드(A5)를 API 경계에서 확인한다.
 */
@DisplayName("[통합] 어드민 소통 스레드 — 운영팀 1:1 채널")
class AdminThreadIntegrationTest extends AdminContractTestSupport {

    private static final String NOTICE = "모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.";

    @Autowired private OperatorChannelService operatorChannels;
    @Autowired private MessageRepository messages;

    private MessageThread brandChannel;
    private MessageThread creatorChannel;
    private Seller otherAdmin;
    private String otherAdminToken;

    @BeforeEach
    void setUpChannels() {
        brandChannel = inTransaction(() -> operatorChannels.requireMarketChannel(brand.market()));
        creatorChannel = inTransaction(() -> operatorChannels.requireCreatorChannel(creator));
        otherAdmin = fixture.createAdmin("operator2@showroomz.test", "박운영");
        otherAdminToken = adminToken(otherAdmin);
    }

    // ------------------------------------------------------------------ 목록 · 접근

    @Test
    @DisplayName("탭별로 운영팀 채널만 나오고 회원번호 · 보조 줄 재료 · 운영팀 접두 판정값이 내려온다")
    void listsOperatorChannelsByTab() throws Exception {
        adminSend(brandChannel, adminToken, "9월 정산 일정 안내드립니다.").andExpect(status().isCreated());

        channels("BRAND")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].threadId").value(brandChannel.getId()))
                .andExpect(jsonPath("$.content[0].tab").value("BRAND"))
                .andExpect(jsonPath("$.content[0].name").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].memberNo").value("BRD-" + brand.marketId()))
                .andExpect(jsonPath("$.content[0].managerName").value("김담당"))
                .andExpect(jsonPath("$.content[0].memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("9월 정산 일정 안내드립니다."))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0))
                .andExpect(jsonPath("$.content[0].writable").value(true));

        // 인플루언서 채널은 가입 안내(시스템 발신)로 열린다 — 쌍 스레드는 어느 탭에도 없다.
        channels("INFLUENCER")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].threadId").value(creatorChannel.getId()))
                .andExpect(jsonPath("$.content[0].name").value("뷰티_하윤"))
                .andExpect(jsonPath("$.content[0].memberNo").value("INF-" + creator.getId()))
                .andExpect(jsonPath("$.content[0].businessType").value("INDIVIDUAL"));
    }

    @Test
    @DisplayName("검색은 이름 부분 일치와 회원번호 두 축이다 — 숫자가 아닌 회원번호는 전체가 아니라 0건이다")
    void searchesByNameOrMemberNumber() throws Exception {
        fixture.createBrand("moodcos@showroomz.test", "무드코스메틱");
        inTransaction(() -> operatorChannels.requireMarketChannel(
                marketRepository.findAll().stream().filter(m -> m.getMarketName().equals("무드코스메틱")).findFirst().orElseThrow()));

        channels("BRAND").andExpect(jsonPath("$.content", hasSize(2)));
        channels("BRAND", "keyword", "글로우").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("글로우랩"));
        channels("BRAND", "keyword", "brd-" + brand.marketId()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("글로우랩"));
        channels("BRAND", "keyword", "BRD-abc").andExpect(jsonPath("$.content", hasSize(0)));
        channels("INFLUENCER", "keyword", "INF-" + creator.getId()).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("운영팀 채널만 열린다 — 브랜드↔인플루언서 쌍 스레드는 403이고 운영자가 아니면 들어올 수 없다")
    void opensOnlyOperatorChannels() throws Exception {
        mockMvc.perform(get("/v1/admin/threads/" + thread.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        adminSend(thread, adminToken, "쌍 스레드에는 쓸 수 없다").andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/threads/999999/info").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/admin/connections/threads").param("tab", "BRAND").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 대화 · 읽음

    @Test
    @DisplayName("운영자 이름은 어드민에만 보인다 — 상대 응답에는 운영자 이름 · id · 자동 안내 표시가 키째로 없다")
    void operatorIdentityStaysInAdmin() throws Exception {
        String sent = body(adminSend(brandChannel, adminToken, "확인했습니다.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.operatorName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.autoNotice").value(false)));
        assertThat(messages.findById(readLong(sent, "$.messageId")).orElseThrow().getSenderId()).isEqualTo(admin.getId());

        // 다른 운영자가 봐도 운영팀의 말풍선이다 — 이름은 쓴 사람의 것이다.
        mockMvc.perform(get("/v1/admin/threads/" + brandChannel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, otherAdminToken))
                .andExpect(jsonPath("$.content[0].mine").value(true))
                .andExpect(jsonPath("$.content[0].operatorName").value(OPERATOR_NAME));

        String seen = body(sellerMessages(brandChannel).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].messageType").value("TEXT")));
        assertThat(seen).doesNotContain(OPERATOR_NAME, "operatorName", "autoNotice", "senderId", "doneByName", "processedByName");
    }

    @Test
    @DisplayName("같은 멱등키로 다시 보내면 새로 저장하지 않고, 본문과 첨부가 모두 비면 400이다")
    void sendIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = body(adminSend(brandChannel, adminToken, key, "안내드립니다.").andExpect(status().isCreated()));
        adminSend(brandChannel, adminToken, key, "안내드립니다.").andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(readLong(first, "$.messageId")));
        adminSend(brandChannel, adminToken, UUID.randomUUID().toString(), " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MESSAGE_EMPTY"));
    }

    @Test
    @DisplayName("읽음 위치는 운영팀 공용이다 — 다른 운영자의 말풍선은 안 읽은 수가 아니고, 한 명이 읽으면 모두에게 0이다")
    void readPositionIsSharedByTeam() throws Exception {
        sellerSend(brandChannel, "계약 취소 요청드립니다.").andExpect(status().isCreated());
        sellerSend(brandChannel, "브랜드 서명은 이미 했습니다.").andExpect(status().isCreated());

        channels("BRAND").andExpect(jsonPath("$.content[0].unreadCount").value(2))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(false));
        summaryOf(otherAdminToken).andExpect(jsonPath("$.brand.unreadCount").value(2))
                .andExpect(jsonPath("$.influencer.unreadCount").value(0))
                .andExpect(jsonPath("$.issue").value(nullValue()));

        // 운영자 A가 답해도 B의 안 읽은 수에 A의 말풍선이 잡히지 않는다.
        adminSend(brandChannel, adminToken, "확인 후 처리하겠습니다.").andExpect(status().isCreated());
        summaryOf(otherAdminToken).andExpect(jsonPath("$.brand.unreadCount").value(2));

        mockMvc.perform(post("/v1/admin/threads/" + brandChannel.getId() + "/read").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        summaryOf(otherAdminToken).andExpect(jsonPath("$.brand.unreadCount").value(0));
        mockMvc.perform(get("/v1/admin/connections/threads").param("tab", "BRAND").header(HttpHeaders.AUTHORIZATION, otherAdminToken))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));

        // 운영팀 메시지는 브랜드의 안 읽은 수다.
        mockMvc.perform(get("/v1/seller/connections/summary").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("탈퇴한 회원의 채널은 열람만 된다")
    void withdrawnMemberChannelIsReadOnly() throws Exception {
        sellerSend(brandChannel, "탈퇴 전 문의입니다.").andExpect(status().isCreated());
        jdbc.update("UPDATE market SET status = 'WITHDRAWN' WHERE market_id = ?", brand.marketId());

        channels("BRAND").andExpect(jsonPath("$.content[0].memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.content[0].writable").value(false));
        mockMvc.perform(get("/v1/admin/threads/" + brandChannel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        adminSend(brandChannel, adminToken, "답변드립니다.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));
    }

    // ------------------------------------------------------------------ 정보 바

    @Test
    @DisplayName("정보 바는 각 모듈의 집계를 읽고, 집계할 수 없는 값은 0이 아니라 null이다")
    void infoBarReadsAggregates() throws Exception {
        seed(ContractStatus.SIGNING);
        seed(ContractStatus.CONCLUSION_PENDING);
        seed(ContractStatus.CONCLUDED);
        seed(ContractStatus.CANCELED);

        info(brandChannel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tab").value("BRAND"))
                .andExpect(jsonPath("$.name").value("글로우랩"))
                .andExpect(jsonPath("$.memberNo").value("BRD-" + brand.marketId()))
                .andExpect(jsonPath("$.profile.managerName").value("김담당"))
                .andExpect(jsonPath("$.profile.managerContact").value("010-1111-2222"))
                .andExpect(jsonPath("$.profile.businessType").value(nullValue()))
                .andExpect(jsonPath("$.progress.contractSigning").value(2))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1))
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(0))
                .andExpect(jsonPath("$.progress.unsettledCount").value(nullValue()))
                .andExpect(jsonPath("$.progress.connectedBrandCount").value(nullValue()))
                .andExpect(jsonPath("$.openIssueThreads", hasSize(0)));

        info(creatorChannel)
                .andExpect(jsonPath("$.tab").value("INFLUENCER"))
                .andExpect(jsonPath("$.name").value("뷰티_하윤"))
                .andExpect(jsonPath("$.profile.businessType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.profile.businessEmail").value("hayun-biz@showroomz.test"))
                .andExpect(jsonPath("$.profile.taxType").value(nullValue()))
                .andExpect(jsonPath("$.profile.managerName").value(nullValue()))
                .andExpect(jsonPath("$.progress.contractSigning").value(2))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1))
                .andExpect(jsonPath("$.progress.connectedBrandCount").value(1))
                .andExpect(jsonPath("$.progress.unsettledCount").value(nullValue()));
    }

    // ------------------------------------------------------------------ 재발송 요청 카드 (A6-0 → A6)

    @Test
    @DisplayName("[서명 안내 다시 받기]는 요청자의 운영팀 채널에만 카드로 등록된다 — 요청자 배지는 그대로, 운영팀 배지만 오른다")
    void resendRequestBecomesCardInRequesterChannel() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        requestResendByCreator(c).andExpect(status().isOk()).andExpect(jsonPath("$.alreadyRequested").value(false));

        ContractResendRequest request = resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId()).get(0);
        assertThat(request.getThreadId()).isEqualTo(creatorChannel.getId());
        assertThat(request.getCardMessageId()).isNotNull();

        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].messageId").value(request.getCardMessageId()))
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].senderType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].content").value("요청 · 서명 안내 다시 받기"))
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_RESEND_REQUEST"))
                .andExpect(jsonPath("$.content[0].card.tone").value("WARNING"))
                .andExpect(jsonPath("$.content[0].card.contractId").value(c.getId()))
                .andExpect(jsonPath("$.content[0].card.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.content[0].card.groupBuyTitle").value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.content[0].card.detail.requesterType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].card.detail.requesterName").value("뷰티_하윤"))
                .andExpect(jsonPath("$.content[0].card.action.state").value("PENDING"))
                .andExpect(jsonPath("$.content[0].card.action.canExecute").value(true));

        // 목록 미리보기는 카드 제목이고, 카드는 운영팀의 안 읽은 수다.
        channels("INFLUENCER").andExpect(jsonPath("$.content[0].lastMessagePreview").value("요청 · 서명 안내 다시 받기"))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(false))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));
        summaryOf(adminToken).andExpect(jsonPath("$.influencer.pendingCardCount").value(1))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(0));

        // 브랜드 채널에는 아무것도 붙지 않는다.
        adminMessages(brandChannel).andExpect(jsonPath("$.content", hasSize(0)));

        // 요청자 화면 — 내가 누른 요청이지만 내 말풍선이 아니고, 내 배지를 올리지도 않는다.
        creatorMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].card.action.state").value("PENDING"))
                .andExpect(jsonPath("$.content[0].card.action.canExecute").doesNotExist());
    }

    @Test
    @DisplayName("중복 억제는 요청자별이다 — 같은 요청자는 기존 카드, 브랜드와 인플루언서가 각각 요청하면 카드 2장이다")
    void resendDedupIsPerRequester() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        requestResendBySeller(c).andExpect(jsonPath("$.alreadyRequested").value(false));
        requestResendBySeller(c).andExpect(jsonPath("$.alreadyRequested").value(true));
        // 브랜드의 미처리 요청이 있어도 인플루언서의 요청은 인플루언서 채널에 따로 등록된다.
        requestResendByCreator(c).andExpect(jsonPath("$.alreadyRequested").value(false));

        assertThat(resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId())).hasSize(2);
        adminMessages(brandChannel).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].card.detail.requesterName").value("글로우랩"));
        summaryOf(adminToken).andExpect(jsonPath("$.brand.pendingCardCount").value(1))
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(1));
    }

    @Test
    @DisplayName("[재발송 완료 알림 보내기] — 정해진 문구가 자동 전송되고 카드가 굳는다. 다시 눌러도 안내는 한 번만 나간다")
    void resendNoticeSendsOnceAndFreezesCard() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId()).get(0).getCardMessageId();
        int historyBefore = historyOf(c).size();

        String sent = body(resendNotice(creatorChannel, cardId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyNotified").value(false))
                .andExpect(jsonPath("$.card.card.tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.card.card.action.state").value("DONE"))
                .andExpect(jsonPath("$.card.card.action.canExecute").value(false))
                .andExpect(jsonPath("$.card.card.action.doneByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.card.card.action.doneAt").exists())
                .andExpect(jsonPath("$.notice.content").value(NOTICE))
                .andExpect(jsonPath("$.notice.autoNotice").value(true))
                .andExpect(jsonPath("$.notice.operatorName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.notice.mine").value(true)));
        long noticeId = readLong(sent, "$.notice.messageId");
        assertThat(readLong(sent, "$.card.card.action.noticeMessageId")).isEqualTo(noticeId);

        // 다른 운영자가 뒤늦게 눌러도 아무것도 나가지 않고 굳은 상태(처음 처리한 사람)를 받는다.
        resendNotice(creatorChannel, cardId, otherAdminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyNotified").value(true))
                .andExpect(jsonPath("$.card.card.action.doneByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.notice.messageId").value(noticeId));
        assertThat(messages.findAll().stream().filter(m -> NOTICE.equals(m.getContent()))).hasSize(1);

        // 계약 관리에 처리 기록을 남기지 않는다(2026.09.28) — 계약 상태도 이력도 그대로다.
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(historyOf(c)).hasSize(historyBefore);
        assertThat(historyOf(c)).noneMatch(h -> h.getEventType() == ContractEventType.RESEND_HANDLED);
        summaryOf(adminToken).andExpect(jsonPath("$.influencer.pendingCardCount").value(0));
        channels("INFLUENCER").andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true));

        // 상대에게는 일반 운영팀 메시지다 — 자동 안내 표시도, 처리자도 없다.
        String seen = body(creatorMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].content").value(NOTICE))
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[1].card.action.state").value("DONE"))
                .andExpect(jsonPath("$.content[1].card.action.doneAt").exists()));
        assertThat(seen).doesNotContain(OPERATOR_NAME, "autoNotice", "operatorName", "doneByName");
    }

    @Test
    @DisplayName("알림 전에 계약이 서명 단계를 벗어나면 카드는 닫히고 알림을 보낼 수 없다")
    void resendCardClosesWhenContractLeavesSigning() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId()).get(0).getCardMessageId();

        cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null).andExpect(status().isOk());

        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].card.tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.content[0].card.action.state").value("CLOSED"))
                .andExpect(jsonPath("$.content[0].card.action.canExecute").value(false));
        resendNotice(creatorChannel, cardId, adminToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
        assertThat(messages.findAll().stream().filter(m -> NOTICE.equals(m.getContent()))).isEmpty();
        summaryOf(adminToken).andExpect(jsonPath("$.influencer.pendingCardCount").value(0));
    }

    @Test
    @DisplayName("재발송 요청 카드가 아닌 메시지 · 다른 채널의 카드에는 알림을 보낼 수 없다")
    void resendNoticeRequiresCardOfThatChannel() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId()).get(0).getCardMessageId();
        long bubbleId = readLong(body(adminSend(creatorChannel, adminToken, "확인 중입니다.")), "$.messageId");

        resendNotice(creatorChannel, bubbleId, adminToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_CARD_NOT_FOUND"));
        resendNotice(brandChannel, cardId, adminToken).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 직권 취소 결과 카드 (A5)

    @Test
    @DisplayName("스레드로 들어온 취소 요청을 처리하면 요청자의 운영팀 채널에 결과 카드가 붙는다 — 메모는 당사자에게 공개된다")
    void adminCancelPostsResultCardToRequesterChannel() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(2)));
        sellerSend(brandChannel, "여름 수분 세럼 계약 취소 요청드립니다.").andExpect(status().isCreated());

        cancel(c, true, ContractCloseReasonCode.CONDITION_REVIEW, "당사자 요청 — 조건 재협의",
                ContractActorType.SELLER, ContractCancelRequestChannel.THREAD, now.minusMinutes(30))
                .andExpect(status().isOk());

        assertThat(reload(c).getCancelRequestThreadId()).isEqualTo(brandChannel.getId());
        adminMessages(brandChannel)
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].content").value("계약 직권 취소 처리됨"))
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_ADMIN_CANCELED"))
                .andExpect(jsonPath("$.content[0].card.tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.content[0].card.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.content[0].card.detail.reasonLabel").value("조건 재검토 필요 — 당사자 요청 — 조건 재협의"))
                .andExpect(jsonPath("$.content[0].card.detail.processedByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.content[0].card.detail.processedAt").exists())
                .andExpect(jsonPath("$.content[0].card.detail.notifiedBothParties").value(true))
                .andExpect(jsonPath("$.content[0].card.action").value(nullValue()));

        // 상대방(인플루언서) 채널에는 붙이지 않는다(§36-9 A-5 미결).
        assertThat(cardsIn(creatorChannel)).isEmpty();

        // 브랜드 화면 — 사유와 메모는 보이고 처리자는 없다. 결과 카드는 브랜드의 안 읽은 수다.
        String seen = body(sellerMessages(brandChannel)
                .andExpect(jsonPath("$.content[0].card.detail.reasonLabel").value("조건 재검토 필요 — 당사자 요청 — 조건 재협의"))
                .andExpect(jsonPath("$.content[0].card.detail.processedAt").exists()));
        assertThat(seen).doesNotContain(OPERATOR_NAME, "processedByName");
        mockMvc.perform(get("/v1/seller/connections/summary").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("전화로 들어온 요청 · 운영자 직권 취소에는 결과 카드가 없다 — 가리킬 요청 채널이 없다")
    void noResultCardWithoutRequestThread() throws Exception {
        Contract byPhone = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(2)));
        cancel(byPhone, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null,
                ContractActorType.CREATOR, ContractCancelRequestChannel.PHONE, now.minusMinutes(30)).andExpect(status().isOk());
        Contract byAdmin = seed(ContractStatus.SIGNING);
        cancel(byAdmin, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null).andExpect(status().isOk());

        assertThat(cardsIn(brandChannel)).isEmpty();
        assertThat(cardsIn(creatorChannel)).isEmpty();
        assertThat(reload(byPhone).getCancelRequestThreadId()).isNull();
        assertThat(reload(byAdmin).getCancelRequestThreadId()).isNull();
    }

    @Test
    @DisplayName("취소가 거부되면 결과 카드도 생기지 않는다")
    void rejectedCancelLeavesNoCard() throws Exception {
        Contract concluded = seed(ContractStatus.CONCLUDED);
        cancel(concluded, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null,
                ContractActorType.SELLER, ContractCancelRequestChannel.THREAD, now.minusMinutes(30)).andExpect(status().isConflict());

        assertThat(cardsIn(brandChannel)).isEmpty();
    }

    // ------------------------------------------------------------------ 요청 헬퍼

    private ResultActions channels(String tab, String... params) throws Exception {
        var request = get("/v1/admin/connections/threads").param("tab", tab).header(HttpHeaders.AUTHORIZATION, adminToken);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mockMvc.perform(request);
    }

    private ResultActions summaryOf(String token) throws Exception {
        return mockMvc.perform(get("/v1/admin/connections/summary").header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions info(MessageThread channel) throws Exception {
        return mockMvc.perform(get("/v1/admin/threads/" + channel.getId() + "/info").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private ResultActions adminMessages(MessageThread channel) throws Exception {
        return mockMvc.perform(get("/v1/admin/threads/" + channel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private ResultActions adminSend(MessageThread channel, String token, String content) throws Exception {
        return adminSend(channel, token, UUID.randomUUID().toString(), content);
    }

    private ResultActions adminSend(MessageThread channel, String token, String key, String content) throws Exception {
        return mockMvc.perform(post("/v1/admin/threads/" + channel.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(java.util.Map.of("clientMessageId", key, "content", content))));
    }

    private ResultActions resendNotice(MessageThread channel, Long cardMessageId, String token) throws Exception {
        return mockMvc.perform(post("/v1/admin/threads/" + channel.getId() + "/cards/" + cardMessageId + "/resend-notice")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions sellerSend(MessageThread channel, String content) throws Exception {
        return mockMvc.perform(post("/v1/seller/threads/" + channel.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, brandToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(java.util.Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content))));
    }

    private ResultActions sellerMessages(MessageThread channel) throws Exception {
        return mockMvc.perform(get("/v1/seller/threads/" + channel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private ResultActions creatorMessages(MessageThread channel) throws Exception {
        return mockMvc.perform(get("/v1/creator/threads/" + channel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private ResultActions requestResendBySeller(Contract c) throws Exception {
        return mockMvc.perform(post(SELLER_CONTRACTS + "/" + c.getId() + "/resend-request").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private ResultActions requestResendByCreator(Contract c) throws Exception {
        return mockMvc.perform(post(CREATOR_CONTRACTS + "/" + c.getId() + "/resend-request").header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private List<Long> cardsIn(MessageThread channel) throws Exception {
        return JsonPath.parse(body(adminMessages(channel))).read("$.content[?(@.messageType == 'SYSTEM')].messageId");
    }
}
