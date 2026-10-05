package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대화 · 읽음(36 설계 0-4 · 0-5 · 3-5 ~ 3-7 · §36-4).
 *
 * <p>두 가지를 집중해서 본다. ① 운영자 이름은 어드민에만 있고 상대 응답에는 <b>키째로</b> 없다 — 화면에서 가리는 것으로는
 * 부족하다(§36-4). ② 읽음 위치는 운영팀 공용이다 — 운영자 A가 답한 채널이 B에게 「안 읽음」으로 남지 않는다.
 */
@DisplayName("[통합] 어드민 소통 스레드 — 대화 · 읽음")
class AdminThreadMessageIntegrationTest extends AdminThreadTestSupport {

    @Autowired private MessageThreadService messageThreadService;

    // ------------------------------------------------------------------ 운영자 표기

    @Test
    @DisplayName("운영자 말풍선은 어드민에 「운영팀 · 이름」으로 보이고 작성 운영자 id가 저장된다 — 다른 운영자가 봐도 운영팀의 말풍선이다")
    void operatorBubbleCarriesNameInAdmin() throws Exception {
        String sent = body(adminSend(brandChannel, adminToken, "확인했습니다.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageType").value("TEXT"))
                .andExpect(jsonPath("$.senderType").value("ADMIN"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.senderName").value(nullValue()))
                .andExpect(jsonPath("$.operatorName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.autoNotice").value(false))
                .andExpect(jsonPath("$.card").value(nullValue()))
                .andExpect(jsonPath("$.content").value("확인했습니다.")));
        assertThat(messages.findById(readLong(sent, "$.messageId")).orElseThrow().getSenderId()).isEqualTo(admin.getId());

        adminSend(brandChannel, otherAdminToken, "추가로 안내드립니다.").andExpect(status().isCreated());

        adminMessages(brandChannel.getId(), otherAdminToken)
                .andExpect(jsonPath("$.content[*].operatorName").value(contains(OTHER_OPERATOR_NAME, OPERATOR_NAME)))
                .andExpect(jsonPath("$.content[*].mine").value(contains(true, true)));
    }

    @Test
    @DisplayName("상대 응답에는 운영자 이름 · id · 자동 안내 표시가 키째로 없다 — 상대에게 운영팀은 SHOWROOMZ 운영팀뿐이다")
    void memberResponsesNeverCarryOperatorIdentity() throws Exception {
        adminSend(brandChannel, adminToken, "확인했습니다.").andExpect(status().isCreated());
        adminSend(creatorChannel, otherAdminToken, "안내드립니다.").andExpect(status().isCreated());

        String seller = body(sellerMessages(brandChannel).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].messageType").value("TEXT"))
                .andExpect(jsonPath("$.content[0].card").value(nullValue())));
        String creatorView = body(creatorMessages(creatorChannel).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN")));

        for (String json : List.of(seller, creatorView)) {
            assertThat(json).doesNotContain(OPERATOR_NAME, OTHER_OPERATOR_NAME, "operatorName", "autoNotice",
                    "senderId", "senderName", "doneByName", "processedByName", "canExecute");
        }
    }

    @Test
    @DisplayName("가입 안내처럼 시스템이 운영팀 이름으로 보낸 메시지는 운영자 이름이 없고 자동 안내로 표시된다")
    void systemWelcomeHasNoOperatorName() throws Exception {
        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value(CREATOR_WELCOME))
                .andExpect(jsonPath("$.content[0].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].mine").value(true))
                .andExpect(jsonPath("$.content[0].operatorName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].autoNotice").value(true));
    }

    @Test
    @DisplayName("상대 말풍선은 상대 이름 · 역할로 내려오고 운영자 필드는 비어 있다")
    void memberBubbleCarriesMemberName() throws Exception {
        sellerSend(brandChannel, "여름 수분 세럼 계약 취소 요청드립니다.").andExpect(status().isCreated());
        creatorSend(creatorChannel, "메일 받았어요!").andExpect(status().isCreated());

        adminMessages(brandChannel)
                .andExpect(jsonPath("$.content[0].senderType").value("SELLER"))
                .andExpect(jsonPath("$.content[0].senderName").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].operatorName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].autoNotice").value(false))
                .andExpect(jsonPath("$.content[0].messageType").value("TEXT"));
        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].senderType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].senderName").value("뷰티_하윤"));
    }

    // ------------------------------------------------------------------ 전송

    @Test
    @DisplayName("같은 멱등키로 다시 보내면 새로 저장하지 않고 기존 메시지를 200으로 돌려준다")
    void sendIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = body(adminSend(brandChannel, adminToken, key, "안내드립니다.").andExpect(status().isCreated()));

        adminSend(brandChannel, adminToken, key, "안내드립니다.").andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(readLong(first, "$.messageId")));

        assertThat(countMessagesWithContent("안내드립니다.")).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 형식 — 멱등키는 필수 · 64자 이하이고, 본문과 첨부가 모두 비면 400이다")
    void validatesSendRequest() throws Exception {
        adminSend(brandChannel.getId(), adminToken, null, "멱등키 없음", null).andExpect(status().isBadRequest());
        adminSend(brandChannel.getId(), adminToken, "", "멱등키 빈 값", null).andExpect(status().isBadRequest());
        adminSend(brandChannel.getId(), adminToken, "k".repeat(65), "멱등키 65자", null).andExpect(status().isBadRequest());
        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), "  ", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MESSAGE_EMPTY"));
        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null, List.of())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MESSAGE_EMPTY"));

        adminSend(brandChannel.getId(), adminToken, "k".repeat(64), "64자 멱등키", null).andExpect(status().isCreated());
        assertThat(messages.findAll()).filteredOn(m -> m.getThread().getId().equals(brandChannel.getId())).hasSize(1);
    }

    @Test
    @DisplayName("운영팀 메시지는 상대의 안 읽은 수다 — 상대가 읽으면 줄어든다")
    void operatorMessageRaisesMemberUnread() throws Exception {
        // 가입 안내 1건이 이미 인플루언서의 안 읽은 메시지다.
        creatorSummary().andExpect(jsonPath("$.unreadCount").value(1));
        sellerSummary().andExpect(jsonPath("$.unreadCount").value(0));

        adminSend(creatorChannel, adminToken, "원천징수영수증 발급 안내드립니다.").andExpect(status().isCreated());
        adminSend(brandChannel, otherAdminToken, "9월 정산 일정 안내드립니다.").andExpect(status().isCreated());

        creatorSummary().andExpect(jsonPath("$.unreadCount").value(2));
        sellerSummary().andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post("/v1/creator/threads/" + creatorChannel.getId() + "/read").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNoContent());
        creatorSummary().andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ------------------------------------------------------------------ 첨부 전송 (S3 없이 업로드 완료 행으로)

    @Test
    @DisplayName("첨부만 보내도 된다 — 배열 순서가 표시 순서이고 미리보기는 첨부 종류 문구다")
    void sendsAttachmentsOnly() throws Exception {
        MessageAttachment first = uploadedAttachment(brandChannel, ParticipantType.ADMIN, admin.getId(),
                "정산내역.pdf", AttachmentType.DOCUMENT, 1_200_000L);
        MessageAttachment second = uploadedAttachment(brandChannel, ParticipantType.ADMIN, admin.getId(),
                "세금계산서.pdf", AttachmentType.DOCUMENT, 800_000L);

        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null,
                List.of(second.getId(), first.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(nullValue()))
                .andExpect(jsonPath("$.attachments", hasSize(2)))
                .andExpect(jsonPath("$.attachments[0].originalName").value("세금계산서.pdf"))
                .andExpect(jsonPath("$.attachments[0].sortOrder").value(0))
                .andExpect(jsonPath("$.attachments[1].originalName").value("정산내역.pdf"))
                .andExpect(jsonPath("$.attachments[1].sortOrder").value(1));

        // 첨부가 있는 메시지도 목록 미리보기 · 최근 시각을 갱신한다 — 첨부 연결이 영속성 컨텍스트를 비워도 잃지 않는다.
        channels("BRAND").andExpect(jsonPath("$.content[0].lastMessagePreview").value("파일 2개"))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true))
                .andExpect(jsonPath("$.content[0].lastMessageAt").exists());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/seller/connections/threads")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.content[0].operatorChannel").value(true))
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("파일 2개"));
        // 상대도 같은 첨부를 같은 순서로 본다.
        sellerMessages(brandChannel).andExpect(jsonPath("$.content[0].attachments[*].originalName")
                .value(contains("세금계산서.pdf", "정산내역.pdf")));
    }

    @Test
    @DisplayName("첨부는 내가 · 이 채널에 올린 · 아직 안 붙은 것만 붙는다 — 다른 운영자 · 상대 · 다른 채널의 첨부는 403, 이미 보낸 첨부는 409")
    void attachmentOwnershipIsChecked() throws Exception {
        MessageAttachment byOtherAdmin = uploadedAttachment(brandChannel, ParticipantType.ADMIN, otherAdmin.getId(),
                "다른운영자.pdf", AttachmentType.DOCUMENT, 1_000L);
        MessageAttachment bySeller = uploadedAttachment(brandChannel, ParticipantType.SELLER, brand.marketId(),
                "브랜드.pdf", AttachmentType.DOCUMENT, 1_000L);
        MessageAttachment otherChannel = uploadedAttachment(creatorChannel, ParticipantType.ADMIN, admin.getId(),
                "다른채널.pdf", AttachmentType.DOCUMENT, 1_000L);
        MessageAttachment mine = uploadedAttachment(brandChannel, ParticipantType.ADMIN, admin.getId(),
                "내첨부.png", AttachmentType.IMAGE, 1_000L);

        for (MessageAttachment denied : List.of(byOtherAdmin, bySeller, otherChannel)) {
            adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), "첨부", List.of(denied.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ATTACHMENT_ACCESS_DENIED"));
        }
        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), "첨부", List.of(999_999L))
                .andExpect(status().isForbidden());

        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null, List.of(mine.getId()))
                .andExpect(status().isCreated());
        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null, List.of(mine.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_ALREADY_ATTACHED"));
        channels("BRAND").andExpect(jsonPath("$.content[0].lastMessagePreview").value("사진"));
    }

    @Test
    @DisplayName("상대가 보낸 첨부도 어드민 대화에 순서대로 실린다")
    void memberAttachmentsShowInAdmin() throws Exception {
        MessageAttachment photo = uploadedAttachment(creatorChannel, ParticipantType.CREATOR, creator.getId(),
                "촬영본.png", AttachmentType.IMAGE, 2_000L);
        transactionTemplate.executeWithoutResult(tx -> messageThreadService.sendMessage(
                threadRepository.findById(creatorChannel.getId()).orElseThrow(), ParticipantType.CREATOR,
                creator.getId(), UUID.randomUUID().toString(), "촬영본 보내드립니다", List.of(photo.getId())));

        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].senderType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].attachments", hasSize(1)))
                .andExpect(jsonPath("$.content[0].attachments[0].attachmentId").value(photo.getId()))
                .andExpect(jsonPath("$.content[0].attachments[0].attachmentType").value("IMAGE"))
                .andExpect(jsonPath("$.content[0].attachments[0].status").value("UPLOADED"));
    }

    // ------------------------------------------------------------------ 조회 페이지

    @Test
    @DisplayName("최신순 커서 페이징 — nextCursor를 넘기면 이어서 읽고, 마지막 페이지는 hasNext=false · nextCursor=null이다")
    void pagesMessagesByCursor() throws Exception {
        for (int i = 1; i <= 5; i++) {
            adminSend(brandChannel, adminToken, "메시지 " + i).andExpect(status().isCreated());
        }

        String first = body(adminMessages(brandChannel, "size", "2")
                .andExpect(jsonPath("$.content[*].content").value(contains("메시지 5", "메시지 4")))
                .andExpect(jsonPath("$.hasNext").value(true)));
        assertThat(readLong(first, "$.nextCursor")).isEqualTo(readLong(first, "$.content[1].messageId"));

        String second = body(adminMessages(brandChannel, "size", "2", "cursor", String.valueOf(readLong(first, "$.nextCursor")))
                .andExpect(jsonPath("$.content[*].content").value(contains("메시지 3", "메시지 2")))
                .andExpect(jsonPath("$.hasNext").value(true)));

        adminMessages(brandChannel, "size", "2", "cursor", String.valueOf(readLong(second, "$.nextCursor")))
                .andExpect(jsonPath("$.content[*].content").value(contains("메시지 1")))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("크기를 주지 않으면 30건씩이다 — 0 이하도 기본값으로 본다")
    void defaultPageSizeIsThirty() throws Exception {
        transactionTemplate.executeWithoutResult(tx -> {
            var channel = threadRepository.findById(brandChannel.getId()).orElseThrow();
            for (int i = 0; i < 31; i++) {
                messageThreadService.sendMessage(channel, ParticipantType.SELLER, brand.marketId(),
                        "seed-" + i, "문의 " + i, null);
            }
        });

        adminMessages(brandChannel).andExpect(jsonPath("$.content", hasSize(30))).andExpect(jsonPath("$.hasNext").value(true));
        adminMessages(brandChannel, "size", "0").andExpect(jsonPath("$.content", hasSize(30)));
        adminMessages(brandChannel, "size", "50").andExpect(jsonPath("$.content", hasSize(31)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ------------------------------------------------------------------ 읽음

    @Test
    @DisplayName("읽음 위치는 운영팀 공용이다 — 다른 운영자의 말풍선은 안 읽은 수가 아니고, 한 명이 읽으면 모두에게 0이다")
    void readPositionIsSharedByTeam() throws Exception {
        sellerSend(brandChannel, "계약 취소 요청드립니다.").andExpect(status().isCreated());
        sellerSend(brandChannel, "브랜드 서명은 이미 했습니다.").andExpect(status().isCreated());

        channels("BRAND").andExpect(jsonPath("$.content[0].unreadCount").value(2));
        summaryOf(otherAdminToken).andExpect(jsonPath("$.brand.unreadCount").value(2));

        // 운영자 A가 답해도 B의 안 읽은 수에 A의 말풍선이 잡히지 않는다 — 답하는 것과 읽는 것은 별개다.
        adminSend(brandChannel, adminToken, "확인 후 처리하겠습니다.").andExpect(status().isCreated());
        summaryOf(otherAdminToken).andExpect(jsonPath("$.brand.unreadCount").value(2));

        markRead(brandChannel, adminToken).andExpect(status().isNoContent());
        summaryOf(otherAdminToken).andExpect(jsonPath("$.brand.unreadCount").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ADMIN_CHANNELS)
                        .param("tab", "BRAND").header(HttpHeaders.AUTHORIZATION, otherAdminToken))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));

        // 읽은 뒤 새로 온 것만 다시 센다.
        sellerSend(brandChannel, "추가 문의드립니다.").andExpect(status().isCreated());
        summaryOf(adminToken).andExpect(jsonPath("$.brand.unreadCount").value(1));
    }

    @Test
    @DisplayName("운영자 읽음은 상대의 읽음과 별개다 — 운영팀이 읽어도 상대의 안 읽은 수는 그대로다")
    void operatorReadDoesNotTouchMemberRead() throws Exception {
        adminSend(brandChannel, adminToken, "안내드립니다.").andExpect(status().isCreated());
        sellerSend(brandChannel, "확인했습니다.").andExpect(status().isCreated());

        markRead(brandChannel, adminToken).andExpect(status().isNoContent());

        sellerSummary().andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("메시지가 없는 채널도 읽음 처리는 204다")
    void readOnEmptyChannel() throws Exception {
        markRead(brandChannel, adminToken).andExpect(status().isNoContent());
        summaryOf(adminToken).andExpect(jsonPath("$.brand.unreadCount").value(0));
    }

    // ------------------------------------------------------------------ 회원 상태

    @Test
    @DisplayName("탈퇴한 브랜드의 채널은 열람 · 읽음만 된다 — 전송은 409")
    void withdrawnBrandChannelIsReadOnly() throws Exception {
        sellerSend(brandChannel, "탈퇴 전 문의입니다.").andExpect(status().isCreated());
        setMarketStatus(brand, "WITHDRAWN");

        adminMessages(brandChannel).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        markRead(brandChannel, adminToken).andExpect(status().isNoContent());
        adminSend(brandChannel, adminToken, "답변드립니다.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));
    }

    @Test
    @DisplayName("탈퇴한 인플루언서의 채널도 열람만 된다 — 계정(USERS) 상태로 판정한다")
    void withdrawnCreatorChannelIsReadOnly() throws Exception {
        setUserStatus(creator, "WITHDRAWN");

        adminMessages(creatorChannel).andExpect(status().isOk());
        adminSend(creatorChannel, adminToken, "답변드립니다.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));
    }

    @Test
    @DisplayName("정지 · 휴면 회원에게는 보낼 수 있다 — 정지 사유를 이 채널에서 설명할 수 있어야 한다")
    void suspendedAndDormantMembersCanReceive() throws Exception {
        setMarketStatus(brand, "SUSPENDED");
        adminSend(brandChannel, adminToken, "정지 사유 안내드립니다.").andExpect(status().isCreated());

        setUserStatus(creator, "DORMANT");
        adminSend(creatorChannel, adminToken, "휴면 해제 안내드립니다.").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 접근

    @Test
    @DisplayName("운영팀 채널만 열린다 — 브랜드↔인플루언서 쌍 스레드는 조회 · 전송 · 읽음 모두 403이다")
    void pairThreadIsNotOpened() throws Exception {
        sellerSend(thread, "하윤님, 촬영 일정 공유드려요.").andExpect(status().isCreated());

        adminMessages(thread.getId(), adminToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        adminSend(thread, adminToken, "쌍 스레드에는 쓸 수 없다").andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_THREADS + thread.getId() + "/read").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());

        assertThat(countMessagesWithContent("쌍 스레드에는 쓸 수 없다")).isZero();
    }

    @Test
    @DisplayName("없는 스레드는 404, 운영자가 아니면 403이다")
    void unknownThreadAndNonAdmin() throws Exception {
        adminMessages(999_999L, adminToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("THREAD_NOT_FOUND"));
        adminSend(999_999L, adminToken, UUID.randomUUID().toString(), "없는 스레드", null).andExpect(status().isNotFound());

        adminMessages(brandChannel.getId(), brandToken).andExpect(status().isForbidden());
        adminSend(brandChannel, brandToken, "브랜드가 어드민 API로").andExpect(status().isForbidden());
        markRead(brandChannel, creatorToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("쌍 스레드의 기존 응답은 말풍선 그대로다 — 카드 필드가 생겼어도 일반 메시지는 TEXT · card=null이다")
    void pairThreadResponseIsUnchanged() throws Exception {
        sellerSend(thread, "하윤님, 촬영 일정 공유드려요.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageType").value("TEXT"))
                .andExpect(jsonPath("$.card").value(nullValue()))
                .andExpect(jsonPath("$.mine").value(true));

        creatorMessages(thread)
                .andExpect(jsonPath("$.content[0].messageType").value("TEXT"))
                .andExpect(jsonPath("$.content[0].card").value(nullValue()))
                .andExpect(jsonPath("$.content[0].mine").value(false));
    }
}
