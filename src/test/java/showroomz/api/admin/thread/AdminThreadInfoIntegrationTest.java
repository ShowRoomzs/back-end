package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;
import showroomz.domain.groupbuy.type.SuspensionWithdrawReason;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.service.BrandSanctionService;
import showroomz.domain.member.brand.service.BrandWithdrawalEligibility;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.service.CreatorSanctionService;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.service.SettlementAmounts;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.service.SettlementInput;
import showroomz.domain.settlement.service.SettlementNumberGenerator;
import showroomz.domain.settlement.service.SettlementRates;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스레드 헤더 · 접이식 정보 바(36 설계 3-4 · §36-2).
 *
 * <p>정보 바는 <b>이 화면에서 계산하지 않는다</b> — 계약 · 공구 · 연결 모듈의 집계를 읽는다. 집계할 모듈이 없는 값은
 * 0이 아니라 null이어야 한다(0은 「없음」이라는 사실이고 null은 「모름」이다).
 */
@DisplayName("[통합] 어드민 소통 스레드 — 헤더 · 정보 바")
class AdminThreadInfoIntegrationTest extends AdminThreadTestSupport {

    @Autowired private GroupBuyRepository groupBuys;
    @Autowired private GroupBuyIssueRepository issues;
    @Autowired private GroupBuyAdminSuspensionRepository adminSuspensions;
    @Autowired private SettlementRepository settlements;
    @Autowired private showroomz.domain.settlement.repository.SettlementPayoutRepository payouts;
    @Autowired private SettlementCalculator settlementCalculator;
    @Autowired private SettlementNumberGenerator settlementNumbers;
    @Autowired private BrandSanctionService brandSanctionService;
    @Autowired private CreatorSanctionService creatorSanctionService;
    @Autowired private SettlementClawbackRepository clawbacks;
    @Autowired private BrandWithdrawalEligibility withdrawalEligibility;

    private int clawbackSeq;

    // ------------------------------------------------------------------ ① 정보

    @Test
    @DisplayName("브랜드 채널 — 담당자 이름 · 연락처 · 가입일이 오고, 인플루언서 칸은 비어 있다")
    void brandProfile() throws Exception {
        info(brandChannel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value(brandChannel.getId()))
                .andExpect(jsonPath("$.tab").value("BRAND"))
                .andExpect(jsonPath("$.name").value("글로우랩"))
                .andExpect(jsonPath("$.memberNo").value("BRD-" + brand.marketId()))
                .andExpect(jsonPath("$.memberId").value(brand.marketId()))
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.writable").value(true))
                .andExpect(jsonPath("$.profile.managerName").value("김담당"))
                .andExpect(jsonPath("$.profile.managerContact").value("010-1111-2222"))
                .andExpect(jsonPath("$.profile.joinedAt").exists())
                .andExpect(jsonPath("$.profile.businessType").value(nullValue()))
                .andExpect(jsonPath("$.profile.businessEmail").value(nullValue()))
                .andExpect(jsonPath("$.profile.instagramUrl").value(nullValue()))
                .andExpect(jsonPath("$.profile.taxType").value(nullValue()));
    }

    @Test
    @DisplayName("인플루언서 채널 — 담당자 대신 사업자 여부 · 업무용 이메일이 온다. 과세 유형은 저장 값이 없어 null이다")
    void creatorProfile() throws Exception {
        info(creatorChannel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tab").value("INFLUENCER"))
                .andExpect(jsonPath("$.name").value("뷰티_하윤"))
                .andExpect(jsonPath("$.memberNo").value("INF-" + creator.getId()))
                .andExpect(jsonPath("$.memberId").value(creator.getId()))
                .andExpect(jsonPath("$.profile.businessType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.profile.businessEmail").value("hayun-biz@showroomz.test"))
                .andExpect(jsonPath("$.profile.taxType").value(nullValue()))
                .andExpect(jsonPath("$.profile.joinedAt").exists())
                .andExpect(jsonPath("$.profile.managerName").value(nullValue()))
                .andExpect(jsonPath("$.profile.managerContact").value(nullValue()));
    }

    @Test
    @DisplayName("헤더의 회원 상태 · 쓰기 가능 여부는 목록과 같은 판정이다 — 정지 행 없이 상태만 정지면 사유를 모른다(sanction null)")
    void headerReflectsMemberStatus() throws Exception {
        setMarketStatus(brand, "WITHDRAWN");
        info(brandChannel).andExpect(jsonPath("$.memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.writable").value(false))
                .andExpect(jsonPath("$.sanction").value(nullValue()));

        setUserStatus(creator, "SUSPENDED");
        info(creatorChannel).andExpect(jsonPath("$.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.writable").value(true))
                .andExpect(jsonPath("$.sanction").value(nullValue()));
    }

    @Test
    @DisplayName("정지 회원 — 정보 바에 열린 정지의 사유 · 상세 · 정지 시각 · 종료일 · 이의 신청 기한이 실리고, 해제하면 사라진다(55 계획서 A11)")
    void sanctionReasonAndTime() throws Exception {
        LocalDateTime suspendedAt = now.minusHours(2);
        LocalDate endsOn = now.toLocalDate().plusDays(14);
        BrandSanction brandSanction = inTransaction(() -> brandSanctionService.suspend(brand.marketId(),
                BrandSuspensionReason.REPEATED_SHIPPING_DELAY, null, endsOn, admin.getId(), "김운영", suspendedAt));
        inTransaction(() -> creatorSanctionService.suspend(creator.getId(), CreatorSuspensionReason.OTHER,
                "협찬 고지 문구를 세 차례 지우셨습니다.", null, admin.getId(), "김운영", suspendedAt));

        info(brandChannel)
                .andExpect(jsonPath("$.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.sanction.reasonCode").value("REPEATED_SHIPPING_DELAY"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value("배송 지연 반복"))
                .andExpect(jsonPath("$.sanction.reasonDetail").value(nullValue()))
                .andExpect(jsonPath("$.sanction.suspendedAt").value(startsWith(suspendedAt.toString().substring(0, 16))))
                .andExpect(jsonPath("$.sanction.endsOn").value(endsOn.toString()))
                .andExpect(jsonPath("$.sanction.appealDeadlineAt")
                        .value(startsWith(brandSanction.getAppealDeadlineAt().toString().substring(0, 16))));
        info(creatorChannel)
                .andExpect(jsonPath("$.sanction.reasonCode").value("OTHER"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value("기타(직접 입력)"))
                .andExpect(jsonPath("$.sanction.reasonDetail").value("협찬 고지 문구를 세 차례 지우셨습니다."))
                .andExpect(jsonPath("$.sanction.endsOn").value(nullValue()))
                .andExpect(jsonPath("$.sanction.appealDeadlineAt").exists());

        inTransaction(() -> brandSanctionService.release(brand.marketId(), SanctionReleaseReason.RESOLVED, null,
                admin.getId(), "김운영", now));
        info(brandChannel).andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction").value(nullValue()));
    }

    // ------------------------------------------------------------------ ② 진행 중 — 계약

    @Test
    @DisplayName("계약 — 서명 진행중(서명 진행중 + 체결 처리 대기) · 체결이 각각 세어지고 종결 · 검토 단계는 빠진다")
    void contractCounts() throws Exception {
        seed(ContractStatus.SIGNING);
        seed(ContractStatus.SIGNING, s -> s.brandSignedAt(now.minusHours(5)).asOf(now.minusHours(4)));
        seed(ContractStatus.CONCLUSION_PENDING);
        seed(ContractStatus.CONCLUDED);
        seed(ContractStatus.CANCELED);
        seed(ContractStatus.EXPIRED);
        seed(ContractStatus.REVIEW_PENDING);
        seed(ContractStatus.DRAFT);

        info(brandChannel)
                .andExpect(jsonPath("$.progress.contractSigning").value(3))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
        info(creatorChannel)
                .andExpect(jsonPath("$.progress.contractSigning").value(3))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
    }

    @Test
    @DisplayName("계약 수는 그 회원 것만 센다 — 다른 브랜드 · 다른 인플루언서의 계약은 섞이지 않는다")
    void contractCountsAreScopedToMember() throws Exception {
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        Creator minji = createCreator("민지의 쇼룸", "minji");
        MessageThread minjiChannel = channelOf(minji);
        seed(ContractStatus.SIGNING, s -> s.owner(mood));
        seed(ContractStatus.SIGNING, s -> s.counterparty(minji));
        seed(ContractStatus.CONCLUDED, s -> s.owner(mood).counterparty(minji));

        info(brandChannel).andExpect(jsonPath("$.progress.contractSigning").value(1))
                .andExpect(jsonPath("$.progress.contractConcluded").value(0));
        info(channelOf(mood.market())).andExpect(jsonPath("$.progress.contractSigning").value(1))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
        info(creatorChannel).andExpect(jsonPath("$.progress.contractSigning").value(1));
        info(minjiChannel).andExpect(jsonPath("$.progress.contractSigning").value(1))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
    }

    // ------------------------------------------------------------------ ② 진행 중 — 공구 · 연결 · 미정산

    @Test
    @DisplayName("공구 — 진행중(판매 중인 상태)과 인플루언서의 종료(종료 + 정산완료)를 센다. 준비 · 중단은 빠진다")
    void groupBuyCounts() throws Exception {
        GroupBuy inProgress = concludedGroupBuy();
        GroupBuy suspensionScheduled = concludedGroupBuy();
        GroupBuy ended = concludedGroupBuy();
        GroupBuy settled = concludedGroupBuy();
        concludedGroupBuy();                                   // 준비중 그대로
        GroupBuy suspended = concludedGroupBuy();
        setStatus(inProgress, GroupBuyStatus.IN_PROGRESS);
        setStatus(suspensionScheduled, GroupBuyStatus.SUSPENSION_SCHEDULED);
        setStatus(ended, GroupBuyStatus.ENDED);
        setStatus(settled, GroupBuyStatus.SETTLED);
        setStatus(suspended, GroupBuyStatus.SUSPENDED);

        info(brandChannel)
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(2))
                .andExpect(jsonPath("$.progress.groupBuyEnded").value(nullValue()))
                .andExpect(jsonPath("$.progress.contractConcluded").value(6));
        info(creatorChannel)
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(2))
                .andExpect(jsonPath("$.progress.groupBuyEnded").value(2));
    }

    @Test
    @DisplayName("공구가 없으면 0이다 — 집계할 수 있는 「없음」은 null이 아니다")
    void zeroIsNotNull() throws Exception {
        info(brandChannel).andExpect(jsonPath("$.progress.contractSigning").value(0))
                .andExpect(jsonPath("$.progress.contractConcluded").value(0))
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.groupBuyEnded").value(0));
    }

    @Test
    @DisplayName("연결 브랜드 수는 인플루언서에만 있고 연결됨만 센다 — 요청중 · 해제는 빠진다")
    void connectedBrandCount() throws Exception {
        info(creatorChannel).andExpect(jsonPath("$.progress.connectedBrandCount").value(1));

        BrandFixture.Brand mood = fixture.createBrand("mood@showroomz.test", "무드코스메틱");
        Connection connected = Connection.requestPair(mood.market(), creator);
        connected.markConnected();
        connections.save(connected);
        connections.save(Connection.requestPair(fixture.createBrand("pure@showroomz.test", "퓨어랩").market(), creator));
        Connection disconnected = Connection.requestPair(fixture.createBrand("daily@showroomz.test", "데일리랩").market(), creator);
        disconnected.markConnected();
        disconnected.markDisconnected();
        connections.save(disconnected);

        info(creatorChannel).andExpect(jsonPath("$.progress.connectedBrandCount").value(2));
        info(brandChannel).andExpect(jsonPath("$.progress.connectedBrandCount").value(nullValue()));
    }

    @Test
    @DisplayName("미정산 건수 — 지급 완료 전 정산을 센다(분배 실패 포함). 지급 완료는 빠지고 다른 브랜드의 정산은 그 인플루언서에만 붙는다(55 계획서 A9 · D22)")
    void unsettledCount() throws Exception {
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.unsettledCount").value(0));

        Long reviewing = settlementOf(concludedGroupBuy(), SettlementStatus.REVIEWING);
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(1));
        info(creatorChannel).andExpect(jsonPath("$.progress.unsettledCount").value(1));

        setSettlementStatus(reviewing, SettlementStatus.PAID);
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.unsettledCount").value(0));

        settlementOf(concludedGroupBuy(), SettlementStatus.PAYOUT_FAILED);
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        settlementOf(concludedGroupBuy(mood), SettlementStatus.ADJUSTING);
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(1));
        info(channelOf(mood.market())).andExpect(jsonPath("$.progress.unsettledCount").value(1));
        info(creatorChannel).andExpect(jsonPath("$.progress.unsettledCount").value(2));
    }

    @Test
    @DisplayName("[55 A9-02] 미정산 건수 — 브랜드 쪽 회수 대기 차감(BRAND · PENDING)이 브랜드 미정산에 1건씩 더해진다. 인플루언서 쪽 · 처리된 차감 · "
            + "다른 브랜드의 차감은 빠지고, 숫자는 회원 상세 탈퇴 조건 「미정산」과 같다")
    void unsettledCountIncludesPendingBrandClawback() throws Exception {
        Long paid = settlementOf(concludedGroupBuy(), SettlementStatus.PAID);
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(0));

        Long pending = clawback(brand.marketId(), paid, ClawbackSide.BRAND, "PENDING");
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(1));
        // 브랜드 쪽 차감은 인플루언서 미정산에 붙지 않는다
        info(creatorChannel).andExpect(jsonPath("$.progress.unsettledCount").value(0));

        // 인플루언서 쪽 차감 · 이미 처리된(반영 · 미회수) 차감은 세지 않는다
        clawback(brand.marketId(), paid, ClawbackSide.CREATOR, "PENDING");
        clawback(brand.marketId(), paid, ClawbackSide.BRAND, "APPLIED");
        clawback(brand.marketId(), paid, ClawbackSide.BRAND, "UNRECOVERABLE");
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(1));

        // 다른 브랜드의 회수 대기 차감은 그 브랜드에만
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        clawback(mood.marketId(), settlementOf(concludedGroupBuy(mood), SettlementStatus.PAID), ClawbackSide.BRAND, "PENDING");
        info(channelOf(mood.market())).andExpect(jsonPath("$.progress.unsettledCount").value(1));

        // 지급 전 정산과 함께 — 정산 1 + 차감 1
        settlementOf(concludedGroupBuy(), SettlementStatus.REVIEWING);
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(2));
        assertThat(withdrawalEligibility.evaluate(brand.marketId()).get(BrandWithdrawalCondition.UNSETTLED)).isEqualTo(2);
        assertThat(withdrawalEligibility.evaluate(mood.marketId()).get(BrandWithdrawalCondition.UNSETTLED)).isEqualTo(1);

        // 차감이 다음 정산에 반영되면 빠진다
        jdbc.update("UPDATE settlement_clawback SET status = 'APPLIED' WHERE clawback_id = ?", pending);
        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(1));
        assertThat(withdrawalEligibility.evaluate(brand.marketId()).get(BrandWithdrawalCondition.UNSETTLED)).isEqualTo(1);
    }

    @Test
    @DisplayName("중단 예정 — 직권 중단 사전 통지(NOTICED)만 센다. 철회 · 집행되면 빠지고, 통지 없는 중단 예정 상태는 세지 않는다(55 계획서 A10 · D23)")
    void suspensionScheduledCountsOnlyNoticedAdminSuspensions() throws Exception {
        GroupBuy target = concludedGroupBuy();
        GroupBuy sellerSide = concludedGroupBuy();
        // 셀러 쪽 중단 요청으로 상태만 중단 예정인 공구 — 운영자 직권 통지가 아니다
        setStatus(sellerSide, GroupBuyStatus.SUSPENSION_SCHEDULED);
        info(brandChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(0));

        GroupBuyAdminSuspension first = notice(target, now.plusDays(2));
        info(brandChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(1));
        info(creatorChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(1));

        // 철회 — 운영 경로와 같은 조건부 UPDATE
        assertThat(inTransaction(() -> adminSuspensions.withdraw(first.getId(), SuspensionWithdrawReason.RECTIFIED,
                "정정된 표시를 확인했습니다.", admin.getId(), now))).isEqualTo(1);
        info(brandChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(0));

        // 다시 통지 → 집행(소명 기한 · 집행 예정이 지났다)
        GroupBuyAdminSuspension second = notice(target, now.minusDays(2));
        info(brandChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(1));
        assertThat(inTransaction(() -> adminSuspensions.execute(second.getId(), "위반이 확인되었습니다.", admin.getId(), now)))
                .isEqualTo(1);
        info(brandChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.groupBuySuspensionScheduled").value(0));
    }

    // ------------------------------------------------------------------ ③ 열린 이슈 스레드

    @Test
    @DisplayName("열린 이슈 스레드 — 이슈가 열린 이슈 스레드와 이행 이견 스레드가 양측 모두에게 나오고, 닫힌 이슈 · 다른 쌍의 스레드는 빠진다")
    void openIssueThreads() throws Exception {
        GroupBuy issueGroupBuy = concludedGroupBuy();
        GroupBuy fulfillmentGroupBuy = concludedGroupBuy();
        GroupBuy closedGroupBuy = concludedGroupBuy();
        MessageThread openIssue = issueThread(issueGroupBuy, thread.getConnection(), false);
        MessageThread fulfillment = threadRepository.save(MessageThread.openForGroupBuy(
                thread.getConnection(), ThreadKind.GROUP_BUY_FULFILLMENT, fulfillmentGroupBuy.getId()));
        issueThread(closedGroupBuy, thread.getConnection(), true);

        // 다른 브랜드와 같은 인플루언서의 이슈 — 인플루언서에게는 보이고 글로우랩에게는 안 보인다.
        BrandFixture.Brand mood = fixture.createBrand("mood@showroomz.test", "무드코스메틱");
        Connection moodPair = Connection.requestPair(mood.market(), creator);
        moodPair.markConnected();
        connections.save(moodPair);
        GroupBuy moodGroupBuy = concludedGroupBuy(mood);
        MessageThread moodIssue = issueThread(moodGroupBuy, moodPair, false);

        info(brandChannel)
                .andExpect(jsonPath("$.openIssueThreads", hasSize(2)))
                .andExpect(jsonPath("$.openIssueThreads[*].threadId").value(containsInAnyOrder(
                        openIssue.getId().intValue(), fulfillment.getId().intValue())))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + openIssue.getId() + ")].kind").value("GROUP_BUY_ISSUE"))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + openIssue.getId() + ")].groupBuyId")
                        .value(issueGroupBuy.getId().intValue()))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + openIssue.getId() + ")].groupBuyTitle")
                        .value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + fulfillment.getId() + ")].kind")
                        .value("GROUP_BUY_FULFILLMENT"));

        info(creatorChannel)
                .andExpect(jsonPath("$.openIssueThreads", hasSize(3)))
                .andExpect(jsonPath("$.openIssueThreads[*].threadId").value(containsInAnyOrder(
                        openIssue.getId().intValue(), fulfillment.getId().intValue(), moodIssue.getId().intValue())));
    }

    @Test
    @DisplayName("열린 이슈 스레드가 없으면 빈 배열이다")
    void noOpenIssueThreads() throws Exception {
        info(brandChannel).andExpect(jsonPath("$.openIssueThreads", hasSize(0)));
        info(creatorChannel).andExpect(jsonPath("$.openIssueThreads", hasSize(0)));
    }

    // ------------------------------------------------------------------ 접근

    @Test
    @DisplayName("운영팀 채널이 아닌 스레드는 403, 없는 스레드는 404, 운영자가 아니면 403이다")
    void access() throws Exception {
        info(thread.getId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        GroupBuy groupBuy = concludedGroupBuy();
        MessageThread issue = issueThread(groupBuy, thread.getConnection(), false);
        info(issue.getId()).andExpect(status().isForbidden());
        info(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("THREAD_NOT_FOUND"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(ADMIN_THREADS + brandChannel.getId() + "/info")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 적재

    /** 체결 API로 공구를 만든다 — 체결 트랜잭션이 공구를 만드는 운영 경로를 그대로 탄다. */
    private GroupBuy concludedGroupBuy() throws Exception {
        return concludedGroupBuy(brand);
    }

    private GroupBuy concludedGroupBuy(BrandFixture.Brand owner) throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.owner(owner));
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);
        conclude(c).andExpect(status().isOk());
        return groupBuys.findByContractId(c.getId()).orElseThrow();
    }

    private void setStatus(GroupBuy groupBuy, GroupBuyStatus status) {
        jdbc.update("UPDATE group_buy SET status = ? WHERE group_buy_id = ?", status.name(), groupBuy.getId());
    }

    /** 정산 1건 — 금액만 계산해 두고(명세 행 없음) 상태는 SQL로 옮긴다. 생성 경로는 정산 테스트가 검증한다. */
    private Long settlementOf(GroupBuy groupBuy, SettlementStatus status) {
        Long id = inTransaction(() -> {
            GroupBuy g = groupBuys.findDetailById(groupBuy.getId()).orElseThrow();
            SettlementAmounts amounts = settlementCalculator.calculate(new SettlementInput(1_000_000, 100_000, 0, 0, 0, 0,
                    CreatorBusinessType.INDIVIDUAL, new SettlementRates(new BigDecimal("0.03"), BigDecimal.ZERO,
                    new BigDecimal("0.10"), new BigDecimal("0.03"), new BigDecimal("0.003"))));
            Long settlementId = settlements.save(Settlement.builder()
                    .settlementNumber(settlementNumbers.nextSettlementNumber(now))
                    .groupBuy(g).contract(g.getContract()).market(g.getMarket()).creator(g.getCreator())
                    .creatorBusinessType(CreatorBusinessType.INDIVIDUAL)
                    .periodStartAt(g.getStartAt()).periodEndAt(g.getEndAt()).ordersClosedAt(now.minusDays(1))
                    .createdAt(now.minusDays(1)).reviewDueAt(now.plusDays(3))
                    .breakdown(new Settlement.Breakdown(1_000_000, 0, 0, 0, 0, 0, 0, 0))
                    .amounts(amounts).build()).getId();
            // 정산 생성과 같이 수취자 행 3개 — 브랜드 미정산은 브랜드 행 기준이다(51 설계서 1-5)
            payouts.saveAll(java.util.List.of(
                    showroomz.domain.settlement.entity.SettlementPayout.waiting(settlementId,
                            showroomz.domain.settlement.type.SettlementPayee.BRAND, amounts.brandPayoutAmount()),
                    showroomz.domain.settlement.entity.SettlementPayout.waiting(settlementId,
                            showroomz.domain.settlement.type.SettlementPayee.CREATOR, amounts.creatorPayoutAmount()),
                    showroomz.domain.settlement.entity.SettlementPayout.waiting(settlementId,
                            showroomz.domain.settlement.type.SettlementPayee.PLATFORM, amounts.platformShareAmount())));
            return settlementId;
        });
        setSettlementStatus(id, status);
        return id;
    }

    /** 정산 상태와 함께 수취자 행을 운영 흐름의 상태로 맞춘다 — 분배 실패는 브랜드 몫도 실패로 둔다(브랜드 쪽 「분배 실패 포함」 확인용). */
    private void setSettlementStatus(Long settlementId, SettlementStatus status) {
        jdbc.update("UPDATE settlement SET status = ? WHERE settlement_id = ?", status.name(), settlementId);
        String payout = switch (status) {
            case REVIEWING -> "WAITING";
            case ADJUSTING -> "HELD";
            case PAYOUT_SCHEDULED -> "SCHEDULED";
            case PAID -> "PAID";
            case PAYOUT_FAILED -> "FAILED";
        };
        jdbc.update("UPDATE settlement_payout SET status = ? WHERE settlement_id = ?", payout, settlementId);
    }

    /** 차감 1건 — 생성은 환불 경로(정산 테스트) 몫이라 행만 만들고 상태는 SQL로 옮긴다. 금액 · 사유는 판정에 쓰이지 않는다. */
    private Long clawback(Long marketId, Long originSettlementId, ClawbackSide side, String status) {
        SettlementClawback saved = clawbacks.save(SettlementClawback.builder()
                .clawbackNumber("CLW-%04d".formatted(++clawbackSeq)).seq(clawbackSeq).side(side)
                .originSettlementId(originSettlementId).reason("REFUND").refundAmount(10_000).amount(3_000)
                .marketId(marketId).creatorId(creator.getId()).createdAt(now).build());
        if (!"PENDING".equals(status)) {
            jdbc.update("UPDATE settlement_clawback SET status = ? WHERE clawback_id = ?", status, saved.getId());
        }
        return saved.getId();
    }

    /** 직권 중단 사전 통지 — 집행 예정 = 소명 기한 + 1일. 공구는 운영 경로처럼 중단 예정으로 옮긴다. */
    private GroupBuyAdminSuspension notice(GroupBuy groupBuy, LocalDateTime appealDeadlineAt) {
        setStatus(groupBuy, GroupBuyStatus.SUSPENSION_SCHEDULED);
        return inTransaction(() -> adminSuspensions.save(GroupBuyAdminSuspension.notice(
                groupBuys.findById(groupBuy.getId()).orElseThrow(), SuspensionReasonClause.ART17_1_LAW,
                "게시물 본문에 효능 단정 표현이 있습니다.", appealDeadlineAt.plusDays(1), appealDeadlineAt, 1, 0, 0L,
                admin.getId(), now.minusDays(3))));
    }

    private MessageThread issueThread(GroupBuy groupBuy, Connection pair, boolean closed) {
        MessageThread issueThread = threadRepository.save(
                MessageThread.openForGroupBuy(pair, ThreadKind.GROUP_BUY_ISSUE, groupBuy.getId()));
        GroupBuyIssue issue = GroupBuyIssue.open(groupBuy, GroupBuyActorType.SELLER, groupBuy.getMarket().getId(),
                GroupBuyIssueType.SETTLEMENT_AMOUNT, "정산 예정 금액에 이견이 있습니다.", issueThread.getId(), now);
        if (closed) {
            issue.close(now);
        }
        issues.save(issue);
        return issueThread;
    }
}
