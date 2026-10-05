package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.thread.dto.AdminThreadDto.ResendNoticeResponse;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자동 카드 ① 서명 안내 재발송 요청(36 설계 5절 · §36-5 · 시안 A6-0 → A6).
 *
 * <pre>
 * [서명 안내 다시 받기] → 요청자의 운영팀 채널에 요청 카드 → 운영자가 모두싸인에서 재발송
 *   → 카드 안 [재발송 완료 알림 보내기] → 정해진 문구가 운영자 말풍선으로 · 카드가 「전송됨 · 시각 · 처리자」로 굳는다
 * </pre>
 *
 * <p>핵심 규칙 — ① 전송과 카드 상태 전환은 한 번에 일어나고 중복 전송이 없다 ② 계약 관리에 처리 기록을 남기지 않는다
 * (2026.09.28 확정) ③ 서명 단계를 벗어난 계약의 카드에는 버튼이 없다.
 */
@DisplayName("[통합] 어드민 소통 스레드 — 재발송 요청 카드")
class AdminThreadResendCardIntegrationTest extends AdminThreadTestSupport {

    // ------------------------------------------------------------------ 요청 → 카드 (A6-0)

    @Test
    @DisplayName("인플루언서의 요청은 인플루언서 운영팀 채널에만 카드로 등록된다 — 계약번호 · 공구명 · 요청자 · 요청 시각이 박힌다")
    void creatorRequestBecomesCardInCreatorChannel() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        requestResendByCreator(c).andExpect(status().isOk()).andExpect(jsonPath("$.alreadyRequested").value(false));

        ContractResendRequest request = latestResend(c);
        assertThat(request.getThreadId()).isEqualTo(creatorChannel.getId());
        assertThat(request.getCardMessageId()).isNotNull();
        assertThat(request.getNoticeMessageId()).isNull();

        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content", hasSize(2)))                 // 카드 + 가입 안내
                .andExpect(jsonPath("$.content[0].messageId").value(request.getCardMessageId()))
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].senderType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].operatorName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].content").value(RESEND_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_RESEND_REQUEST"))
                .andExpect(jsonPath("$.content[0].card.title").value(RESEND_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].card.tone").value("WARNING"))
                .andExpect(jsonPath("$.content[0].card.contractId").value(c.getId()))
                .andExpect(jsonPath("$.content[0].card.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.content[0].card.groupBuyTitle").value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.content[0].card.detail.requesterType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].card.detail.requesterName").value("뷰티_하윤"))
                .andExpect(jsonPath("$.content[0].card.detail.requestedAt").exists())
                .andExpect(jsonPath("$.content[0].card.detail.reasonLabel").doesNotExist())
                .andExpect(jsonPath("$.content[0].card.action.type").value("RESEND_NOTICE"))
                .andExpect(jsonPath("$.content[0].card.action.state").value("PENDING"))
                .andExpect(jsonPath("$.content[0].card.action.canExecute").value(true))
                .andExpect(jsonPath("$.content[0].card.action.doneAt").value(nullValue()))
                .andExpect(jsonPath("$.content[0].card.action.doneByName").value(nullValue()));

        assertThat(cardsIn(brandChannel)).isEmpty();
    }

    @Test
    @DisplayName("브랜드의 요청은 브랜드 운영팀 채널에 붙는다")
    void sellerRequestBecomesCardInBrandChannel() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        requestResendBySeller(c).andExpect(status().isOk());

        assertThat(latestResend(c).getThreadId()).isEqualTo(brandChannel.getId());
        adminMessages(brandChannel)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].senderType").value("SELLER"))
                .andExpect(jsonPath("$.content[0].card.detail.requesterType").value("SELLER"))
                .andExpect(jsonPath("$.content[0].card.detail.requesterName").value("글로우랩"));
        assertThat(cardsIn(creatorChannel)).isEmpty();
    }

    @Test
    @DisplayName("요청 카드는 운영팀 배지만 올린다 — 요청자 자신의 배지는 그대로다. 목록 미리보기는 카드 제목이다")
    void cardRaisesOnlyTeamUnread() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        creatorSummary().andExpect(jsonPath("$.unreadCount").value(1));          // 가입 안내

        requestResendByCreator(c).andExpect(status().isOk());

        channels("INFLUENCER")
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value(RESEND_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(false))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));
        summaryOf(adminToken)
                .andExpect(jsonPath("$.influencer.unreadCount").value(1))
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(1))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(0));
        creatorSummary().andExpect(jsonPath("$.unreadCount").value(1));

        // 읽어도 미처리 카드 수는 그대로다 — 읽음과 처리는 다른 축이다.
        markRead(creatorChannel, adminToken).andExpect(status().isNoContent());
        summaryOf(adminToken)
                .andExpect(jsonPath("$.influencer.unreadCount").value(0))
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(1));
    }

    @Test
    @DisplayName("요청자 화면 — 카드는 내 말풍선이 아니고(mine=false) 처리자 · 버튼 가능 여부가 없다")
    void requesterSeesCardWithoutOperatorFields() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());

        String seen = body(creatorMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].senderType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].content").value(RESEND_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].card.tone").value("WARNING"))
                .andExpect(jsonPath("$.content[0].card.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.content[0].card.detail.requesterName").value("뷰티_하윤"))
                .andExpect(jsonPath("$.content[0].card.action.state").value("PENDING")));
        assertThat(seen).doesNotContain("canExecute", "doneByName", "noticeMessageId", "operatorName");
    }

    @Test
    @DisplayName("카드의 사실은 그때의 스냅샷이다 — 요청 뒤 브랜드명이 바뀌어도 카드는 요청 당시 이름이다")
    void cardFactsAreSnapshots() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendBySeller(c).andExpect(status().isOk());

        jdbc.update("UPDATE market SET market_name = ? WHERE market_id = ?", "글로우랩 리뉴얼", brand.marketId());

        adminMessages(brandChannel).andExpect(jsonPath("$.content[0].card.detail.requesterName").value("글로우랩"));
        channels("BRAND").andExpect(jsonPath("$.content[0].name").value("글로우랩 리뉴얼"));
    }

    // ------------------------------------------------------------------ 중복 억제 · 반복 요청

    @Test
    @DisplayName("중복 억제는 요청자별이다 — 같은 요청자는 기존 카드, 브랜드와 인플루언서가 각각 요청하면 각자 채널에 1장씩이다")
    void dedupIsPerRequester() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        requestResendBySeller(c).andExpect(jsonPath("$.alreadyRequested").value(false));
        requestResendBySeller(c).andExpect(jsonPath("$.alreadyRequested").value(true));
        requestResendByCreator(c).andExpect(jsonPath("$.alreadyRequested").value(false));
        requestResendByCreator(c).andExpect(jsonPath("$.alreadyRequested").value(true));

        assertThat(resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId())).hasSize(2);
        assertThat(cardsIn(brandChannel)).hasSize(1);
        assertThat(cardsIn(creatorChannel)).hasSize(1);
        summaryOf(adminToken).andExpect(jsonPath("$.brand.pendingCardCount").value(1))
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(1));
    }

    @Test
    @DisplayName("알림을 받은 뒤 다시 요청하면 새 카드가 생긴다 — 병합은 처리 전 요청끼리만이다")
    void requestAfterNoticeCreatesNewCard() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long firstCard = latestResend(c).getCardMessageId();
        resendNotice(creatorChannel, firstCard, adminToken).andExpect(status().isOk());

        requestResendByCreator(c).andExpect(jsonPath("$.alreadyRequested").value(false));

        Long secondCard = latestResend(c).getCardMessageId();
        assertThat(secondCard).isNotEqualTo(firstCard);
        assertThat(cardsIn(creatorChannel)).containsExactly(secondCard, firstCard);
        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].card.action.state").value("PENDING"))
                .andExpect(jsonPath("$.content[2].card.action.state").value("DONE"));
        summaryOf(adminToken).andExpect(jsonPath("$.influencer.pendingCardCount").value(1));
    }

    @Test
    @DisplayName("이미 서명한 쪽은 요청할 수 없고 카드도 생기지 않는다")
    void signedPartyCannotRequest() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.creatorSignedAt(now.minusHours(3)).asOf(now.minusHours(2)));

        requestResendByCreator(c).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_RESEND_NOT_ALLOWED"));

        assertThat(cardsIn(creatorChannel)).isEmpty();
        assertThat(resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId())).isEmpty();
    }

    @Test
    @DisplayName("운영팀 채널이 없는 회원이 요청하면 그 자리에서 채널을 만들고 카드를 붙인다 — 채널은 가입 안내로 열린다")
    void createsMissingChannelOnRequest() throws Exception {
        Creator minji = createCreator("민지의 쇼룸", "minji");
        Contract c = seed(ContractStatus.SIGNING, s -> s.counterparty(minji));

        requestResendByCreator(c, creatorTokenOf(minji)).andExpect(status().isOk());

        Long channelId = latestResend(c).getThreadId();
        assertThat(channelId).isNotNull().isNotEqualTo(creatorChannel.getId());
        channels("INFLUENCER", "keyword", "민지")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].threadId").value(channelId))
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value(RESEND_CARD_TITLE));
        adminMessages(channelId, adminToken)
                .andExpect(jsonPath("$.content[*].messageType").value(contains("SYSTEM", "TEXT")))
                .andExpect(jsonPath("$.content[1].content").value(org.hamcrest.Matchers.startsWith("안녕하세요, 민지의 쇼룸님.")));
    }

    // ------------------------------------------------------------------ 알림 전송 (A6)

    @Test
    @DisplayName("[재발송 완료 알림 보내기] — 정해진 문구가 운영자 말풍선으로 나가고 카드가 「전송됨 · 시각 · 처리자」로 굳는다")
    void noticeSendsFixedMessageAndFreezesCard() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        String sent = body(resendNotice(creatorChannel, cardId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyNotified").value(false))
                .andExpect(jsonPath("$.card.messageId").value(cardId))
                .andExpect(jsonPath("$.card.card.tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.card.card.action.state").value("DONE"))
                .andExpect(jsonPath("$.card.card.action.canExecute").value(false))
                .andExpect(jsonPath("$.card.card.action.doneByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.card.card.action.doneAt").exists())
                .andExpect(jsonPath("$.notice.messageType").value("TEXT"))
                .andExpect(jsonPath("$.notice.content").value(NOTICE))
                .andExpect(jsonPath("$.notice.senderType").value("ADMIN"))
                .andExpect(jsonPath("$.notice.autoNotice").value(true))
                .andExpect(jsonPath("$.notice.operatorName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.notice.mine").value(true)));
        long noticeId = readLong(sent, "$.notice.messageId");
        assertThat(readLong(sent, "$.card.card.action.noticeMessageId")).isEqualTo(noticeId);

        ContractResendRequest request = latestResend(c);
        assertThat(request.isHandled()).isTrue();
        assertThat(request.getHandledBy()).isEqualTo(admin.getId());
        assertThat(request.getNoticeMessageId()).isEqualTo(noticeId);
        Message notice = messages.findById(noticeId).orElseThrow();
        assertThat(notice.isAutoNotice()).isTrue();
        assertThat(notice.getSenderId()).isEqualTo(admin.getId());

        // 대화 흐름에서도 같은 모양이다 — 안내 말풍선이 카드 아래(최신)에 온다.
        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].messageId").value(noticeId))
                .andExpect(jsonPath("$.content[0].autoNotice").value(true))
                .andExpect(jsonPath("$.content[1].messageId").value(cardId))
                .andExpect(jsonPath("$.content[1].card.action.state").value("DONE"));
        channels("INFLUENCER").andExpect(jsonPath("$.content[0].lastMessagePreview").value(NOTICE))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true));
        summaryOf(adminToken).andExpect(jsonPath("$.influencer.pendingCardCount").value(0));
    }

    @Test
    @DisplayName("상대에게 안내는 일반 운영팀 메시지다 — 자동 안내 표시 · 처리자 없이 안 읽은 수만 오른다")
    void requesterSeesPlainOperatorMessage() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendBySeller(c).andExpect(status().isOk());
        sellerSummary().andExpect(jsonPath("$.unreadCount").value(0));

        resendNotice(brandChannel, latestResend(c).getCardMessageId(), adminToken).andExpect(status().isOk());

        sellerSummary().andExpect(jsonPath("$.unreadCount").value(1));
        String seen = body(sellerMessages(brandChannel)
                .andExpect(jsonPath("$.content[0].content").value(NOTICE))
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].messageType").value("TEXT"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[1].card.action.state").value("DONE"))
                .andExpect(jsonPath("$.content[1].card.action.doneAt").exists())
                .andExpect(jsonPath("$.content[1].card.tone").value("NEUTRAL")));
        assertThat(seen).doesNotContain(OPERATOR_NAME, "autoNotice", "operatorName", "doneByName");
    }

    @Test
    @DisplayName("계약 관리에 처리 기록을 남기지 않는다 — 계약 상태 · 이력 · 서명 기한이 그대로다(2026.09.28 확정)")
    void noticeLeavesContractUntouched() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Contract before = reload(c);
        int historyBefore = historyOf(c).size();

        resendNotice(creatorChannel, latestResend(c).getCardMessageId(), adminToken).andExpect(status().isOk());

        Contract after = reload(c);
        assertThat(after.getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getSignatureDeadlineAt()).isEqualTo(before.getSignatureDeadlineAt());
        assertThat(after.getSignatureRequestedAt()).isEqualTo(before.getSignatureRequestedAt());
        assertThat(historyOf(c)).hasSize(historyBefore)
                .noneMatch(h -> h.getEventType() == ContractEventType.RESEND_HANDLED);
    }

    @Test
    @DisplayName("계약 관리의 「재발송 요청」 큐는 카드 버튼으로 비워진다 — 큐 카운트가 곧 알림 전 카드 수다")
    void noticeClearsContractResendQueue() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        summary().andExpect(jsonPath("$.queues.RESEND").value(1));
        detail(c).andExpect(jsonPath("$.resend.pendingCount").value(1));

        resendNotice(creatorChannel, latestResend(c).getCardMessageId(), adminToken).andExpect(status().isOk());

        summary().andExpect(jsonPath("$.queues.RESEND").value(0));
        detail(c).andExpect(jsonPath("$.resend.pendingCount").value(0));
    }

    @Test
    @DisplayName("다시 눌러도 안내는 한 번만 나간다 — 뒤에 누른 운영자는 오류가 아니라 굳은 상태(처음 처리한 사람)를 받는다")
    void secondPressSendsNothing() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();
        long noticeId = readLong(body(resendNotice(creatorChannel, cardId, adminToken)), "$.notice.messageId");

        resendNotice(creatorChannel, cardId, otherAdminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyNotified").value(true))
                .andExpect(jsonPath("$.card.card.action.state").value("DONE"))
                .andExpect(jsonPath("$.card.card.action.doneByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.notice.messageId").value(noticeId));
        resendNotice(creatorChannel, cardId, adminToken).andExpect(jsonPath("$.alreadyNotified").value(true));

        assertThat(countMessagesWithContent(NOTICE)).isEqualTo(1);
        assertThat(latestResend(c).getHandledBy()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("두 운영자가 동시에 눌러도 안내는 한 건이다 — 요청 행 잠금으로 한 명만 보내고 다른 한 명은 굳은 상태를 받는다")
    void simultaneousPressesSendOnce() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        List<ResendNoticeResponse> results = java.util.Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (Long operatorId : List.of(admin.getId(), otherAdmin.getId())) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    results.add(threadCommands.sendResendNotice(creatorChannel.getId(), cardId, operatorId));
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> f : futures) f.get(15, TimeUnit.SECONDS);
        }

        assertThat(results).hasSize(2);
        assertThat(results).filteredOn(r -> !r.alreadyNotified()).hasSize(1);
        assertThat(results).filteredOn(ResendNoticeResponse::alreadyNotified).hasSize(1);
        assertThat(countMessagesWithContent(NOTICE)).isEqualTo(1);

        ContractResendRequest request = latestResend(c);
        String winnerName = request.getHandledBy().equals(admin.getId()) ? OPERATOR_NAME : OTHER_OPERATOR_NAME;
        assertThat(results).allSatisfy(r -> {
            assertThat(r.card().card().action().doneByName()).isEqualTo(winnerName);
            assertThat(r.notice().messageId()).isEqualTo(request.getNoticeMessageId());
        });
    }

    // ------------------------------------------------------------------ 닫힌 카드

    @Test
    @DisplayName("알림 전에 계약이 취소되면 카드는 닫히고(버튼 없음 · 중립 톤) 알림을 보낼 수 없다")
    void closesWhenCanceled() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null).andExpect(status().isOk());

        assertClosed(creatorChannel, cardId);
    }

    @Test
    @DisplayName("알림 전에 양측 서명이 끝나면(체결 처리 대기) 카드는 닫힌다")
    void closesWhenBothSigned() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        signatures(c, now.minusMinutes(30), now.minusMinutes(10), versionOf(c)).andExpect(status().isOk());

        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.CONCLUSION_PENDING);
        assertClosed(creatorChannel, cardId);
    }

    @Test
    @DisplayName("알림 전에 계약이 만료되면 카드는 닫힌다")
    void closesWhenExpired() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.deadlineAt(now.minusHours(1)));
        requestResendBySeller(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        expire(c, true).andExpect(status().isOk());

        assertClosed(brandChannel, cardId);
    }

    @Test
    @DisplayName("알림을 보낸 뒤 계약이 끝나도 카드는 「전송됨」으로 남고, 다시 누르면 오류 없이 굳은 상태를 받는다")
    void doneCardStaysDoneAfterContractEnds() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();
        resendNotice(creatorChannel, cardId, adminToken).andExpect(status().isOk());

        cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null).andExpect(status().isOk());

        adminMessages(creatorChannel).andExpect(jsonPath("$.content[1].card.action.state").value("DONE"));
        resendNotice(creatorChannel, cardId, otherAdminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyNotified").value(true));
    }

    @Test
    @DisplayName("탈퇴한 회원에게는 알림을 보낼 수 없다 — 카드는 PENDING으로 남는다")
    void withdrawnMemberCannotBeNotified() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();
        setUserStatus(creator, "WITHDRAWN");

        resendNotice(creatorChannel, cardId, adminToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));

        assertThat(latestResend(c).isHandled()).isFalse();
        assertThat(countMessagesWithContent(NOTICE)).isZero();
    }

    // ------------------------------------------------------------------ 카드 도입 전 방식과의 공존 (5-3)

    @Test
    @DisplayName("계약 관리의 처리 기록 API로 먼저 닫힌 요청은 카드도 「전송됨」이다 — 안내 말풍선은 없고 버튼을 눌러도 아무것도 나가지 않는다")
    void legacyHandleClosesCardWithoutNotice() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        handleResend(c).andExpect(status().isOk());

        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].card.action.state").value("DONE"))
                .andExpect(jsonPath("$.content[0].card.action.doneByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.content[0].card.action.noticeMessageId").value(nullValue()));
        resendNotice(creatorChannel, cardId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyNotified").value(true))
                .andExpect(jsonPath("$.notice").value(nullValue()));
        assertThat(countMessagesWithContent(NOTICE)).isZero();
    }

    // ------------------------------------------------------------------ 잘못된 대상 · 권한

    @Test
    @DisplayName("재발송 요청 카드가 아닌 메시지 · 다른 채널의 카드 · 없는 메시지에는 알림을 보낼 수 없다(404)")
    void noticeRequiresResendCardOfThatChannel() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();
        long bubbleId = readLong(body(adminSend(creatorChannel, adminToken, "확인 중입니다.")), "$.messageId");
        Contract canceled = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(2)));
        cancel(canceled, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.CREATOR,
                showroomz.domain.contract.type.ContractCancelRequestChannel.THREAD, now.minusHours(1)).andExpect(status().isOk());
        Long cancelCardId = cardsIn(creatorChannel).get(0);

        resendNotice(creatorChannel, bubbleId, adminToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_CARD_NOT_FOUND"));
        resendNotice(creatorChannel, cancelCardId, adminToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_CARD_NOT_FOUND"));
        resendNotice(brandChannel, cardId, adminToken).andExpect(status().isNotFound());
        resendNotice(creatorChannel, 999_999L, adminToken).andExpect(status().isNotFound());

        assertThat(latestResend(c).isHandled()).isFalse();
        assertThat(messages.findById(cancelCardId).map(Message::getMessageType)).contains(MessageType.SYSTEM);
    }

    @Test
    @DisplayName("쌍 스레드 경로로는 누를 수 없고(403) 운영자가 아니면 누를 수 없다(403)")
    void noticeAccess() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        requestResendByCreator(c).andExpect(status().isOk());
        Long cardId = latestResend(c).getCardMessageId();

        resendNotice(thread.getId(), cardId, adminToken).andExpect(status().isForbidden());
        resendNotice(creatorChannel, cardId, creatorToken).andExpect(status().isForbidden());
        resendNotice(creatorChannel, cardId, brandToken).andExpect(status().isForbidden());

        assertThat(latestResend(c).isHandled()).isFalse();
        assertThat(countMessagesWithContent(NOTICE)).isZero();
    }

    // ------------------------------------------------------------------ 확인

    private void assertClosed(MessageThread channel, Long cardId) throws Exception {
        adminMessages(channel)
                .andExpect(jsonPath("$.content[?(@.messageId == " + cardId + ")].card.action.state").value("CLOSED"))
                .andExpect(jsonPath("$.content[?(@.messageId == " + cardId + ")].card.action.canExecute").value(false))
                .andExpect(jsonPath("$.content[?(@.messageId == " + cardId + ")].card.tone").value("NEUTRAL"));
        resendNotice(channel, cardId, adminToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
        assertThat(countMessagesWithContent(NOTICE)).isZero();
        summaryOf(adminToken).andExpect(jsonPath("$.influencer.pendingCardCount").value(0))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(0));
    }
}
