package showroomz.api.common.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.refreshToken.UserRefreshToken;
import showroomz.api.app.auth.refreshToken.UserRefreshTokenRepository;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.calendar.entity.BusinessHoliday;
import showroomz.domain.calendar.repository.BusinessHolidayRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1017 설계서 P0 — 정지 배너 재료(8-10) · 공용 공휴일(4-5) · 스튜디오 로그아웃(5-4) · 1:1 문의 24시간 SLA(6-0 ⑤).
 */
@DisplayName("1017 P0 — 계정 상태 · 공휴일 · 스튜디오 로그아웃 · 문의 SLA")
class MemberAccountStatusIntegrationTest extends SettlementTestSupport {

    @Autowired private BusinessHolidayRepository holidayRepository;
    @Autowired private UserRefreshTokenRepository userRefreshTokenRepository;

    // ------------------------------------------------------------------ 파트너 계정 상태

    @Test
    @DisplayName("파트너 — 정상이면 sanction 없음 · 정지되면 사유 · 상세 · 지급 정지 · 운영팀 채널 · 정지 카드 위치")
    void sellerAccountStatus() throws Exception {
        sellerGet("/v1/seller/account-status").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction").value(nullValue()))
                .andExpect(jsonPath("$.withdrawalPending").value(false));

        adminPost("/v1/admin/brands/" + brand.marketId() + "/suspend",
                Map.of("reason", "SETTLEMENT_INFO_MISMATCH", "reasonDetail", "예금주와 상호가 다릅니다."))
                .andExpect(status().isOk());

        sellerGet("/v1/seller/account-status").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.sanction.reason").value("SETTLEMENT_INFO_MISMATCH"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value("정산 정보 불일치"))
                .andExpect(jsonPath("$.sanction.reasonDetail").value("예금주와 상호가 다릅니다."))
                .andExpect(jsonPath("$.sanction.payoutHeld").value(true))
                .andExpect(jsonPath("$.sanction.operatorChannelThreadId").isNumber())
                .andExpect(jsonPath("$.sanction.suspendCardMessageId").isNumber());
    }

    @Test
    @DisplayName("파트너 — 상세 사유가 없으면 reasonDetail null · 지급 영향 없는 사유는 payoutHeld false")
    void sellerAccountStatusWithoutDetail() throws Exception {
        adminPost("/v1/admin/brands/" + brand.marketId() + "/suspend", Map.of("reason", "REPEATED_SHIPPING_DELAY"))
                .andExpect(status().isOk());
        sellerGet("/v1/seller/account-status").andExpect(status().isOk())
                .andExpect(jsonPath("$.sanction.reasonDetail").value(nullValue()))
                .andExpect(jsonPath("$.sanction.payoutHeld").value(false));
    }

    // ------------------------------------------------------------------ 스튜디오 계정 상태

    @Test
    @DisplayName("스튜디오 — 정지 · 해제가 배너 재료에 그대로 반영된다 · 소비자 토큰은 403")
    void creatorAccountStatus() throws Exception {
        creatorGet("/v1/creator/account-status").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction").value(nullValue()));

        adminPost("/v1/admin/creators/" + creator.getId() + "/suspend",
                Map.of("reason", "AD_LAW_VIOLATION_REPEATED", "reasonDetail", "근거 없는 효능 표현"))
                .andExpect(status().isOk());
        creatorGet("/v1/creator/account-status").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value("표시광고법 위반 문구 반복"))
                .andExpect(jsonPath("$.sanction.reasonDetail").value("근거 없는 효능 표현"))
                .andExpect(jsonPath("$.sanction.payoutHeld").value(false))
                .andExpect(jsonPath("$.sanction.operatorChannelThreadId").isNumber());

        adminPost("/v1/admin/creators/" + creator.getId() + "/release", Map.of("reason", "RESOLVED"))
                .andExpect(status().isOk());
        creatorGet("/v1/creator/account-status").andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction").value(nullValue()));

        mockMvc.perform(get("/v1/creator/account-status").header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 공휴일

    @Test
    @DisplayName("공휴일 — 영업일 달력과 같은 집합 · 이름은 등록 테이블 · 이름 없는 설정 공휴일은 「공휴일」 · 인증 불요 · 기간 오류 400")
    void holidays() throws Exception {
        holidayRepository.save(new BusinessHoliday(LocalDate.of(2026, 10, 9), "한글날", operator.getId()));
        businessCalendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9)));

        mockMvc.perform(get("/v1/common/business-holidays").param("from", "2026-09-27").param("to", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$[0].name").value("공휴일"))
                .andExpect(jsonPath("$[1].date").value("2026-10-09"))
                .andExpect(jsonPath("$[1].name").value("한글날"));

        mockMvc.perform(get("/v1/common/business-holidays").param("from", "2026-11-01").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/common/business-holidays").param("from", "2026-01-01").param("to", "2027-12-31"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 스튜디오 로그아웃

    @Test
    @DisplayName("스튜디오 로그아웃 — 그 refresh token 만 폐기 · 다시 불러도 204 · 토큰 누락 400")
    void creatorLogout() throws Exception {
        userRefreshTokenRepository.save(new UserRefreshToken(String.valueOf(creator.getUser().getId()), "creator-refresh-1"));

        creatorPost("/v1/creator/auth/logout", Map.of("refreshToken", "creator-refresh-1")).andExpect(status().isNoContent());
        assertThat(userRefreshTokenRepository.findByRefreshToken("creator-refresh-1")).isNull();
        creatorPost("/v1/creator/auth/logout", Map.of("refreshToken", "creator-refresh-1")).andExpect(status().isNoContent());
        creatorPost("/v1/creator/auth/logout", Map.of()).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 1:1 문의 SLA

    @Test
    @DisplayName("1:1 문의 — 답변 기한은 24시간이다(어드민 홈 · 운영 지표와 같은 값)")
    void inquirySlaIs24Hours() throws Exception {
        mockMvc.perform(post("/v1/user/inquiries").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .content("{\"type\":\"DELIVERY\",\"content\":\"배송이 언제 오나요?\"}"))
                .andExpect(status().isCreated());

        jdbc.update("UPDATE one_to_one_inquiry SET created_at = ?", LocalDateTime.now().minusHours(23));
        adminGet("/v1/admin/inquiries").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slaExceeded").value(false));

        jdbc.update("UPDATE one_to_one_inquiry SET created_at = ?", LocalDateTime.now().minusHours(25));
        adminGet("/v1/admin/inquiries").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slaExceeded").value(true));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions creatorPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
