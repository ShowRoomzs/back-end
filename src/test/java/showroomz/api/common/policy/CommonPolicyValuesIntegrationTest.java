package showroomz.api.common.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.global.config.properties.MemberProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.support.IntegrationTestSupport;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공용 정책 기한 값(58 계획 6-1) — 인증 없이 · 설정값 그대로 · 미정 값은 null. */
@DisplayName("공용 정책 기한 값")
class CommonPolicyValuesIntegrationTest extends IntegrationTestSupport {

    @Autowired private OrderProperties orderProperties;
    @Autowired private MemberProperties memberProperties;

    @Test
    @DisplayName("인증 없이 설정값 그대로 · 설정 없는 값은 null")
    void defaults() throws Exception {
        mockMvc.perform(get("/v1/common/policy-values")).andExpect(status().isOk())
                .andExpect(jsonPath("$.claimInspectDueBusinessDays").value(2))
                .andExpect(jsonPath("$.claimRefundDueBusinessDays").value(1))
                .andExpect(jsonPath("$.claimAutoReceiveBusinessDays").doesNotExist())
                .andExpect(jsonPath("$.trackingStallAlertDays").value(7))
                .andExpect(jsonPath("$.stallConsumerChoiceDays").value(7))
                .andExpect(jsonPath("$.settlementReviewBusinessDays").value(3))
                .andExpect(jsonPath("$.settlementPayoutBusinessDays").value(3))
                .andExpect(jsonPath("$.adjustmentDeadlineBusinessDays").value(10))
                .andExpect(jsonPath("$.fixedFeePaymentBusinessDays").value(nullValue()))
                .andExpect(jsonPath("$.sanctionAppealBusinessDays").value(3))
                .andExpect(jsonPath("$.sanctionAppealReviewBusinessDays").value(nullValue()))
                .andExpect(jsonPath("$.cancelRequestRespondBusinessDays").value(2))
                .andExpect(jsonPath("$.creatorAccountReviewBusinessDays").value(nullValue()))
                .andExpect(jsonPath("$.groupBuySuspensionAppealBusinessDays").value(3));
    }

    @Test
    @DisplayName("설정이 바뀌면 응답도 바뀐다")
    void followsProperties() throws Exception {
        int inspectDue = orderProperties.getClaim().getInspectDueBusinessDays();
        Integer review = memberProperties.getSanction().getAppealReviewBusinessDays();
        try {
            orderProperties.getClaim().setInspectDueBusinessDays(5);
            memberProperties.getSanction().setAppealReviewBusinessDays(4);
            mockMvc.perform(get("/v1/common/policy-values")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.claimInspectDueBusinessDays").value(5))
                    .andExpect(jsonPath("$.sanctionAppealReviewBusinessDays").value(4));
        } finally {
            orderProperties.getClaim().setInspectDueBusinessDays(inspectDue);
            memberProperties.getSanction().setAppealReviewBusinessDays(review);
        }
    }
}
