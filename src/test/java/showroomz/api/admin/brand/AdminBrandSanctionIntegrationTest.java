package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageSanctionChannelGateway;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 4 ~ 6절 — 정지 · 해제(M1) · 브랜드 몫 지급 정지 연동 · 정지 중 차단 가드. 지급 행은 시드 정산의 수취자 행을 SQL 로
 * 원하는 상태에 둔 뒤 조치가 어느 행을 옮기는지 본다.
 */
@DisplayName("어드민 브랜드 정지 · 해제 · 지급 정지 · 차단 가드(52 시나리오 SP · RL · PH · GD)")
class AdminBrandSanctionIntegrationTest extends AdminBrandTestSupport {

    private static final String BLOCKED_SUFFIX = " 수 없습니다. 운영팀 소통 스레드로 문의해 주세요.";

    @Autowired private MessageSanctionChannelGateway channelGateway;

    // ------------------------------------------------------------------ 4-1 정지

    @Test
    @DisplayName("SP-01 지급 정지 여부는 사유가 정한다 — 정산 정보 불일치 · 사업자 정보 허위만 · 바디로 켤 수 없다")
    void payoutHoldFollowsReason() throws Exception {
        for (BrandSuspensionReason reason : BrandSuspensionReason.values()) {
            BrandFixture.Brand target = cleanBrand("사유" + reason.ordinal());
            adminPost(base(target.marketId()) + "/suspend",
                    Map.of("reason", reason.name(), "reasonDetail", "상세", "payoutHeld", true))
                    .andExpect(status().isOk());
            BrandSanction sanction = sanctionRepository.findByMarketIdAndReleasedAtIsNull(target.marketId()).orElseThrow();
            assertThat(sanction.isPayoutHeld()).as(reason.name()).isEqualTo(reason.isHoldsPayout());
        }
        assertThat(BrandSuspensionReason.values()).filteredOn(BrandSuspensionReason::isHoldsPayout)
                .containsExactly(BrandSuspensionReason.SETTLEMENT_INFO_MISMATCH, BrandSuspensionReason.BUSINESS_INFO_FALSIFIED);
    }

    @Test
    @DisplayName("SP-02 입력 형식 — 사유 없음 · 모르는 사유 · 상세 501자 400 · 500자 통과 · 비-기타 상세는 다듬어 저장 · 공백이면 null")
    void suspendInputFormat() throws Exception {
        BrandFixture.Brand target = cleanBrand("입력형식");
        Long id = target.marketId();
        adminPost(base(id) + "/suspend", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        suspend(id, "FOO", null).andExpect(status().isBadRequest());
        suspend(id, "OTHER", "가".repeat(501)).andExpect(status().isBadRequest());
        assertThat(sanctionRepository.findAll()).isEmpty();

        suspend(id, "OTHER", "가".repeat(500)).andExpect(status().isOk());
        release(id, Map.of()).andExpect(status().isOk());
        suspend(id, "PRODUCT_INFO_FALSIFIED", "  과장 광고  ").andExpect(status().isOk());
        assertThat(sanctionRepository.findByMarketIdAndReleasedAtIsNull(id).orElseThrow().getReasonDetail())
                .isEqualTo("과장 광고");
        release(id, Map.of()).andExpect(status().isOk());
        suspend(id, "PRODUCT_INFO_FALSIFIED", "   ").andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.items[0].detail").value("상품 정보 허위·과장"));
        assertThat(sanctionRepository.findByMarketIdAndReleasedAtIsNull(id).orElseThrow().getReasonDetail()).isNull();
    }

    @Test
    @DisplayName("SP-03 · SP-04 상태 경계 · 검증 순서 — 탈퇴 409 · 정지 행 없는 정지 409 · 휴면은 정지 가능(해제하면 활성) · 기타 상세 검증이 대상 확인보다 먼저")
    void suspendStateBoundaries() throws Exception {
        BrandFixture.Brand withdrawn = cleanBrand("경계탈퇴");
        approve(withdrawn.marketId(), withdrawalService.request(withdrawn.marketId(), null, null, LocalDateTime.now()).getId())
                .andExpect(status().isOk());
        suspend(withdrawn.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        suspend(withdrawn.marketId(), "OTHER", "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRAND_SUSPEND_DETAIL_REQUIRED"));
        suspend(999_999L, "OTHER", "  ").andExpect(status().isBadRequest());

        BrandFixture.Brand rowless = cleanBrand("경계행없음");
        suspendWithoutSanctionRow(rowless.marketId());
        suspend(rowless.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_ALREADY_SUSPENDED"));

        BrandFixture.Brand dormant = cleanBrand("경계휴면");
        setMarketStatus(dormant.marketId(), MarketStatus.DORMANT);
        suspend(dormant.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        assertThat(marketStatus(dormant.marketId())).isEqualTo(MarketStatus.SUSPENDED);
        release(dormant.marketId(), Map.of()).andExpect(status().isOk());
        assertThat(marketStatus(dormant.marketId())).isEqualTo(MarketStatus.ACTIVE);

        assertThat(sanctionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("SP-05 운영팀 채널이 없으면 정지가 만든다 — 카드 1장 · 정지 블록의 소명 스레드 = 그 채널")
    void suspendCreatesOperatorChannel() throws Exception {
        BrandFixture.Brand target = cleanBrand("채널없음");
        brandDetail(target.marketId()).andExpect(jsonPath("$.operatorThread").doesNotExist());

        suspend(target.marketId(), "CONSUMER_RESPONSE_NEGLECT", null).andExpect(status().isOk());
        JsonNode body = brandDetailJson(target.marketId());
        long threadId = body.at("/operatorThread/threadId").asLong();
        assertThat(threadId).isPositive();
        assertThat(body.at("/rail/currentStatus/sanction/threadId").asLong()).isEqualTo(threadId);
        assertThat(operatorCards(target.marketId())).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
        BrandSanction sanction = sanctionRepository.findByMarketIdAndReleasedAtIsNull(target.marketId()).orElseThrow();
        assertThat(sanction.getSuspendCardId()).isNotNull();
    }

    @Test
    @DisplayName("SP-07 조치 응답 = 레일 전체 — 같은 시점 상세의 rail 과 같다")
    void suspendRespondsWithRail() throws Exception {
        BrandFixture.Brand target = cleanBrand("레일응답");
        JsonNode response = json(suspend(target.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk()));
        Set<String> keys = new HashSet<>();
        response.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("currentStatus", "pendingChips", "withdrawalReview", "histories");
        assertThat(response).isEqualTo(brandDetailJson(target.marketId()).path("rail"));
    }

    // ------------------------------------------------------------------ 4-2 해제

    @Test
    @DisplayName("RL-01 해제 사유 — 바디 없음 · 빈 바디는 소명 · 시정 확인 · 오정지 + 안내 · 탈퇴 종결은 고를 수 없다 · 안내 501자 400 · 공백 안내는 null")
    void releaseReasons() throws Exception {
        BrandFixture.Brand target = cleanBrand("해제사유");
        Long id = target.marketId();

        suspend(id, "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        postAs(adminToken, base(id) + "/release", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.items[0].detail").value("소명 · 시정 확인"));
        assertThat(latestSanction(id).getReleaseReason().name()).isEqualTo("RESOLVED");

        suspend(id, "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        release(id, Map.of("reason", "WITHDRAWN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("탈퇴 종결은 운영자가 고를 수 있는 해제 사유가 아닙니다."));
        release(id, Map.of("note", "가".repeat(501))).andExpect(status().isBadRequest());
        assertThat(marketStatus(id)).isEqualTo(MarketStatus.SUSPENDED);
        release(id, Map.of("reason", "MISTAKE", "note", "사업자 정보 재확인")).andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.items[0].type").value("SUSPENSION_RELEASED"))
                .andExpect(jsonPath("$.histories.items[0].detail").value("오정지 — 사업자 정보 재확인"));
        BrandSanction mistaken = latestSanction(id);
        assertThat(mistaken.getReleaseReason().name()).isEqualTo("MISTAKE");
        assertThat(mistaken.getReleaseNote()).isEqualTo("사업자 정보 재확인");
        assertThat(mistaken.getReleasedByName()).isEqualTo("김운영");

        suspend(id, "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        release(id, Map.of("note", "   ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.items[0].detail").value("소명 · 시정 확인"));
        assertThat(latestSanction(id).getReleaseNote()).isNull();
    }

    @Test
    @DisplayName("RL-02 · RL-03 해제 경계 — 활성 409 · 탈퇴 409 · 정지 행 없는 정지는 상태만 되돌린다(카드 · 지급 없음 · 이력 ref 없음)")
    void releaseBoundaries() throws Exception {
        BrandFixture.Brand active = cleanBrand("해제활성");
        release(active.marketId(), Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_SUSPENDED"));

        BrandFixture.Brand withdrawn = cleanBrand("해제탈퇴");
        approve(withdrawn.marketId(), withdrawalService.request(withdrawn.marketId(), null, null, LocalDateTime.now()).getId())
                .andExpect(status().isOk());
        release(withdrawn.marketId(), Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));

        BrandFixture.Brand rowless = cleanBrand("해제행없음");
        suspendWithoutSanctionRow(rowless.marketId());
        release(rowless.marketId(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.histories.items[0].type").value("SUSPENSION_RELEASED"))
                .andExpect(jsonPath("$.histories.items[0].refId").doesNotExist());
        assertThat(marketStatus(rowless.marketId())).isEqualTo(MarketStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connection WHERE type = 'OPERATOR_MARKET' AND market_id = ?",
                Integer.class, rowless.marketId())).isZero();
    }

    @Test
    @DisplayName("RL-04 해제 다음 영업일 — 10.02(금) 해제 → 10.06(화) · 10.08(목) 해제 → 10.12(월)(주말 · 대체공휴일 · 한글날 건너뜀)")
    void releaseDueIsNextBusinessDay() throws Exception {
        fixHolidays();
        Settlement s = seedSettlement("영업일 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));

        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        sanctionService.release(brand.marketId(), null, null, operator.getId(), "김운영", LocalDateTime.of(2026, 10, 2, 15, 0));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 6));

        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        sanctionService.release(brand.marketId(), null, null, operator.getId(), "김운영", LocalDateTime.of(2026, 10, 8, 15, 0));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        // 해제 이력은 인자로 준 과거 시각에 찍히므로 순서가 아니라 건수를 본다.
        assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.PAYOUT_BLOCKED::equals).hasSize(2);
        assertThat(settlementEvents(s.getId())).filteredOn(SettlementEventType.PAYOUT_HOLD_RELEASED::equals).hasSize(2);
    }

    @Test
    @DisplayName("RL-05 · RL-06 지급 유지 사유 해제는 지급 행을 건드리지 않는다 · 재정지는 새 정지 행 · 카드 3장 누적")
    void releaseWithoutHoldAndResuspend() throws Exception {
        Settlement s = seedSettlement("유지 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        LocalDate due = LocalDate.now().plusDays(3);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, due);

        suspend(brand.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        SettlementPayout untouched = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(untouched.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(untouched.getDueDate()).isEqualTo(due);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED,
                SettlementEventType.PAYOUT_HOLD_RELEASED);

        suspend(brand.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        assertThat(sanctionRepository.findAll()).hasSize(2);
        assertThat(operatorCards(brand.marketId())).containsExactly(MessageCardType.ACCOUNT_SUSPENDED,
                MessageCardType.ACCOUNT_SUSPENSION_RELEASED, MessageCardType.ACCOUNT_SUSPENDED);
        JsonNode histories = json(adminGet(base() + "/histories")).path("content");
        assertThat(textsAt(histories, "type")).containsSubsequence("SUSPENDED", "SUSPENSION_RELEASED", "SUSPENDED");
    }

    @Test
    @DisplayName("RL-07 카드 멱등 — 같은 정지 id 로 두 번 올려도 카드는 1장")
    void sanctionCardIsIdempotent() {
        BrandFixture.Brand target = cleanBrand("멱등카드");
        MessageThread channel = operatorChannel(target);
        LocalDateTime now = LocalDateTime.now();
        Long first = channelGateway.postSuspendedCard(channel, MessageSanctionChannelGateway.SanctionKind.BRAND, 4242L,
                "배송 지연 반복", now, operator.getId(), null);
        Long second = channelGateway.postSuspendedCard(channel, MessageSanctionChannelGateway.SanctionKind.BRAND, 4242L,
                "배송 지연 반복", now, operator.getId(), null);
        assertThat(second).isEqualTo(first);
        assertThat(operatorCards(target.marketId())).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
    }

    // ------------------------------------------------------------------ 5 지급 정지 연동

    @Test
    @DisplayName("PH-01 정지가 옮기는 행 — 브랜드 몫 예정(SCHEDULED)만 · 확정 전 · 지시 후 · 완료 · 실패 · 다른 수취자 · 남의 마켓 불변")
    void holdMovesOnlyScheduledBrandRows() throws Exception {
        LocalDate due = LocalDate.now().plusDays(3);
        Map<PayoutStatus, Settlement> byStatus = new java.util.EnumMap<>(PayoutStatus.class);
        for (PayoutStatus status : List.of(PayoutStatus.WAITING, PayoutStatus.HELD, PayoutStatus.SCHEDULED,
                PayoutStatus.REQUESTED, PayoutStatus.PAID, PayoutStatus.FAILED)) {
            Settlement s = seedSettlement("보류 " + status, 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
            setPayout(s.getId(), SettlementPayee.BRAND, status, 900_000, null, due);
            setPayout(s.getId(), SettlementPayee.CREATOR, PayoutStatus.SCHEDULED, 50_000, null, due);
            setPayout(s.getId(), SettlementPayee.PLATFORM, PayoutStatus.SCHEDULED, 50_000, null, due);
            byStatus.put(status, s);
        }
        BrandFixture.Brand other = cleanBrand("남의지급");
        Settlement others = settlementOf(other, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(others.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, due);

        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());

        byStatus.forEach((status, s) -> {
            SettlementPayout row = payout(s.getId(), SettlementPayee.BRAND);
            if (status == PayoutStatus.SCHEDULED) {
                assertThat(row.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
                assertThat(row.getFailCode()).isEqualTo(PayoutBlockReason.BRAND_SUSPENDED.name());
                assertThat(settlementEvents(s.getId())).containsOnlyOnce(SettlementEventType.PAYOUT_BLOCKED);
            } else {
                assertThat(row.getStatus()).as(status.name()).isEqualTo(status);
                assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED);
            }
            assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        });
        assertThat(payout(others.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("PH-02 여러 건 — 예정 3행 보류 → 해제 시 3행 모두 다음 영업일 예정 · 정산마다 이력 1쌍")
    void holdAndReleaseSeveralRows() throws Exception {
        List<Settlement> settlements = List.of(
                seedSettlement("다건1", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED),
                seedSettlement("다건2", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED),
                seedSettlement("다건3", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED));
        for (Settlement s : settlements) {
            setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        }
        suspend(brand.marketId(), "BUSINESS_INFO_FALSIFIED", null).andExpect(status().isOk());
        for (Settlement s : settlements) {
            assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        }
        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        LocalDate next = businessCalendar.addBusinessDays(LocalDate.now(), 1);
        for (Settlement s : settlements) {
            SettlementPayout row = payout(s.getId(), SettlementPayee.BRAND);
            assertThat(row.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(row.getDueDate()).isEqualTo(next);
            assertThat(row.getFailCode()).isNull();
            assertThat(settlementEvents(s.getId())).containsOnlyOnce(SettlementEventType.PAYOUT_BLOCKED,
                    SettlementEventType.PAYOUT_HOLD_RELEASED);
        }
    }

    @Test
    @DisplayName("PH-03 정지 중 확정으로 보류된 행도 해제가 다음 영업일 예정으로 올린다")
    void releaseRowsHeldAtConfirmation() throws Exception {
        registerSellerAccount();
        Settlement s = seedSettlement("정지 중 확정", 1_000_000, SettlementStatus.REVIEWING);
        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        confirm(s);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        SettlementPayout row = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(row.getDueDate()).isEqualTo(businessCalendar.addBusinessDays(LocalDate.now(), 1));
    }

    @Test
    @DisplayName("PH-04 다른 사유로 보류된 브랜드 행 — 정지도 해제도 건드리지 않는다(PG 재등록 경로가 푼다)")
    void otherHoldCodesAreUntouched() throws Exception {
        Settlement s = seedSettlement("PG 보류", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.BLOCKED, 900_000, "ACCOUNT_HOLDER_MISMATCH",
                LocalDate.now().plusDays(3));

        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getFailCode()).isEqualTo("ACCOUNT_HOLDER_MISMATCH");
        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        SettlementPayout row = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(row.getFailCode()).isEqualTo("ACCOUNT_HOLDER_MISMATCH");
    }

    @Test
    @DisplayName("PH-05 판정은 정지 행의 스냅샷을 본다 — 지급 유지 사유라도 행이 지급 정지면 확정이 보류한다")
    void holdReadsSnapshot() throws Exception {
        registerSellerAccount();
        Settlement s = seedSettlement("스냅샷 정산", 1_000_000, SettlementStatus.REVIEWING);
        suspend(brand.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        jdbc.update("UPDATE brand_sanction SET payout_held = true WHERE market_id = ?", brand.marketId());

        confirm(s);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getFailCode()).isEqualTo(PayoutBlockReason.BRAND_SUSPENDED.name());
    }

    @Test
    @DisplayName("PH-06 · PH-07 상세 · 정산 화면 표시 — 미정산액에 보류 금액 포함 · 07b · 파트너 정산이 사유 코드 · 라벨을 그대로 낸다")
    void holdIsVisible() throws Exception {
        Settlement s = seedSettlement("표시 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());

        brandDetail(brand.marketId()).andExpect(jsonPath("$.operations.payoutHeld").value(true))
                .andExpect(jsonPath("$.operations.unsettledAmount").value(900_000));
        String admin = adminGet("/v1/admin/settlements/" + s.getId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).contains("BRAND_SUSPENDED").contains(PayoutBlockReason.BRAND_SUSPENDED.getLabel());
        // 파트너 정산 화면은 상태 라벨을 「지급 예정」으로 두고 계정 정지 보류를 payoutHold 배지로 따로 낸다(1017 설계서 S5 · S6).
        getAs(brandToken, "/v1/seller/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutHold.reason").value("BRAND_SUSPENDED"))
                .andExpect(jsonPath("$.payoutHold.label").value(PayoutBlockReason.BRAND_SUSPENDED.getLabel()))
                .andExpect(jsonPath("$.payoutHold.sanction").value(true));
        String partner = getAs(brandToken, "/v1/seller/settlements/" + s.getId()).andReturn().getResponse()
                .getContentAsString();
        assertThat(partner).contains("지급 예정");

        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        brandDetail(brand.marketId()).andExpect(jsonPath("$.operations.payoutHeld").value(false))
                .andExpect(jsonPath("$.operations.unsettledAmount").value(900_000));
    }

    @Test
    @DisplayName("PH-08 파트너 기본정보 — 정지 중 brandStatus · sanction 배너 재료 · 해제 뒤 null")
    void partnerBusinessInfoSanction() throws Exception {
        suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", "예금주 불일치").andExpect(status().isOk());
        getAs(brandToken, "/v1/seller/basic-info/business").andExpect(status().isOk())
                .andExpect(jsonPath("$.brandStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.sanction.reason").value("SETTLEMENT_INFO_MISMATCH"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value("정산 정보 불일치"))
                .andExpect(jsonPath("$.sanction.suspendedAt").exists())
                .andExpect(jsonPath("$.sanction.payoutHeld").value(true));
        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        getAs(brandToken, "/v1/seller/basic-info/business").andExpect(jsonPath("$.brandStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction").doesNotExist());
    }

    // ------------------------------------------------------------------ 6 차단 가드

    @Test
    @DisplayName("BD-09 ① ③ ④ 정지 중 상품 등록 · 검토 요청 · 연결 요청 409(무엇이 막혔는지 메시지) · 해제하면 즉시 풀린다(GD-04)")
    void blockedActions() throws Exception {
        long draftId = json(postAs(brandToken, "/v1/seller/contracts", Map.of("creatorId", creator.getId()))
                .andExpect(status().isCreated())).path("contractId").asLong();
        Creator stranger = createCreator("새쇼룸", "stranger");
        long productsBefore = productRepository.count();
        suspend(brand.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());

        postAs(brandToken, "/v1/seller/products", productBody()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 상품을 등록할" + BLOCKED_SUFFIX));
        postAs(brandToken, "/v1/seller/contracts/" + draftId + "/review-request", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 계약 검토를 요청할" + BLOCKED_SUFFIX));
        postAs(brandToken, "/v1/seller/connections", Map.of("creatorId", stranger.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 인플루언서에게 연결을 요청할" + BLOCKED_SUFFIX));
        postAs(brandToken, "/v1/seller/contracts", Map.of("creatorId", creator.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 새 계약을 작성할" + BLOCKED_SUFFIX));
        assertThat(productRepository.count()).isEqualTo(productsBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM contract WHERE contract_id = ?", String.class, draftId))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connection WHERE creator_id = ?", Integer.class,
                stranger.getId())).isZero();

        release(brand.marketId(), Map.of()).andExpect(status().isOk());
        postAs(brandToken, "/v1/seller/products", productBody()).andExpect(status().isCreated());
        postAs(brandToken, "/v1/seller/connections", Map.of("creatorId", stranger.getId())).andExpect(status().isCreated());
        postAs(brandToken, "/v1/seller/contracts", Map.of("creatorId", creator.getId())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BD-09 ⑤ 공구 오픈은 예외가 아니라 건너뛴다 — 같은 배치의 다른 브랜드 공구는 열린다")
    void openSkipsOnlySuspendedBrand() throws Exception {
        GroupBuy mine = seedIn(GroupBuyStatus.READY);
        BrandFixture.Brand other = cleanBrand("다른오픈");
        Creator partner = createCreator("다른쇼룸", "otheropen");
        connect(other, partner);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy theirs = seed(other, partner, "다른 브랜드 공구", now.plusDays(10), now.plusDays(17));
        moveTo(theirs.getId(), GroupBuyStatus.READY);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id IN (?, ?)", now.minusMinutes(1), mine.getId(),
                theirs.getId());
        suspend(brand.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());

        assertThat(lifecycleService.open(mine.getId(), LocalDateTime.now())).isFalse();
        assertThat(lifecycleService.open(theirs.getId(), LocalDateTime.now())).isTrue();
        assertThat(reload(mine.getId()).getStatus()).isEqualTo(GroupBuyStatus.READY);
        assertThat(reload(theirs.getId()).getStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("GD-01 · GD-02 허용 범위 — 배송 정책 · 작성중 계약 삭제 · 주문 요약 · 정산 조회 · 로그인 통과 · 진행 중 공구 상품은 판매 유지")
    void allowedWhileSuspended() throws Exception {
        long draftId = json(postAs(brandToken, "/v1/seller/contracts", Map.of("creatorId", creator.getId()))
                .andExpect(status().isCreated())).path("contractId").asLong();
        suspend(brand.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/v1/seller/basic-info/delivery-policy")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("shippingLeadDays", 3, "defaultDeliveryFee", 3000))))
                .andExpect(status().is2xxSuccessful());
        deleteAs(brandToken, "/v1/seller/contracts/" + draftId).andExpect(status().is2xxSuccessful());
        sellerSummary().andExpect(status().isOk());
        getAs(brandToken, "/v1/seller/settlements").andExpect(status().isOk());
        postAs(null, "/v1/seller/auth/login", Map.of("email", brand.email(), "password", RAW_PASSWORD))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT display_status FROM product WHERE product_id = ?", String.class,
                cream.getProductId())).isEqualTo("DISPLAY");
        assertThat(paidGroup().getId()).isNotNull();
    }

    @Test
    @DisplayName("GD-03 · GD-05 정지 가드는 market.status 만 본다 — 행 없는 정지는 막고 · 행만 있는 활성은 통과 · 탈퇴 대기는 별도 잠금 가드가 막는다(51 7절)")
    void guardReadsMarketStatusOnly() throws Exception {
        BrandFixture.Brand rowless = cleanBrand("가드행없음");
        suspendWithoutSanctionRow(rowless.marketId());
        postAs(sellerToken(rowless.seller()), "/v1/seller/contracts", Map.of("creatorId", creator.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));

        BrandFixture.Brand rowOnly = cleanBrand("가드행만");
        connect(rowOnly, creator);
        insertOpenSanction(rowOnly.marketId(), "REPEATED_SHIPPING_DELAY", false);
        postAs(sellerToken(rowOnly.seller()), "/v1/seller/contracts", Map.of("creatorId", creator.getId()))
                .andExpect(status().isCreated());

        BrandFixture.Brand pending = cleanBrand("가드대기");
        withdrawalService.request(pending.marketId(), null, null, LocalDateTime.now());
        connect(pending, creator);
        postAs(sellerToken(pending.seller()), "/v1/seller/contracts", Map.of("creatorId", creator.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_PENDING"));
    }

    // ------------------------------------------------------------------ 보조

    private BrandSanction latestSanction(Long marketId) {
        return sanctionRepository.findAll().stream().filter(s -> s.getMarketId().equals(marketId))
                .max(java.util.Comparator.comparing(BrandSanction::getId)).orElseThrow();
    }

    private Map<String, Object> productBody() {
        Long categoryId = jdbc.queryForObject("SELECT category_id FROM product WHERE product_id = ?", Long.class,
                cream.getProductId());
        return Map.of("categoryId", categoryId, "name", "정지 중 신상품", "regularPrice", 20_000);
    }
}
