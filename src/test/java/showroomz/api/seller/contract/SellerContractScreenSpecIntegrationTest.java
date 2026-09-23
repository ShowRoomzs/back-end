package showroomz.api.seller.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClause;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.product.entity.Product;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기획서 {@code ui-partner-08-contracts.html} 화면 대조 — <b>서버가 값을 내려야 그려지는 것들</b>.
 *
 * <p>앞의 세 클래스가 규칙(검증·전이·권한)을 본다면 여기는 <b>화면이 서버에서 받아야 하는 값</b>을 본다.
 * 시안이 문구로 못박은 것들 — B1 정산 요약의 원천징수 자동 전환, C3 확인 모달의 요약 수치,
 * B6·B7·B8 종결 카드가 서로 다르게 그려지는 근거, B4a에서 사라지는 버튼, B5b의 공구 생성 게이트 —
 * 이 값이 비거나 뒤바뀌면 FE는 화면을 못 그리거나 없는 사실을 지어내게 된다.
 */
@DisplayName("[통합] 파트너센터 계약 화면 대조")
class SellerContractScreenSpecIntegrationTest extends SellerContractTestSupport {

    // ── B1 · 정산 조건 요약 ────────────────────────────────────────────────

    @Test
    @DisplayName("원천징수 표기는 상대 계정 정보를 따라 자동으로 바뀐다 — 브랜드 입력이 아니다")
    void settlementSummaryFollowsCounterpartyAccount() throws Exception {
        long contractId = createDraft();

        // 상대를 아직 고르지 않았으면 지어내지 않는다.
        detail(contractId)
                .andExpect(jsonPath("$.settlement.platformFeeRate").value(2))
                // PG 수수료율은 자문 회신 전이다. 0으로 내리면 화면이 「0%」= 수수료 없음으로 읽는다.
                .andExpect(jsonPath("$.settlement.pgFeeRate").doesNotExist())
                .andExpect(jsonPath("$.settlement.withholdingType").doesNotExist())
                .andExpect(jsonPath("$.settlement.withholdingLabel").doesNotExist());

        saveOk(contractId, validForm(0L));
        detail(contractId)
                .andExpect(jsonPath("$.settlement.withholdingType").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.settlement.withholdingLabel").value("원천징수 3.3%"));

        // 사업자 상대면 원천징수가 아니라 세금계산서 발행으로 바뀐다(§25-5-6).
        Creator business = createConnectedCreator("코스메_하늘", "haneul", CreatorBusinessType.BUSINESS);
        long businessContract = createDraft();
        saveOk(businessContract, validForm(0L).creator(business.getId()));
        detail(businessContract)
                .andExpect(jsonPath("$.settlement.withholdingType").value("TAX_INVOICE"))
                .andExpect(jsonPath("$.settlement.withholdingLabel").value("세금계산서 발행"));
    }

    // ── C3 · 검토 요청 확인 모달이 쓰는 값 ──────────────────────────────────

    @Test
    @DisplayName("확인 모달의 「(17일)」·예상 리워드·지급 시점 문구는 서버가 내려준 값이다")
    void detailCarriesSummaryValuesForConfirmModal() throws Exception {
        long contractId = createDraft();
        LocalDateTime startAt = LocalDateTime.of(LocalDate.now().plusDays(19), LocalTime.of(10, 0));
        LocalDateTime endAt = startAt.plusDays(16).withHour(23).withMinute(55);

        saveOk(contractId, validForm(0L)
                .period(startAt, endAt)
                .content(1, 1, 3, endAt.toLocalDate())
                .fixedFee(1_200_000, FixedFeeTrigger.POST_REGISTERED, true)
                .items(item(serum, 8_900, "45.0", 500), item(cream, 22_000, "12.0", 150)));

        detail(contractId)
                // 일수는 시작·종료 「일자」 양끝 포함이다 — 화면의 (17일)과 H4가 같은 값을 본다.
                .andExpect(jsonPath("$.period.days").value(17))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].unitReward").value(4_005))
                .andExpect(jsonPath("$.items[1].unitReward").value(2_640))
                .andExpect(jsonPath("$.fixedFee.amount").value(1_200_000))
                .andExpect(jsonPath("$.fixedFee.triggerLabel").value("공구 게시물 등록 후"))
                .andExpect(jsonPath("$.fixedFee.noticeAgreedAt").exists());

        // 지급 시점 3종의 문구도 서버가 소유한다 — 세 서피스와 계약서 PDF가 같은 값을 쓴다.
        saveOk(contractId, validForm(currentVersion(contractId)).fixedFee(500_000, FixedFeeTrigger.GROUP_BUY_ENDED, true));
        detail(contractId).andExpect(jsonPath("$.fixedFee.triggerLabel").value("공구 종료 후"));

        saveOk(contractId, validForm(currentVersion(contractId)).fixedFee(500_000, FixedFeeTrigger.SETTLEMENT_COMPLETED, true));
        detail(contractId).andExpect(jsonPath("$.fixedFee.triggerLabel").value("정산 완료 후"));
    }

    // ── B6 · B7 · B8 · 종결 3종 ────────────────────────────────────────────

    @Test
    @DisplayName("거절은 상대의 사유·메모가 그대로 전달된다 — 라벨은 거절 사유 목록에서 찾는다")
    void declinedContractShowsCounterpartyReason() throws Exception {
        Contract declined = seedInStatus(ContractStatus.DECLINED);

        detail(declined.getId())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.statusLabel").value("거절"))
                .andExpect(jsonPath("$.statusTone").value("DANGER"))
                .andExpect(jsonPath("$.closure.actorType").value("CREATOR"))
                .andExpect(jsonPath("$.closure.reasonCode").value("SCHEDULE_MISMATCH"))
                // 취소 사유 5종이 아니라 거절 사유 5종에서 찾아야 나오는 문구다 —
                // 두 enum이 close_reason_code 한 컬럼을 공유한다.
                .andExpect(jsonPath("$.closure.reasonLabel").value("일정이 맞지 않음"))
                .andExpect(jsonPath("$.closure.memo").value("일정이 맞지 않습니다."))
                .andExpect(jsonPath("$.closure.closedAt").exists())
                // 승인 완료 전 종결이라 지급 의무가 소멸한다.
                .andExpect(jsonPath("$.fixedFee.obligationAlive").value(false))
                // 「연결 상태 연결됨 유지」 — 계약만 종결되고 연결은 남는다.
                .andExpect(jsonPath("$.counterparty.connectionId").value(counterpartyConnection.getId()))
                .andExpect(jsonPath("$.permissions.canDuplicate").value(true));
    }

    @Test
    @DisplayName("만료는 사유가 없다 — 없는 사유를 지어내지 않고 열람 여부로 그 공백을 메운다")
    void expiredContractHasNoReasonButReportsViewing() throws Exception {
        Contract expired = seedInStatus(ContractStatus.EXPIRED);

        detail(expired.getId())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.statusLabel").value("만료"))
                // 아무도 거절하지 않았다 — 거절의 위험색과 구분되는 중립색이다.
                .andExpect(jsonPath("$.statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                .andExpect(jsonPath("$.closure.closedAt").exists())
                .andExpect(jsonPath("$.closure.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.closure.reasonLabel").doesNotExist())
                .andExpect(jsonPath("$.closure.memo").doesNotExist())
                .andExpect(jsonPath("$.fixedFee.obligationAlive").value(false))
                .andExpect(jsonPath("$.signature.deadlineAt").exists())
                // B7의 「계약서 열람 기록 없음」 — 거절과 달리 상대 메모가 없어 이 사실이 설명을 대신한다.
                .andExpect(jsonPath("$.signature.counterpartyViewed").value(false));

        // 상대가 스튜디오에서 계약서를 열면 그 사실이 브랜드 화면에 나타난다.
        transactionTemplate.executeWithoutResult(tx ->
                contractRepository.markCreatorViewed(expired.getId(), LocalDateTime.now().withNano(0)));
        detail(expired.getId()).andExpect(jsonPath("$.signature.counterpartyViewed").value(true));
    }

    @Test
    @DisplayName("취소는 내가 넣은 사유가 그대로 남는다 — 종결 3종이 같은 자리에 다른 값을 싣는다")
    void canceledContractShowsMyOwnReason() throws Exception {
        Contract canceled = seedInStatus(ContractStatus.CANCELED);

        detail(canceled.getId())
                .andExpect(jsonPath("$.statusLabel").value("취소"))
                .andExpect(jsonPath("$.closure.actorType").value("SELLER"))
                .andExpect(jsonPath("$.closure.reasonCode").value("SCHEDULE_CHANGE"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("공구 일정 변경"))
                .andExpect(jsonPath("$.fixedFee.obligationAlive").value(false));
    }

    // ── B4 · B4a · B4c · 서명 구간 ─────────────────────────────────────────

    @Test
    @DisplayName("[서명 안내 다시 받기]는 내 서명이 남아 있을 때만이다 — 내가 서명하면 사라진다")
    void resendGuidanceOnlyWhileMySignatureIsPending() throws Exception {
        LocalDateTime signedAt = LocalDateTime.now().minusHours(3).withNano(0);

        // B4 — 양측 미서명. 안내를 못 받았으면 다시 요청할 수 있다.
        long bothPending = seedInStatus(ContractStatus.SIGNING).getId();
        detail(bothPending)
                .andExpect(jsonPath("$.signature.brandSignedAt").doesNotExist())
                .andExpect(jsonPath("$.signature.creatorSignedAt").doesNotExist())
                .andExpect(jsonPath("$.permissions.canRequestResend").value(true));
        requestResend(bothPending).andExpect(status().isOk());

        // B4c — 상대만 서명했다. 내 서명이 남았으므로 그대로 쓸 수 있다.
        long myTurn = seedInStatus(ContractStatus.SIGNING,
                contract -> contract.updateSignatures(null, signedAt, signedAt)).getId();
        detail(myTurn)
                .andExpect(jsonPath("$.signature.creatorSignedAt").exists())
                // 서명 값은 어드민이 옮겨 적은 것이라 「언제 기준」인지가 함께 내려간다.
                .andExpect(jsonPath("$.signature.asOf").exists())
                .andExpect(jsonPath("$.permissions.canRequestResend").value(true));
        requestResend(myTurn).andExpect(status().isOk());

        // B4a — 내가 서명했다. 받을 안내가 없어 버튼이 사라지고 호출도 막힌다.
        long mySignatureDone = seedInStatus(ContractStatus.SIGNING,
                contract -> contract.updateSignatures(signedAt, null, signedAt)).getId();
        detail(mySignatureDone)
                .andExpect(jsonPath("$.signature.brandSignedAt").exists())
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false))
                // 공은 상대에게 있지만 취소는 아직 내 몫으로 남는다.
                .andExpect(jsonPath("$.permissions.canCancel").value(true));
        requestResend(mySignatureDone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_RESEND_NOT_ALLOWED"));
    }

    // ── B5 · B5b · 공구 생성 게이트 ────────────────────────────────────────

    @Test
    @DisplayName("체결완료 1건당 공구 1건이다 — 공구가 붙으면 게이트가 닫히고 딥링크만 남는다")
    void groupBuyGateOpensOncePerConcludedContract() throws Exception {
        long contractId = seedInStatus(ContractStatus.CONCLUDED).getId();

        detail(contractId)
                .andExpect(jsonPath("$.groupBuy.groupBuyId").doesNotExist())
                .andExpect(jsonPath("$.groupBuy.canCreate").value(true))
                .andExpect(jsonPath("$.permissions.canCreateGroupBuy").value(true));

        transactionTemplate.executeWithoutResult(tx ->
                contractRepository.assignGroupBuy(contractId, 777L));

        detail(contractId)
                .andExpect(jsonPath("$.groupBuy.groupBuyId").value(777))
                // 「공구가 이미 생성돼 있어 추가로 만들 수 없습니다」
                .andExpect(jsonPath("$.groupBuy.canCreate").value(false))
                .andExpect(jsonPath("$.permissions.canCreateGroupBuy").value(false))
                // 공구가 생겼다고 지급 기록까지 닫히지는 않는다 — 두 버튼은 서로 무관하다.
                .andExpect(jsonPath("$.permissions.canRecordPayment").value(true));

        // 체결 전에는 게이트 자체가 없다.
        detail(seedInStatus(ContractStatus.SIGNING).getId())
                .andExpect(jsonPath("$.groupBuy.canCreate").value(false));
    }

    // ── A2 · A2a · 빈 목록 두 종류 ─────────────────────────────────────────

    @Test
    @DisplayName("계약이 없는 것과 검색 결과가 없는 것은 다른 화면이다 — 탭 카운트가 갈라준다")
    void separatesEmptyStateFromEmptySearchResult() throws Exception {
        // A2 — 계약이 아예 없다. 탭 카운트도 전부 0이다.
        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        summary()
                .andExpect(jsonPath("$.tabCounts.ALL").value(0))
                .andExpect(jsonPath("$.tabCounts.DRAFT").value(0))
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(0))
                .andExpect(jsonPath("$.actionRequiredCount").value(0));

        seedInStatus(ContractStatus.DRAFT);

        // A2a — 조건에 맞는 것만 없다. 「다른 탭에는 있을 수 있습니다」를 카운트가 뒷받침한다.
        mockMvc.perform(get(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .param("keyword", "앵콜"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        summary().andExpect(jsonPath("$.tabCounts.ALL").value(1));
    }

    @Test
    @DisplayName("목록은 20건씩·50건씩 두 가지로 끊는다")
    void supportsBothPageSizes() throws Exception {
        seedInStatus(ContractStatus.DRAFT);

        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.pageInfo.limit").value(20));
        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken).param("size", "50"))
                .andExpect(jsonPath("$.pageInfo.limit").value(50));
    }

    // ── B2a · 항목 배열의 전체 교체 ────────────────────────────────────────

    @Test
    @DisplayName("항목은 통째로 교체된다 — 「가운데 행 삭제」가 배열 하나로 표현된다")
    void replacesItemArrayWholesale() throws Exception {
        Product toner = createProduct("수분진정 토너 200ml", 21_000);
        long contractId = createDraft();

        long version = versionOf(saveOk(contractId, validForm(0L).items(
                item(serum, 28_000, "15.0", 300),
                item(cream, 22_000, "12.0", 150),
                item(toner, 18_900, "12.0", 200))));
        detail(contractId).andExpect(jsonPath("$.items[*].productName", contains(
                "수분진정 세럼 30ml", "수분진정 크림 50ml", "수분진정 토너 200ml")));

        // 2번째 행만 뺀 배열을 보낸다 — 남은 행은 순서를 유지한 채 앞으로 당겨진다.
        version = versionOf(saveOk(contractId, validForm(version).items(
                item(serum, 28_000, "15.0", 300),
                item(toner, 18_900, "12.0", 200))));
        detail(contractId)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].productName", contains(
                        "수분진정 세럼 30ml", "수분진정 토너 200ml")));

        // 빈 배열이면 전부 삭제다.
        saveOk(contractId, validForm(version).items(List.of()));
        detail(contractId).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("항목만 고친 저장도 버전을 올린다 — 탭 두 개가 번갈아 저장해도 앞 저장이 지워지지 않는다")
    void bumpsVersionEvenWhenOnlyItemsChanged() throws Exception {
        long contractId = createDraft();
        long version = versionOf(saveOk(contractId, validForm(0L)));

        // 탭 A와 탭 B가 같은 버전을 들고 있다. A가 먼저 상품 행만 바꿔 저장한다.
        long afterA = versionOf(saveOk(contractId, validForm(version)
                .items(item(cream, 20_000, "10.0", 100))));
        assertThat(afterA).isGreaterThan(version);

        // B가 들고 있던 버전으로 저장하면 A의 저장을 덮지 않고 409다.
        save(contractId, validForm(version).items(item(serum, 28_000, "15.0", 300)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_MODIFIED_ELSEWHERE"));

        detail(contractId)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("수분진정 크림 50ml"));
    }

    // ── C5 · 표준 조항 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("작성 화면의 표준 조항은 항상 현행 버전이다 — 새 버전이 시행되면 그쪽을 읽는다")
    void clausesAlwaysReadCurrentEffectiveVersion() throws Exception {
        clauses().andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value("1.0"));

        seedNewerClauseVersion();

        clauses().andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value("2.0"))
                .andExpect(jsonPath("$.clauses[0].code").value("PRICE_POLICY"))
                .andExpect(jsonPath("$.clauses[0].fullBody").value("개정된 최저가 정책 문안."));
    }

    // ------------------------------------------------------------------ 헬퍼

    private org.springframework.test.web.servlet.ResultActions summary() throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private org.springframework.test.web.servlet.ResultActions clauses() throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/clauses").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    /** 시행일이 더 늦은 EFFECTIVE 버전 — 조회는 시행일 내림차순으로 현행 한 건만 고른다. */
    private void seedNewerClauseVersion() {
        ContractClauseVersion version = ContractClauseVersion.builder()
                .versionNumber("2.0")
                .effectiveDate(LocalDate.now())
                .status(ContractClauseVersionStatus.EFFECTIVE)
                .clauses(new ArrayList<>())
                .build();
        version.getClauses().add(ContractClause.builder()
                .clauseVersion(version)
                .code("PRICE_POLICY")
                .sortOrder(1)
                .summaryTitle("가격 정책")
                .summaryDescription("공구 기간 중 타 채널 최저가 준수")
                .fullTitle("제3조 최저가 정책")
                .fullBody("개정된 최저가 정책 문안.")
                .build());
        clauseVersionRepository.save(version);
    }
}
