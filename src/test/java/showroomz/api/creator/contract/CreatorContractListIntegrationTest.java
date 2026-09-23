package showroomz.api.creator.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.contract.type.ContractDeclineReason;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1 목록의 조작 — 탭 · 검색 · 정렬 3종 · 페이지 · 상세의 [‹ 이전] [다음 ›] · 기한 경고색 경계.
 *
 * <p>화면 대조({@link CreatorContractScreenSpecIntegrationTest})가 「시안의 8행이 그대로 서는가」를 본다면,
 * 여기는 인플루언서가 목록을 <b>움직였을 때</b> 서버가 같은 판정을 유지하는지를 본다.
 */
@DisplayName("[통합] 쇼룸 스튜디오 계약 목록")
class CreatorContractListIntegrationTest extends CreatorContractTestSupport {

    // ── 탭 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("탭은 5종이고 각 탭은 자기 상태만 담는다 — 종료 탭은 거절·만료·취소 셋이다")
    void eachTabHoldsOnlyItsStatuses() throws Exception {
        Long signing = signingContract();
        Long pending = conclusionPendingContract();
        Long concluded = concludedContract();
        Long declined = declinedContract();
        Long expired = expiredContract();
        Long canceled = canceledContract();

        list("tab", "SIGNING").andExpect(jsonPath("$.content[*].contractId").value(contains(signing.intValue())));
        list("tab", "CONCLUSION_PENDING").andExpect(jsonPath("$.content[*].contractId").value(contains(pending.intValue())));
        list("tab", "CONCLUDED").andExpect(jsonPath("$.content[*].contractId").value(contains(concluded.intValue())));
        list("tab", "CLOSED").andExpect(jsonPath("$.content[*].contractId").value(containsInAnyOrder(
                declined.intValue(), expired.intValue(), canceled.intValue())));
        list("tab", "ALL").andExpect(jsonPath("$.pageInfo.totalResults").value(6));
    }

    @Test
    @DisplayName("작성중 탭은 코드에 없다 — tab=DRAFT는 잘못된 요청(400)이지 서버 오류가 아니다")
    void rejectsTabsThatDoNotExistOnStudio() throws Exception {
        saveContract(ContractStatus.DRAFT, contract -> { });

        list("tab", "DRAFT").andExpect(status().isBadRequest());
        list("tab", "REVIEW_PENDING").andExpect(status().isBadRequest());
        // 파트너의 「생성일순」도 스튜디오에는 없다 — 생성일은 브랜드의 사정이다.
        list("sort", "CREATED_DESC").andExpect(status().isBadRequest());
    }

    // ── 검색 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("검색은 공구명과 브랜드명을 본다 — 앞뒤 공백·대소문자를 가리지 않는다")
    void searchesByTitleAndBrandName() throws Exception {
        Market cosmetic = otherBrand("△△ 코스메틱");
        Long ampoule = saveContractFor(cosmetic, me, null, ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "가을 앰플 신제품 공구");
            approve(contract);
        });
        Long glow = saveContract(ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "Glow Cream 앵콜 공구");
            approve(contract);
        });
        // 도착 전 계약은 검색어가 맞아도 나오지 않는다.
        saveContract(ContractStatus.REVIEW_PENDING, contract -> {
            applyScreenTerms(contract, "가을 앰플 미발송 공구");
            requestReview(contract);
        });

        list("keyword", "앰플").andExpect(jsonPath("$.content[*].contractId").value(contains(ampoule.intValue())));
        list("keyword", "코스메틱").andExpect(jsonPath("$.content[*].contractId").value(contains(ampoule.intValue())));
        list("keyword", "  glow cream ").andExpect(jsonPath("$.content[*].contractId").value(contains(glow.intValue())));
        list("keyword", "퓨어랩").andExpect(jsonPath("$.content[*].contractId").value(contains(glow.intValue())));
        list("keyword", "없는 검색어").andExpect(jsonPath("$.content").isEmpty());
        // 탭과 검색은 함께 걸린다.
        list("tab", "CLOSED", "keyword", "앰플").andExpect(jsonPath("$.content").isEmpty());
    }

    // ── 정렬 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("받은 순은 서명 요청이 도착한 시각 기준이다 — 브랜드가 먼저 작성했어도 늦게 보냈으면 위다")
    void receivedOrderIgnoresCreationTime() throws Exception {
        // 먼저 작성됐지만 늦게 도착 → 위
        Long writtenFirstSentLast = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(8)));
        // 나중에 작성됐지만 먼저 도착 → 아래
        Long writtenLastSentFirst = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().minusDays(3), LocalDateTime.now().plusDays(8)));

        list().andExpect(jsonPath("$.content[*].contractId").value(contains(
                writtenFirstSentLast.intValue(), writtenLastSentFirst.intValue())));
        list("sort", "RECEIVED_DESC").andExpect(jsonPath("$.content[*].contractId").value(contains(
                writtenFirstSentLast.intValue(), writtenLastSentFirst.intValue())));
    }

    @Test
    @DisplayName("서명 기한순은 기한이 빠른 것부터 — 조치해야 할 건이 위로 온다")
    void deadlineOrderPutsTheMostUrgentFirst() throws Exception {
        Long late = saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(9)));
        Long urgent = saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(1)));
        Long middle = saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(4)));

        list("sort", "DEADLINE_ASC").andExpect(jsonPath("$.content[*].contractId").value(contains(
                urgent.intValue(), middle.intValue(), late.intValue())));
    }

    @Test
    @DisplayName("공구 시작일순은 시작이 빠른 것부터다")
    void startDateOrder() throws Exception {
        Long later = saveContract(ContractStatus.SIGNING, contract -> {
            startAt(contract, 20);
            approve(contract);
        });
        Long sooner = saveContract(ContractStatus.SIGNING, contract -> {
            startAt(contract, 8);
            approve(contract);
        });

        list("sort", "START_AT_ASC").andExpect(jsonPath("$.content[*].contractId").value(contains(
                sooner.intValue(), later.intValue())));
    }

    // ── 페이지 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("페이지는 1부터이고 페이지 정보가 함께 내려간다")
    void pagesThroughReceivedContracts() throws Exception {
        for (int hoursAgo = 1; hoursAgo <= 5; hoursAgo++) {
            int received = hoursAgo;
            saveContract(ContractStatus.SIGNING, contract ->
                    approve(contract, LocalDateTime.now().minusHours(received), LocalDateTime.now().plusDays(8)));
        }

        list("page", "1", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.currentPage").value(1))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(5))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
        list("page", "3", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false));
    }

    // ── 이전 / 다음 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[‹ 이전] [다음 ›]은 지금 보는 탭·정렬 안의 이웃이다 — 탭 밖의 계약으로 건너가지 않는다")
    void neighborsStayInsideTheCurrentTabAndSort() throws Exception {
        Long first = saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(1)));
        Long second = saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(3)));
        Long third = saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(5)));
        // 서명 기한으로는 first와 second 사이지만 서명 진행중 탭 밖이다.
        Long closedBetween = declined(
                saveContract(ContractStatus.SIGNING, contract -> approve(contract, LocalDateTime.now().plusDays(2))),
                ContractDeclineReason.ETC, null);

        detail(second, "tab", "SIGNING", "sort", "DEADLINE_ASC")
                .andExpect(jsonPath("$.navigation.prevContractId").value(first))
                .andExpect(jsonPath("$.navigation.nextContractId").value(third));
        detail(first, "tab", "SIGNING", "sort", "DEADLINE_ASC")
                .andExpect(jsonPath("$.navigation.prevContractId").doesNotExist())
                .andExpect(jsonPath("$.navigation.nextContractId").value(second));

        // 전체 탭이면 종결 건도 이웃이다.
        detail(first, "tab", "ALL", "sort", "DEADLINE_ASC")
                .andExpect(jsonPath("$.navigation.nextContractId").value(closedBetween));
    }

    @Test
    @DisplayName("검색 중이면 이웃도 검색 결과 안에서 찾는다")
    void neighborsRespectKeyword() throws Exception {
        Long ampouleOld = saveContract(ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "앰플 공구 A");
            approve(contract, LocalDateTime.now().minusHours(3), LocalDateTime.now().plusDays(8));
        });
        saveContract(ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "크림 공구");
            approve(contract, LocalDateTime.now().minusHours(2), LocalDateTime.now().plusDays(8));
        });
        Long ampouleNew = saveContract(ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "앰플 공구 B");
            approve(contract, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(8));
        });

        detail(ampouleNew, "keyword", "앰플")
                .andExpect(jsonPath("$.navigation.prevContractId").doesNotExist())
                .andExpect(jsonPath("$.navigation.nextContractId").value(ampouleOld));
    }

    @Test
    @DisplayName("같은 시각에 도착한 두 계약도 서로의 이웃이다 — 동률에서 건너뛰지 않는다")
    void neighborsDoNotSkipTies() throws Exception {
        LocalDateTime sameMoment = LocalDateTime.now().minusHours(2).withNano(0);
        Long a = saveContract(ContractStatus.SIGNING, contract -> approve(contract, sameMoment, LocalDateTime.now().plusDays(8)));
        Long b = saveContract(ContractStatus.SIGNING, contract -> approve(contract, sameMoment, LocalDateTime.now().plusDays(8)));

        // 동률이면 id 내림차순 — b가 위, a가 아래
        list().andExpect(jsonPath("$.content[*].contractId").value(contains(b.intValue(), a.intValue())));
        detail(b, "sort", "RECEIVED_DESC").andExpect(jsonPath("$.navigation.nextContractId").value(a));
        detail(a, "sort", "RECEIVED_DESC").andExpect(jsonPath("$.navigation.prevContractId").value(b));
    }

    // ── 「내 서명 기한」 경고색 ─────────────────────────────────────────────

    @Test
    @DisplayName("경고색 경계는 D-3이다 — 3일 안이면 경고, 그 밖이면 중립")
    void imminentThresholdIsThreeDays() throws Exception {
        Long inside = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().plusDays(3).minusMinutes(5)));
        Long outside = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().plusDays(3).plusHours(1)));

        list().andExpect(jsonPath("$.content[?(@.contractId == %d)].deadline.tone".formatted(inside))
                        .value(contains("WARNING")))
                .andExpect(jsonPath("$.content[?(@.contractId == %d)].deadline.tone".formatted(outside))
                        .value(contains("NEUTRAL")));
    }

    @Test
    @DisplayName("기한이 지나도 운영자가 만료 처리하기 전에는 서명 진행중이다 — 만료는 자동 판정이 아니다(MVP)")
    void overdueContractStaysSigningUntilOperatorExpiresIt() throws Exception {
        LocalDateTime passedDeadline = LocalDateTime.now().minusHours(6).withNano(0);
        Long overdue = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().minusDays(8), passedDeadline));

        list()
                .andExpect(jsonPath("$.content[0].contractId").value(overdue))
                .andExpect(jsonPath("$.content[0].status").value("SIGNING"))
                // 「기한 경과」는 만료 처리된 뒤의 표기다. 그 전까지는 날짜 그대로 · 경고색(위험색 아님).
                .andExpect(jsonPath("$.content[0].deadline.type").value("DEADLINE"))
                .andExpect(jsonPath("$.content[0].deadline.deadlineAt").value(iso(passedDeadline)))
                .andExpect(jsonPath("$.content[0].deadline.tone").value("WARNING"));
        summary().andExpect(jsonPath("$.tabCounts.SIGNING").value(1));
    }

    // ── 경계 · 권한 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("다른 인플루언서의 계약은 목록·카운트·배지 어디에도 섞이지 않는다")
    void doesNotMixOtherCreatorsContracts() throws Exception {
        Creator other = createOtherCreator();
        saveContractFor(other, ContractStatus.SIGNING, this::approve);
        saveContractFor(other, ContractStatus.CONCLUDED, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
            contract.conclude(LocalDateTime.now());
        });
        Long mine = signingContract();

        list().andExpect(jsonPath("$.content[*].contractId").value(contains(mine.intValue())));
        summary()
                .andExpect(jsonPath("$.tabCounts.ALL").value(1))
                .andExpect(jsonPath("$.tabCounts.CONCLUDED").value(0))
                .andExpect(jsonPath("$.actionRequiredCount").value(1));
    }

    @Test
    @DisplayName("스튜디오 계약 API는 인플루언서 전용이다 — 브랜드 토큰·비로그인은 들어오지 못한다")
    void requiresCreatorRole() throws Exception {
        signingContract();

        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, sellerToken(brand.seller())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CONTRACTS + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("someone@showroomz.test", RoleType.USER, 999L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CONTRACTS)).andExpect(status().isUnauthorized());
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private void startAt(showroomz.domain.contract.entity.Contract contract, int daysFromNow) {
        LocalDateTime start = LocalDateTime.now().plusDays(daysFromNow).withHour(10).withMinute(0);
        contract.updateTerms(contract.getTitle(), start, start.plusDays(7),
                contract.getFixedFeeAmount(), contract.getFixedFeeTrigger(), contract.getFixedFeeNoticeAgreedAt(),
                contract.getContentFeedCount(), contract.getContentReelsCount(), contract.getContentStoryCount(),
                start.plusDays(7).toLocalDate(), contract.getSecondaryUseAllowed(), contract.getSecondaryUsePeriodType(),
                contract.getSecondaryUseMonths(), contract.getBrandPreReview(), contract.getNote());
    }
}
