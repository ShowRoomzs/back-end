package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCancelRequestChannel;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.type.MessageCardType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자동 카드 ② 계약 직권 취소 결과(36 설계 6절 · §36-6 · 시안 A5).
 *
 * <pre>
 * 당사자가 운영팀 채널에 대화로 요청 → 운영자가 사정 확인 → 계약 관리 C6 직권 취소 → 요청 채널에 결과 카드
 * </pre>
 *
 * <p>요청은 일반 대화다 — 카드가 붙는 것은 처리 결과뿐이다. 카드가 붙는 채널은 요청자에서 서버가 유도한다(운영자가 고르지 않는다).
 * 사유 메모는 당사자에게 그대로 공개된다(2026.10.05 확정). 상대방 채널에는 붙지 않는다(§36-9 A-5 미결).
 */
@DisplayName("[통합] 어드민 소통 스레드 — 직권 취소 결과 카드")
class AdminThreadCancelCardIntegrationTest extends AdminThreadTestSupport {

    private static final String MEMO = "인플루언서와 공구 기간을 다시 협의하기로 했습니다.";

    // ------------------------------------------------------------------ 카드가 붙는 곳

    @Test
    @DisplayName("브랜드가 스레드로 요청한 취소 — 브랜드 운영팀 채널에 결과 카드가 붙고 요청 채널이 계약에 남는다")
    void sellerThreadRequestPostsToBrandChannel() throws Exception {
        Contract c = signingContract();
        sellerSend(brandChannel, "여름 수분 세럼 계약 취소 요청드립니다.").andExpect(status().isCreated());

        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO, ContractActorType.SELLER).andExpect(status().isOk());

        assertThat(reload(c).getCancelRequestThreadId()).isEqualTo(brandChannel.getId());
        adminMessages(brandChannel)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].operatorName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].autoNotice").value(false))
                .andExpect(jsonPath("$.content[0].content").value(CANCEL_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_ADMIN_CANCELED"))
                .andExpect(jsonPath("$.content[0].card.title").value(CANCEL_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].card.tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.content[0].card.contractId").value(c.getId()))
                .andExpect(jsonPath("$.content[0].card.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.content[0].card.groupBuyTitle").value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.content[0].card.detail.reasonLabel").value("공구 일정 변경 — " + MEMO))
                .andExpect(jsonPath("$.content[0].card.detail.processedAt").exists())
                .andExpect(jsonPath("$.content[0].card.detail.processedByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.content[0].card.detail.notifiedBothParties").value(true))
                .andExpect(jsonPath("$.content[0].card.detail.requesterName").doesNotExist())
                .andExpect(jsonPath("$.content[0].card.action").value(nullValue()))
                .andExpect(jsonPath("$.content[1].senderType").value("SELLER"));

        // 상대방(인플루언서) 채널에는 붙지 않는다.
        assertThat(cardsIn(creatorChannel)).isEmpty();
    }

    @Test
    @DisplayName("인플루언서가 스레드로 요청한 취소 — 인플루언서 운영팀 채널에 붙고 브랜드 채널에는 없다")
    void creatorThreadRequestPostsToCreatorChannel() throws Exception {
        Contract c = signingContract();
        creatorSend(creatorChannel, "공구 일정이 안 맞아 취소 요청드립니다.").andExpect(status().isCreated());

        cancelBy(c, ContractCloseReasonCode.NEGOTIATION_STOPPED, null, ContractActorType.CREATOR).andExpect(status().isOk());

        assertThat(reload(c).getCancelRequestThreadId()).isEqualTo(creatorChannel.getId());
        assertThat(cardsIn(creatorChannel)).hasSize(1);
        assertThat(cardsIn(brandChannel)).isEmpty();
        adminMessages(creatorChannel).andExpect(jsonPath("$.content[0].card.detail.reasonLabel").value("상대와 협의 중단"));
    }

    @Test
    @DisplayName("체결 처리 대기(양측 서명 완료) 계약의 취소도 같은 카드가 붙는다")
    void conclusionPendingCancelPostsCard() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));

        cancelBy(c, ContractCloseReasonCode.CONDITION_REVIEW, null, ContractActorType.SELLER).andExpect(status().isOk());

        adminMessages(brandChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_ADMIN_CANCELED"))
                .andExpect(jsonPath("$.content[0].card.detail.reasonLabel").value("조건 재검토 필요"));
    }

    @Test
    @DisplayName("요청 경로가 스레드가 아니면(전화 · 이메일 · 기타) · 운영자 직권이면 카드가 없다 — 가리킬 요청 채널이 없다")
    void noCardWithoutThreadRequest() throws Exception {
        for (ContractCancelRequestChannel channel : new ContractCancelRequestChannel[]{
                ContractCancelRequestChannel.PHONE, ContractCancelRequestChannel.EMAIL, ContractCancelRequestChannel.ETC}) {
            Contract c = signingContract();
            cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER, channel,
                    now.minusHours(1)).andExpect(status().isOk());
            assertThat(reload(c).getCancelRequestThreadId()).as(channel.name()).isNull();
        }
        Contract byAdmin = signingContract();
        cancel(byAdmin, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null).andExpect(status().isOk());

        assertThat(reload(byAdmin).getCancelRequestThreadId()).isNull();
        assertThat(cardsIn(brandChannel)).isEmpty();
        assertThat(cardsIn(creatorChannel)).isEmpty();
    }

    @Test
    @DisplayName("운영팀 채널이 없는 요청자면 그 자리에서 채널을 만들고 카드를 붙인다")
    void createsMissingChannel() throws Exception {
        Creator minji = createCreator("민지의 쇼룸", "minji");
        Contract c = seed(ContractStatus.SIGNING, s -> s.counterparty(minji).createdAt(now.minusDays(2)));

        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.CREATOR).andExpect(status().isOk());

        Long channelId = reload(c).getCancelRequestThreadId();
        assertThat(channelId).isNotNull().isNotEqualTo(creatorChannel.getId());
        adminMessages(channelId, adminToken)
                .andExpect(jsonPath("$.content[*].messageType").value(contains("SYSTEM", "TEXT")))
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_ADMIN_CANCELED"));
    }

    // ------------------------------------------------------------------ 사유 문구

    @Test
    @DisplayName("사유는 「라벨 — 메모」다 — 기타는 메모가 필수이고, 메모가 없으면 라벨만 나간다")
    void reasonLabelCombinesLabelAndMemo() throws Exception {
        Contract etc = signingContract();
        cancelBy(etc, ContractCloseReasonCode.ETC, "  모델 일정 변경으로 촬영 불가  ", ContractActorType.SELLER).andExpect(status().isOk());
        Contract noMemo = signingContract();
        cancelBy(noMemo, ContractCloseReasonCode.OUT_OF_STOCK, "   ", ContractActorType.SELLER).andExpect(status().isOk());

        adminMessages(brandChannel)
                .andExpect(jsonPath("$.content[*].card.detail.reasonLabel")
                        .value(contains("상품 재고 부족", "기타 — 모델 일정 변경으로 촬영 불가")));
    }

    @Test
    @DisplayName("메모는 당사자에게 그대로 공개된다 — 처리 운영자 이름만 빠진다(2026.10.05 확정)")
    void memoIsDisclosedButOperatorIsNot() throws Exception {
        Contract c = signingContract();

        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO, ContractActorType.SELLER).andExpect(status().isOk());

        String seen = body(sellerMessages(brandChannel)
                .andExpect(jsonPath("$.content[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].content").value(CANCEL_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].card.tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.content[0].card.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.content[0].card.detail.reasonLabel").value("공구 일정 변경 — " + MEMO))
                .andExpect(jsonPath("$.content[0].card.detail.processedAt").exists())
                .andExpect(jsonPath("$.content[0].card.detail.notifiedBothParties").value(true))
                .andExpect(jsonPath("$.content[0].card.action").value(nullValue())));
        assertThat(seen).doesNotContain(OPERATOR_NAME, "processedByName", "operatorName", "operatorId");
    }

    @Test
    @DisplayName("카드는 처리 시점의 스냅샷이다 — 카드의 처리자 id는 저장되고, 운영자 이름은 조회할 때 붙는다")
    void cardStoresOperatorIdOnly() throws Exception {
        Contract c = signingContract();
        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO, ContractActorType.SELLER).andExpect(status().isOk());

        Message card = messages.findById(cardsIn(brandChannel).get(0)).orElseThrow();
        assertThat(card.getCardType()).isEqualTo(MessageCardType.CONTRACT_ADMIN_CANCELED);
        assertThat(card.getSenderId()).isEqualTo(admin.getId());
        assertThat(card.getRefId()).isEqualTo(c.getId());
        assertThat(card.getClientMessageId()).isEqualTo("contract-cancel-" + c.getId());
        assertThat(card.getCardPayload()).contains("\"operatorId\":" + admin.getId()).doesNotContain(OPERATOR_NAME);

        // 이름은 현재 값으로 붙는다.
        jdbc.update("UPDATE seller SET name = ? WHERE seller_id = ?", "김운영(퇴사)", admin.getId());
        adminMessages(brandChannel).andExpect(jsonPath("$.content[0].card.detail.processedByName").value("김운영(퇴사)"));
    }

    // ------------------------------------------------------------------ 배지 · 목록

    @Test
    @DisplayName("결과 카드는 요청자의 배지를 올리고 운영팀 배지는 올리지 않는다 — 목록 미리보기는 카드 제목 · 운영팀 발신이다")
    void resultCardRaisesMemberUnreadOnly() throws Exception {
        Contract c = signingContract();
        sellerSend(brandChannel, "계약 취소 요청드립니다.").andExpect(status().isCreated());
        markRead(brandChannel, adminToken).andExpect(status().isNoContent());

        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER).andExpect(status().isOk());

        sellerSummary().andExpect(jsonPath("$.unreadCount").value(1));
        summaryOf(adminToken).andExpect(jsonPath("$.brand.unreadCount").value(0))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(0));
        channels("BRAND").andExpect(jsonPath("$.content[0].lastMessagePreview").value(CANCEL_CARD_TITLE))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true));
    }

    // ------------------------------------------------------------------ 실패하면 카드도 없다

    @Test
    @DisplayName("취소가 거부되면 카드도 없다 — 체결된 계약 · 체크리스트 미확인 · 요청 시각 오류")
    void rejectedCancelLeavesNoCard() throws Exception {
        Contract concluded = seed(ContractStatus.CONCLUDED, s -> s.createdAt(now.minusDays(2)));
        cancelBy(concluded, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER)
                .andExpect(status().isConflict());

        Contract unchecked = signingContract();
        cancel(unchecked, false, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER,
                ContractCancelRequestChannel.THREAD, now.minusHours(1)).andExpect(status().isBadRequest());

        Contract future = signingContract();
        cancel(future, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER,
                ContractCancelRequestChannel.THREAD, now.plusHours(1)).andExpect(status().isBadRequest());

        Contract etcWithoutMemo = signingContract();
        cancelBy(etcWithoutMemo, ContractCloseReasonCode.ETC, null, ContractActorType.SELLER).andExpect(status().isBadRequest());

        assertThat(cardsIn(brandChannel)).isEmpty();
        assertThat(reload(unchecked).getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(reload(unchecked).getCancelRequestThreadId()).isNull();
    }

    @Test
    @DisplayName("이미 취소된 계약을 다시 취소할 수 없다 — 카드는 한 장이다")
    void cardIsPostedOnce() throws Exception {
        Contract c = signingContract();
        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER).andExpect(status().isOk());
        cancelBy(c, ContractCloseReasonCode.SCHEDULE_CHANGE, null, ContractActorType.SELLER).andExpect(status().isConflict());

        assertThat(cardsIn(brandChannel)).hasSize(1);
    }

    // ------------------------------------------------------------------ 적재 · 요청

    /** 요청 시각(1시간 전)이 계약 작성 이후가 되도록 작성 시각을 이틀 전으로 둔다. */
    private Contract signingContract() {
        return seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(2)));
    }

    private org.springframework.test.web.servlet.ResultActions cancelBy(Contract c, ContractCloseReasonCode reason, String memo,
                                                                        ContractActorType requester) throws Exception {
        LocalDateTime requestedAt = now.minusHours(1);
        return cancel(c, true, reason, memo, requester, ContractCancelRequestChannel.THREAD, requestedAt);
    }
}
