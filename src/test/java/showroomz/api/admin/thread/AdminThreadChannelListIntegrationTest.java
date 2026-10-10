package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.message.entity.MessageThread;
import showroomz.support.BrandFixture;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 좌측 목록 · 탭 배지(36 설계 3-1 · 3-2 · §36-1).
 *
 * <p>「새 대화」가 없다 — 모든 회원과 채널이 하나씩 미리 있고 찾기는 검색이다. 그래서 메시지가 없는 채널도 목록에 나와야 하고,
 * 검색은 이름 · 담당자 · 회원번호 세 축이어야 한다.
 */
@DisplayName("[통합] 어드민 소통 스레드 — 채널 목록 · 탭 배지")
class AdminThreadChannelListIntegrationTest extends AdminThreadTestSupport {

    // ------------------------------------------------------------------ 탭 · 항목

    @Test
    @DisplayName("탭별로 운영팀 채널만 나오고 회원번호 · 보조 줄 재료 · 운영팀 접두 판정값이 내려온다 — 쌍 스레드는 어느 탭에도 없다")
    void listsOperatorChannelsByTab() throws Exception {
        adminSend(brandChannel, adminToken, "9월 정산 일정 안내드립니다.").andExpect(status().isCreated());

        channels("BRAND")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].threadId").value(brandChannel.getId()))
                .andExpect(jsonPath("$.content[0].tab").value("BRAND"))
                .andExpect(jsonPath("$.content[0].name").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].memberNo").value("BRD-" + brand.marketId()))
                .andExpect(jsonPath("$.content[0].memberId").value(brand.marketId()))
                .andExpect(jsonPath("$.content[0].managerName").value("김담당"))
                .andExpect(jsonPath("$.content[0].businessType").value(nullValue()))
                .andExpect(jsonPath("$.content[0].memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("9월 정산 일정 안내드립니다."))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true))
                .andExpect(jsonPath("$.content[0].lastMessageAt").exists())
                .andExpect(jsonPath("$.content[0].unreadCount").value(0))
                .andExpect(jsonPath("$.content[0].writable").value(true));

        channels("INFLUENCER")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].threadId").value(creatorChannel.getId()))
                .andExpect(jsonPath("$.content[0].tab").value("INFLUENCER"))
                .andExpect(jsonPath("$.content[0].name").value("뷰티_하윤"))
                .andExpect(jsonPath("$.content[0].memberNo").value("INF-" + creator.getId()))
                .andExpect(jsonPath("$.content[0].memberId").value(creator.getId()))
                .andExpect(jsonPath("$.content[0].managerName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].businessType").value("INDIVIDUAL"));
    }

    @Test
    @DisplayName("채널은 메시지가 없어도 목록에 있다 — 브랜드 채널은 빈 채로, 인플루언서 채널은 가입 안내로 열린다")
    void channelsExistBeforeAnyConversation() throws Exception {
        channels("BRAND")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value(nullValue()))
                .andExpect(jsonPath("$.content[0].lastMessageAt").value(nullValue()))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(false))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));

        // 가입 안내는 운영팀(시스템) 발신이라 「운영팀: 」 접두가 붙고, 운영팀이 읽을 메시지가 아니다.
        channels("INFLUENCER")
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value(CREATOR_WELCOME))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(true))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));
    }

    @Test
    @DisplayName("마지막 발신이 상대면 접두가 없고 운영팀 안 읽은 수가 된다")
    void memberLastMessageHasNoOperatorPrefix() throws Exception {
        creatorSend(creatorChannel, "정산 계좌 변경은 어디서 하나요?").andExpect(status().isCreated());

        channels("INFLUENCER")
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("정산 계좌 변경은 어디서 하나요?"))
                .andExpect(jsonPath("$.content[0].lastMessageByOperator").value(false))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));
    }

    @Test
    @DisplayName("미리보기는 255자에서 잘린다")
    void previewIsTruncated() throws Exception {
        adminSend(brandChannel, adminToken, "가".repeat(300)).andExpect(status().isCreated());

        channels("BRAND").andExpect(jsonPath("$.content[0].lastMessagePreview", hasLength(255)));
    }

    // ------------------------------------------------------------------ 정렬 · 페이지

    @Test
    @DisplayName("최근 메시지순이고 메시지가 없는 채널은 맨 아래다 — 빈 채널끼리는 나중에 생긴 채널이 위다")
    void sortsByLastMessageWithEmptyChannelsLast() throws Exception {
        MessageThread mood = channelOf(brandWithChannel("mood@showroomz.test", "무드코스메틱").market());
        MessageThread pure = channelOf(brandWithChannel("pure@showroomz.test", "퓨어랩").market());
        MessageThread daily = channelOf(brandWithChannel("daily@showroomz.test", "데일리랩").market());
        backdateLastMessage(mood, now.minusHours(1));
        backdateLastMessage(pure, now.minusDays(3));

        channels("BRAND")
                .andExpect(jsonPath("$.content[*].name").value(contains("무드코스메틱", "퓨어랩", "데일리랩", "글로우랩")))
                .andExpect(jsonPath("$.content[*].threadId").value(contains(
                        mood.getId().intValue(), pure.getId().intValue(), daily.getId().intValue(),
                        brandChannel.getId().intValue())));

        // 오래된 채널에 새 메시지가 오면 맨 위로 올라간다.
        sellerSend(brandChannel, "정산 문의드립니다.").andExpect(status().isCreated());
        channels("BRAND").andExpect(jsonPath("$.content[0].name").value("글로우랩"));
    }

    @Test
    @DisplayName("페이지를 나눠 내린다 — 1부터 시작하고 전체 건수 · 다음 페이지 여부가 함께 온다")
    void paginates() throws Exception {
        brandWithChannel("mood@showroomz.test", "무드코스메틱");
        brandWithChannel("pure@showroomz.test", "퓨어랩");

        channels("BRAND", "page", "1", "size", "2")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.pageInfo.currentPage").value(1))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
        channels("BRAND", "page", "2", "size", "2")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false));
    }

    // ------------------------------------------------------------------ 검색

    @Test
    @DisplayName("브랜드 탭은 브랜드명 · 담당자 · 회원번호로 찾는다 — 숫자가 아닌 회원번호는 전체가 아니라 0건이다")
    void searchesBrandsByNameManagerAndNumber() throws Exception {
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        jdbc.update("UPDATE seller SET name = ? WHERE seller_id = ?", "이현", mood.seller().getId());

        channels("BRAND").andExpect(jsonPath("$.content", hasSize(2)));
        channels("BRAND", "keyword", "글로우").andExpect(jsonPath("$.content[*].name").value(contains("글로우랩")));
        channels("BRAND", "keyword", "이현").andExpect(jsonPath("$.content[*].name").value(contains("무드코스메틱")))
                .andExpect(jsonPath("$.content[0].managerName").value("이현"));
        channels("BRAND", "keyword", "brd-" + mood.marketId()).andExpect(jsonPath("$.content[*].name").value(contains("무드코스메틱")));
        channels("BRAND", "keyword", "  BRD-" + brand.marketId() + "  ").andExpect(jsonPath("$.content[*].name").value(contains("글로우랩")));

        channels("BRAND", "keyword", "BRD-abc").andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        channels("BRAND", "keyword", "BRD-999999").andExpect(jsonPath("$.content", hasSize(0)));
        channels("BRAND", "keyword", "없는브랜드").andExpect(jsonPath("$.content", hasSize(0)));
        // 공백뿐인 검색어는 검색하지 않은 것과 같다.
        channels("BRAND", "keyword", "   ").andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("인플루언서 탭은 쇼룸명 · 회원번호로 찾는다 — 브랜드 회원번호는 이 탭에서 이름 검색으로 남아 걸리지 않는다")
    void searchesInfluencersByShowroomAndNumber() throws Exception {
        var minji = createCreator("민지의 쇼룸", "minji");
        channelOf(minji);

        channels("INFLUENCER").andExpect(jsonPath("$.content", hasSize(2)));
        channels("INFLUENCER", "keyword", "하윤").andExpect(jsonPath("$.content[*].name").value(contains("뷰티_하윤")));
        channels("INFLUENCER", "keyword", "INF-" + minji.getId()).andExpect(jsonPath("$.content[*].name").value(contains("민지의 쇼룸")));
        channels("INFLUENCER", "keyword", "INF-x1").andExpect(jsonPath("$.content", hasSize(0)));
        channels("INFLUENCER", "keyword", "BRD-" + brand.marketId()).andExpect(jsonPath("$.content", hasSize(0)));
        // 쇼룸명 검색에는 판매 담당자 축이 없다.
        channels("INFLUENCER", "keyword", "김담당").andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ------------------------------------------------------------------ 회원 상태

    @Test
    @DisplayName("회원 상태가 목록에 실린다 — 정지 · 휴면은 쓰기가 열려 있고 탈퇴만 닫힌다")
    void memberStatusDecidesWritable() throws Exception {
        setMarketStatus(brand, "SUSPENDED");
        channels("BRAND").andExpect(jsonPath("$.content[0].memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.content[0].writable").value(true));

        setMarketStatus(brand, "DORMANT");
        channels("BRAND").andExpect(jsonPath("$.content[0].memberStatus").value("DORMANT"))
                .andExpect(jsonPath("$.content[0].writable").value(true));

        setMarketStatus(brand, "WITHDRAWN");
        channels("BRAND").andExpect(jsonPath("$.content[0].memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.content[0].writable").value(false));

        // 인플루언서는 계정(USERS)의 상태를 본다. 탈퇴해도 채널은 기록으로 남아 목록에 나온다.
        setUserStatus(creator, "WITHDRAWN");
        channels("INFLUENCER").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.content[0].writable").value(false));
    }

    // ------------------------------------------------------------------ 요청 검증 · 권한

    @Test
    @DisplayName("탭은 필수다 — 없거나 모르는 값이면 400이다(이슈 스레드 탭 ISSUE 는 44 이슈 스레드 설계서 4-1 로 생겼다)")
    void tabIsRequired() throws Exception {
        channels(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        channels("PAIR").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        channels("ISSUE").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("운영자만 본다 — 브랜드 · 인플루언서 토큰은 403, 토큰이 없으면 401이다")
    void onlyAdminsCanList() throws Exception {
        for (String token : new String[]{brandToken, creatorToken}) {
            mockMvc.perform(get(ADMIN_CHANNELS).param("tab", "BRAND").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(ADMIN_SUMMARY).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(ADMIN_CHANNELS).param("tab", "BRAND")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_SUMMARY)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 탭 배지

    @Test
    @DisplayName("탭 배지의 안 읽은 수는 그 탭 전 채널의 합이다 — 운영팀 · 시스템 발신과 쌍 스레드는 세지 않는다")
    void summaryUnreadSumsAcrossChannels() throws Exception {
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        MessageThread moodChannel = channelOf(mood.market());

        sellerSend(brandChannel, "계약 취소 요청드립니다.").andExpect(status().isCreated());
        sellerSend(brandChannel, "브랜드 서명은 이미 했습니다.").andExpect(status().isCreated());
        mockMvc.perform(post("/v1/seller/threads/" + moodChannel.getId() + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, sellerToken(mood.seller())).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "입점 서류 재제출했습니다."))))
                .andExpect(status().isCreated());
        creatorSend(creatorChannel, "정산 계좌 변경은 어디서 하나요?").andExpect(status().isCreated());
        // 운영팀 채널이 아닌 쌍 스레드의 대화는 운영팀이 읽을 메시지가 아니다.
        sellerSend(thread, "하윤님, 촬영 일정 공유드려요.").andExpect(status().isCreated());
        adminSend(brandChannel, adminToken, "확인 후 처리하겠습니다.").andExpect(status().isCreated());

        summaryOf(adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand.unreadCount").value(3))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(0))
                .andExpect(jsonPath("$.influencer.unreadCount").value(1))
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(0))
                .andExpect(jsonPath("$.issue.openCount").value(0))
                .andExpect(jsonPath("$.issue.closedCount").value(0))
                .andExpect(jsonPath("$.issue.unreadCount").value(0));
    }

    @Test
    @DisplayName("탭 배지의 미처리 카드 수 — 카드가 있고 · 알림 전이고 · 계약이 서명 진행중인 재발송 요청만 요청자 탭에 센다")
    void summaryPendingCardsCountOnlyActionableCards() throws Exception {
        Contract byCreator = seed(ContractStatus.SIGNING);
        Contract bySeller = seed(ContractStatus.SIGNING);
        Contract canceledLater = seed(ContractStatus.SIGNING);
        Contract legacy = seed(ContractStatus.SIGNING);

        requestResendByCreator(byCreator).andExpect(status().isOk());
        requestResendBySeller(bySeller).andExpect(status().isOk());
        requestResendByCreator(canceledLater).andExpect(status().isOk());
        // 카드 도입 이전에 쌓인 요청 — 채널에 카드가 없어 배지에 셀 수 없다(계약 관리 큐가 받는다).
        resendRequested(legacy, ContractActorType.CREATOR, now.minusDays(1));

        summaryOf(adminToken)
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(2))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(1));

        cancel(canceledLater, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null).andExpect(status().isOk());

        summaryOf(adminToken)
                .andExpect(jsonPath("$.influencer.pendingCardCount").value(1))
                .andExpect(jsonPath("$.brand.pendingCardCount").value(1));
    }
}
