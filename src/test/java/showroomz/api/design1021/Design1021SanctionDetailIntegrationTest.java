package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.service.BrandSanctionService;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.service.CreatorSanctionService;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.service.UserSanctionService;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.scheduler.SanctionAutoReleaseScheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 7절 — 정지 기간 · 자동 해제 · 이의 신청 기한 · 정지 중 탈퇴의 P7 기본 흐름 밖: 영업일 기한 계산 · 인플루언서 지급 보류 해제 ·
 * 운영팀 해제 카드 · 소비자 해제 토스트 · 배치 · 계정 상태 기간 블록 · 정지 중 탈퇴 신청 성공 · 운영자 해제와의 경계.
 */
@DisplayName("1021 7절 정지 상세 — 이의 기한 · 자동 해제 훅(지급 · 카드 · 토스트) · 배치 · 계정 상태 · 정지 중 탈퇴 신청")
class Design1021SanctionDetailIntegrationTest extends SettlementTestSupport {

    @Autowired private BrandSanctionService brandSanctionService;
    @Autowired private BrandSanctionRepository brandSanctions;
    @Autowired private CreatorSanctionService creatorSanctionService;
    @Autowired private CreatorSanctionRepository creatorSanctions;
    @Autowired private UserSanctionService userSanctionService;
    @Autowired private UserSanctionRepository userSanctions;
    @Autowired private CreatorWithdrawalService creatorWithdrawalService;

    // ------------------------------------------------------------------ 7-2 이의 신청 기한

    @Test
    @DisplayName("이의 신청 기한 = 통지 + 3영업일 23:59:59 — 금요일 통지는 수요일 · 토요일 통지는 다음 영업일(월)부터 세어 목요일")
    void appealDeadlineCountsBusinessDays() {
        LocalDateTime friday = LocalDateTime.of(2026, 11, 6, 10, 0);
        BrandSanction brandSanction = inTransaction(() -> brandSanctionService.suspend(brand.marketId(),
                BrandSuspensionReason.PRODUCT_INFO_FALSIFIED, null, LocalDate.of(2026, 11, 12), operator.getId(), "운영자",
                friday));
        assertThat(brandSanction.getAppealDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 11, 11, 23, 59, 59));
        assertThat(brandSanction.getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 12));

        LocalDateTime saturday = LocalDateTime.of(2026, 11, 7, 10, 0);
        UserSanction userSanction = inTransaction(() -> userSanctionService.suspend(consumer.getId(),
                UserSuspensionReason.ABUSIVE_LANGUAGE, null, null, operator.getId(), "운영자", saturday));
        assertThat(userSanction.getAppealDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 11, 12, 23, 59, 59));
        assertThat(userSanction.getEndsOn()).isNull();

        String payload = jdbc.queryForObject("SELECT card_payload FROM message WHERE message_id = ?", String.class,
                brandSanctions.findById(brandSanction.getId()).orElseThrow().getSuspendCardId());
        assertThat(payload).contains("2026-11-12").contains("appealDeadlineAt");
    }

    @Test
    @DisplayName("기간 없는 정지(종료일 비움)는 자동 해제 대상이 아니다 — 배치 후보에도 없다")
    void indefiniteSuspensionStays() {
        LocalDateTime now = LocalDateTime.now();
        inTransaction(() -> brandSanctionService.suspend(brand.marketId(), BrandSuspensionReason.REPEATED_SHIPPING_DELAY,
                null, null, operator.getId(), "운영자", now));

        assertThat(brandSanctions.findMemberIdsToAutoRelease(now.toLocalDate().plusYears(1))).doesNotContain(brand.marketId());
        assertThat(inTransaction(() -> brandSanctionService.releaseByTerm(brand.marketId(), now.plusYears(1)))).isNull();
        assertThat(marketStatus()).isEqualTo("SUSPENDED");
    }

    // ------------------------------------------------------------------ 7-2 자동 해제 훅

    @Test
    @DisplayName("인플루언서 지급 정지 사유 · 기간 종료 — 시스템 해제 · 보류된 지급 예정 행 복귀 · 운영팀 해제 카드 · 처리 이력 주체 시스템 · 자동 해제 표식")
    void creatorAutoReleaseRestoresPayout() {
        Settlement s = seedSettlement("정지 해제 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ? "
                + "AND payee = 'CREATOR'", LocalDate.now().plusDays(2), s.getId());
        LocalDateTime now = LocalDateTime.now();
        CreatorSanction sanction = inTransaction(() -> creatorSanctionService.suspend(creator.getId(),
                CreatorSuspensionReason.SETTLEMENT_INFO_MISMATCH, null, now.toLocalDate(), operator.getId(), "운영자", now));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("CREATOR_SUSPENDED");

        assertThat(inTransaction(() -> creatorSanctionService.releaseByTerm(creator.getId(), now))).isNull();
        LocalDateTime nextDay = now.toLocalDate().plusDays(1).atTime(0, 5);
        assertThat(inTransaction(() -> creatorSanctionService.releaseByTerm(creator.getId(), nextDay))).isNotNull();

        CreatorSanction released = creatorSanctions.findById(sanction.getId()).orElseThrow();
        assertThat(released.getReleaseReason()).isEqualTo(SanctionReleaseReason.TERM_ENDED);
        assertThat(released.isAutoReleased()).isTrue();
        assertThat(released.getReleasedBy()).isNull();
        assertThat(released.getReleaseCardId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM creator WHERE creator_id = ?", String.class, creator.getId()))
                .isEqualTo("ACTIVE");
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getFailCode()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_admin_history WHERE creator_id = ? "
                + "AND event_type = 'SUSPENSION_RELEASED' AND actor_type = 'SYSTEM'", Long.class, creator.getId()))
                .isEqualTo(1L);
        // 한 번 풀린 정지는 다시 풀지 않는다.
        assertThat(inTransaction(() -> creatorSanctionService.releaseByTerm(creator.getId(), nextDay))).isNull();
    }

    @Test
    @DisplayName("소비자 기간 종료 — 「제재 기간 종료」 해제 · 회원 정상 · 해제 토스트 · 이력 주체 시스템 · 계정 상태의 기간 블록")
    void consumerAutoRelease() throws Exception {
        LocalDate today = LocalDate.now();
        adminPost("/v1/admin/users/" + consumer.getId() + "/suspend",
                Map.of("reason", "RETURN_ABUSE", "endsOn", today.toString())).andExpect(status().isOk());
        consumerAccountStatus().andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspension.term.endsOn").value(today.toString()))
                .andExpect(jsonPath("$.suspension.term.periodDays").value(1))
                .andExpect(jsonPath("$.suspension.term.autoReleaseAt").value(org.hamcrest.Matchers.startsWith(
                        today.plusDays(1).toString())));

        assertThat(inTransaction(() -> userSanctionService.releaseByTerm(consumer.getId(),
                today.plusDays(1).atTime(0, 5)))).isNotNull();

        UserSanction released = userSanctions.findAll().stream()
                .filter(u -> u.getUserId().equals(consumer.getId())).findFirst().orElseThrow();
        assertThat(released.getReleaseReason()).isEqualTo(UserReleaseReason.TERM_ENDED);
        assertThat(released.isAutoReleased()).isTrue();
        consumerAccountStatus().andExpect(jsonPath("$.status").value("NORMAL"))
                .andExpect(jsonPath("$.releaseNotice.releasedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_admin_history WHERE user_id = ? "
                + "AND event_type = 'SUSPENSION_RELEASED' AND actor_type = 'SYSTEM'", Long.class, consumer.getId()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("배치 1회 — 종료일이 지난 브랜드 · 인플루언서 정지만 해제 · 아직 남은 정지는 그대로")
    void schedulerReleasesOnlyEndedTerms() {
        LocalDateTime now = LocalDateTime.now();
        inTransaction(() -> brandSanctionService.suspend(brand.marketId(), BrandSuspensionReason.PRODUCT_INFO_FALSIFIED,
                null, now.toLocalDate(), operator.getId(), "운영자", now));
        inTransaction(() -> creatorSanctionService.suspend(creator.getId(), CreatorSuspensionReason.OTHER, "기간 남음",
                now.toLocalDate().plusDays(5), operator.getId(), "운영자", now));
        jdbc.update("UPDATE brand_sanction SET ends_on = ? WHERE market_id = ?", now.toLocalDate().minusDays(1),
                brand.marketId());

        new SanctionAutoReleaseScheduler(brandSanctions, creatorSanctions, userSanctions, brandSanctionService,
                creatorSanctionService, userSanctionService).tick();

        assertThat(marketStatus()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM creator WHERE creator_id = ?", String.class, creator.getId()))
                .isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("운영자 해제가 먼저면 기간 종료 해제는 할 일이 없다 · 운영자 해제 사유로 「정지 기간 종료」는 고를 수 없다")
    void operatorReleaseFirst() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        inTransaction(() -> creatorSanctionService.suspend(creator.getId(), CreatorSuspensionReason.OTHER, "확인 필요",
                now.toLocalDate(), operator.getId(), "운영자", now));

        assertThatThrownBy(() -> inTransaction(() -> creatorSanctionService.release(creator.getId(),
                SanctionReleaseReason.TERM_ENDED, null, operator.getId(), "운영자", now)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        inTransaction(() -> creatorSanctionService.release(creator.getId(), SanctionReleaseReason.RESOLVED, null,
                operator.getId(), "운영자", now));

        assertThat(inTransaction(() -> creatorSanctionService.releaseByTerm(creator.getId(), now.plusDays(2)))).isNull();
    }

    // ------------------------------------------------------------------ 계정 상태 · 정지 중 탈퇴

    @Test
    @DisplayName("스튜디오 계정 상태 — 기간 3일(오늘 포함) · 자동 해제 시각 · 이의 기한 · 소명 검토 영업일 미정 null")
    void creatorAccountStatusTerm() throws Exception {
        LocalDate today = LocalDate.now();
        inTransaction(() -> creatorSanctionService.suspend(creator.getId(), CreatorSuspensionReason.GROUP_BUY_NONPERFORMANCE,
                null, today.plusDays(2), operator.getId(), "운영자", LocalDateTime.now()));

        creatorGet("/v1/creator/account-status").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.sanction.term.endsOn").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.sanction.term.periodDays").value(3))
                .andExpect(jsonPath("$.sanction.term.appealDeadlineAt").isNotEmpty())
                .andExpect(jsonPath("$.sanction.term.appealReviewBusinessDays").doesNotExist());
    }

    @Test
    @DisplayName("기-03 — 남은 정산 · 계약이 없는 인플루언서는 정지 중에도 탈퇴 신청이 접수된다")
    void suspendedCreatorWithoutBlockersRequestsWithdrawal() {
        Creator fresh = createCreator("정지_쇼룸", "suspended-fresh");
        LocalDateTime now = LocalDateTime.now();
        inTransaction(() -> creatorSanctionService.suspend(fresh.getId(), CreatorSuspensionReason.FALSE_INFORMATION, null,
                now.toLocalDate().plusDays(7), operator.getId(), "운영자", now));

        var request = inTransaction(() -> creatorWithdrawalService.request(fresh.getId(), "활동을 그만둡니다", now));

        assertThat(request.getId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM creator WHERE creator_id = ?", String.class, fresh.getId()))
                .isEqualTo("SUSPENDED");
    }

    // ------------------------------------------------------------------ helpers

    private String marketStatus() {
        return jdbc.queryForObject("SELECT status FROM market WHERE market_id = ?", String.class, brand.marketId());
    }

    private org.springframework.test.web.servlet.ResultActions consumerAccountStatus() throws Exception {
        return mockMvc.perform(get("/v1/user/account-status").header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk());
    }
}
