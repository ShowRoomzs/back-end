package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.api.creator.auth.service.CreatorApplicationService;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P0 — 명칭 · 설정 묶음(13절): 운영자 사업자 정보 공용 API(10-1) · 연결 검색어 필수(10-3) · 취소 거부 사유 3종(5-3) ·
 * 입점 반려 통지(9) · 서명 기한 제안값(1-7) · 고정 지급비 시점 정의(1-9).
 */
@DisplayName("1021 P0 — 사업자 정보 · 검색어 필수 · 거부 사유 · 반려 통지 · 서명 기한 제안 · 고정 지급비 정의")
class Design1021P0IntegrationTest extends SettlementTestSupport {

    @Autowired private CreatorApplicationRepository applications;
    @Autowired private CreatorApplicationService applicationService;

    @Test
    @DisplayName("기-35 — 공용 사업자 정보는 인증 없이 실제 값 · 미정 값은 null")
    void companyInfo() throws Exception {
        mockMvc.perform(get("/v1/common/company-info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("쇼룸즈"))
                .andExpect(jsonPath("$.representative").value("소병서"))
                .andExpect(jsonPath("$.registrationNumber").value("791-12-03353"))
                .andExpect(jsonPath("$.mailOrderReportNumber").value(nullValue()))
                .andExpect(jsonPath("$.privacyOfficer.name").value("소병서"))
                .andExpect(jsonPath("$.customerService.phone").value(nullValue()))
                .andExpect(jsonPath("$.copyright").value("© 2026 Showroomz"));
    }

    @Test
    @DisplayName("기-23 — 인플루언서 검색은 검색어 필수(빈 검색어로 전체 열람 불가)")
    void creatorSearchRequiresKeyword() throws Exception {
        sellerGet("/v1/seller/connections/creators").andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/seller/connections/creators").param("keyword", "  ")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isBadRequest());
        sellerGet("/v1/seller/connections/creators?keyword=" + creator.getShowroomName()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기-33 — 취소 거부 사유 「주문 제작·맞춤 상품」은 받지 않는다(400) · 3종은 그대로")
    void madeToOrderRejectReasonRemoved() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        OrderCancelRequest request = seedCancelRequest(group, itemsOf(group));
        String url = SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/reject";
        sellerPost(url, Map.of("reasonCode", "MADE_TO_ORDER")).andExpect(status().isBadRequest());
        sellerPost(url, Map.of("reasonCode", "ALREADY_PACKED")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기-36 — 반려 통지: 미리보기 = 메일 문안 · 통지 라벨 · 상세 사유 미포함 · 신청자 응답도 통지 라벨만")
    void rejectionNotice() throws Exception {
        Users applicant = createConsumer("reject-notice", "반려대상");
        Long applicationId = inTransaction(() -> applications.save(CreatorApplication.createApplication(applicant,
                SnsType.INSTAGRAM, "https://instagram.com/notice", "notice", 3_000, "notice-biz@showroomz.test",
                "김통지", "1995-01-01", "01011112222"))).getId();
        String base = "/v1/admin/creator/applications/" + applicationId;

        adminGet(base + "/reject-preview?reasonType=SUSPECTED_FAKE_FOLLOWERS").andExpect(status().isOk())
                .andExpect(jsonPath("$.internalLabel").value("허위 팔로워 의심"))
                .andExpect(jsonPath("$.noticeLabel").value("채널 운영 기준 미충족"))
                .andExpect(jsonPath("$.reapplyAvailableOn").value(LocalDate.now().plusDays(14).toString()));

        org.mockito.Mockito.when(mailService.sendCreatorRejectionNotice(anyString(), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        adminPost(base + "/reject", Map.of("rejectReasonType", "SUSPECTED_FAKE_FOLLOWERS",
                "rejectReasonDetail", "팔로워 급증 구간 다수 — 내부 기록")).andExpect(status().isNoContent());
        // 58 계획 5-24 — 발송 결과가 반려 상세에 남는다
        adminGet(base).andExpect(status().isOk()).andExpect(jsonPath("$.noticeSent").value(true));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendCreatorRejectionNotice(eq("notice-biz@showroomz.test"), anyString(), body.capture());
        assertThat(body.getValue()).contains("채널 운영 기준 미충족").doesNotContain("허위 팔로워").doesNotContain("내부 기록");
        verify(mailService, never()).sendCreatorRejectionEmail(anyString(), anyString(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyString());

        CreatorApplication saved = applications.findById(applicationId).orElseThrow();
        assertThat(saved.getBusinessEmail()).isNull();
        assertThat(saved.getRejectReasonDetail()).isEqualTo("팔로워 급증 구간 다수 — 내부 기록");
        assertThat(applicationService.getMyRejectedApplication(applicant.getId()).getRejectReason())
                .isEqualTo("채널 운영 기준 미충족");
    }

    @Test
    @DisplayName("58 5-24 — 반려 통지 메일이 실패하면 상세 noticeSent=false · 반려는 그대로")
    void rejectionNoticeFailureRecorded() throws Exception {
        Users applicant = createConsumer("reject-notice-fail", "반려실패");
        Long applicationId = inTransaction(() -> applications.save(CreatorApplication.createApplication(applicant,
                SnsType.INSTAGRAM, "https://instagram.com/notice-fail", "noticefail", 3_000, "notice-fail@showroomz.test",
                "김실패", "1995-01-01", "01011113333"))).getId();
        String base = "/v1/admin/creator/applications/" + applicationId;
        adminGet(base).andExpect(status().isOk()).andExpect(jsonPath("$.noticeSent").value(nullValue()));

        org.mockito.Mockito.when(mailService.sendCreatorRejectionNotice(anyString(), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("smtp down")));
        adminPost(base + "/reject", Map.of("rejectReasonType", "OTHER")).andExpect(status().isNoContent());

        adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.noticeSent").value(false));
    }

    @Test
    @DisplayName("기-07 — 검토 대기 계약에 서명 기한 제안값(공구 시작 3일 전 23:55) · 기-28 고정 지급비 시점 정의")
    void signatureDeadlineSuggestionAndFixedFeeDescription() throws Exception {
        Contract contract = groupBuy.getContract();
        LocalDateTime startAt = LocalDateTime.now().plusDays(20).withHour(10).withMinute(0).withSecond(0).withNano(0);
        jdbc.update("UPDATE contract SET status = 'REVIEW_PENDING', group_buy_start_at = ? WHERE contract_id = ?",
                startAt, contract.getId());

        adminGet("/v1/admin/contracts/" + contract.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.signature.deadlineSuggestedAt")
                        .value(org.hamcrest.Matchers.startsWith(startAt.toLocalDate().minusDays(3) + "T23:55")))
                .andExpect(jsonPath("$.signature.deadlineOverdueWhileHeld").value(false));

        String trigger = jdbc.queryForObject("SELECT fixed_fee_trigger FROM contract WHERE contract_id = ?",
                String.class, contract.getId());
        if (trigger != null) {
            sellerGet("/v1/seller/contracts/" + contract.getId()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.fixedFee.triggerDescription")
                            .value(FixedFeeTrigger.valueOf(trigger).getDescription()))
                    .andExpect(jsonPath("$.fixedFee.paymentBusinessDays").value(nullValue()));
        }
    }
}
