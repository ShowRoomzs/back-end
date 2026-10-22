package showroomz.api.admin.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P2 — 게시물 수정 제한(2-2 · 기-08) · 직권 중단 양측 통지 · 운영팀 채널 카드 · 인플루언서 의견(2-3 · 기-21).
 */
@DisplayName("1021 P2 — 게시물 수정 제한 · 직권 중단 양측 카드 · 인플루언서 의견")
class Design1021P2GroupBuyIntegrationTest extends AdminGroupBuyTestSupport {

    @Autowired private OperatorChannelService operatorChannels;

    @Test
    @DisplayName("기-08 — 준비완료(예약) 게시물은 수정 불가 · 노출 중지면 시정 허용 · 진행 중은 본문만(제목 변경 400 · 노출 중지면 제목도)")
    void postEditRules() throws Exception {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        GroupBuyPost readyPost = seedPost(ready.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        edit(ready.getId(), readyPost.getTitle(), "본문 수정").andExpect(status().isConflict());
        studioDetail(ready.getId()).andExpect(jsonPath("$.permissions.canEditPost").value(false));

        GroupBuy readyHidden = seedIn(GroupBuyStatus.READY);
        GroupBuyPost hiddenPost = seedPost(readyHidden.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        edit(readyHidden.getId(), "제목 시정", "본문 시정").andExpect(status().isOk());

        GroupBuy live = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost livePost = seedPost(live.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        studioDetail(live.getId()).andExpect(jsonPath("$.permissions.canEditPost").value(true))
                .andExpect(jsonPath("$.permissions.canEditPostTitle").value(false));
        edit(live.getId(), "다른 제목", "본문 수정").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_TITLE_LOCKED"));
        edit(live.getId(), livePost.getTitle(), "본문만 수정").andExpect(status().isOk())
                .andExpect(jsonPath("$.post.content").value("본문만 수정"));

        GroupBuy liveHidden = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(liveHidden.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        studioDetail(liveHidden.getId()).andExpect(jsonPath("$.permissions.canEditPostTitle").value(true));
        edit(liveHidden.getId(), "시정한 제목", "시정한 본문").andExpect(status().isOk());
    }

    @Test
    @DisplayName("기-21 — 사전 통지는 양측 운영팀 채널에 카드 · 인플루언서에게 소명 기한 · 의견 채널 · 어드민은 인플루언서 의견 수를 본다")
    void suspensionNoticeBothSides() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        LocalDateTime appeal = businessCalendar.addBusinessDays(LocalDate.now(), 3).atTime(23, 59, 59);
        LocalDateTime execute = businessCalendar.addBusinessDays(appeal.toLocalDate(), 1).atTime(10, 0);

        adminAction(groupBuy.getId(), "admin-suspension/notice", Map.of("clause", "ART17_1_LAW",
                "executeScheduledAt", execute.toString(), "appealDeadlineAt", appeal.toString(),
                "noticeBody", "게시물 본문에 효능 단정 표현이 있습니다.")).andExpect(status().isOk());

        Long brandChannel = operatorChannels.findMarketChannelThreadId(brand.market()).orElseThrow();
        Long creatorChannel = operatorChannels.findCreatorChannelThreadId(creator).orElseThrow();
        for (Long channel : new Long[]{brandChannel, creatorChannel}) {
            mockMvc.perform(get("/v1/admin/threads/" + channel + "/messages").header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].card.cardType").value("GROUP_BUY_SUSPENSION_NOTICE"))
                    .andExpect(jsonPath("$.content[0].card.detail.notice.kind").value("ADMIN_SUSPENSION"))
                    .andExpect(jsonPath("$.content[0].card.detail.notice.counterpartyNoticed").value(true));
        }

        studioDetail(groupBuy.getId())
                .andExpect(jsonPath("$.adminSuspension.appealDeadlineAt", notNullValue()))
                .andExpect(jsonPath("$.adminSuspension.operatorChannelThreadId").value(creatorChannel));
        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.adminSuspension.creatorOpinion.replyCount").value(0));

        mockMvc.perform(post("/v1/creator/threads/" + creatorChannel + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("clientMessageId", UUID.randomUUID().toString(),
                                "content", "표현을 수정하겠습니다."))))
                .andExpect(status().isCreated());
        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.adminSuspension.creatorOpinion.replyCount").value(1));
    }

    private ResultActions edit(long groupBuyId, String title, String content) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("content", content);
        return mockMvc.perform(patch("/v1/creator/group-buys/" + groupBuyId + "/post")
                .header(HttpHeaders.AUTHORIZATION, creatorToken()).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    private ResultActions studioDetail(long groupBuyId) throws Exception {
        return mockMvc.perform(get("/v1/creator/group-buys/" + groupBuyId)
                .header(HttpHeaders.AUTHORIZATION, creatorToken()));
    }

    private String creatorToken() {
        return bearerToken(creator.getUser().getUsername(), RoleType.CREATOR, creator.getUser().getId());
    }
}
