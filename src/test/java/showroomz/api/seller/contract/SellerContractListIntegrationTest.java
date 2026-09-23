package showroomz.api.seller.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §26-1 · 설계서 4-1·4-4 — 목록과 요약.
 *
 * <p>여기서 지키려는 것은 「서버가 소유한다」고 못박은 것들이다 —
 * 탭 ↔ 상태 묶음(1-3) · 배지 정의(4-4) · 기간 겹침 · 정렬의 NULL 위치 · 브랜드 격리.
 * 이 판정들이 FE로 새면 세 서피스가 각자 다른 표를 들게 된다.
 */
@DisplayName("[통합] 파트너센터 계약 목록·요약")
class SellerContractListIntegrationTest extends SellerContractTestSupport {

    @Test
    @DisplayName("탭은 6개지만 행의 status는 9종 그대로다 — 묶음은 필터일 뿐이다")
    void groupsNineStatusesIntoSixTabsWithoutRewritingRows() throws Exception {
        for (ContractStatus status : ContractStatus.values()) {
            seedInStatus(status);
        }

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(9));

        list("tab", "DRAFT").andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));

        list("tab", "REVIEW").andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                .andExpect(jsonPath("$.content[*].status",
                        containsInAnyOrder("REVIEW_PENDING", "REVIEW_REJECTED")));

        list("tab", "SIGNING").andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                .andExpect(jsonPath("$.content[*].status",
                        containsInAnyOrder("SIGNING", "CONCLUSION_PENDING")));

        list("tab", "CONCLUDED").andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].statusLabel").value("체결완료"));

        // 종결 3종은 한 탭으로 접히지만 배지 문구는 각자의 것을 유지한다(§26-1).
        list("tab", "CLOSED").andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                .andExpect(jsonPath("$.content[*].status",
                        containsInAnyOrder("DECLINED", "EXPIRED", "CANCELED")));
    }

    @Test
    @DisplayName("GNB 배지는 검토 반려와 B4c만 센다 — 공이 상대에게 있는 대기는 넣지 않는다")
    void countsOnlyBrandActionableContractsInBadge() throws Exception {
        LocalDateTime signedAt = LocalDateTime.now().minusDays(1).withNano(0);

        seedInStatus(ContractStatus.DRAFT);
        seedInStatus(ContractStatus.REVIEW_PENDING);
        seedInStatus(ContractStatus.REVIEW_REJECTED);
        seedInStatus(ContractStatus.SIGNING);
        // B4c — 상대만 서명했다. 이 파일에서 유일하게 「내가 조치해야 하는」 서명 진행중이다.
        seedInStatus(ContractStatus.SIGNING, contract -> contract.updateSignatures(null, signedAt, signedAt));
        // B4a — 내가 먼저 서명했다. 공은 상대에게 있으므로 배지가 아니다.
        seedInStatus(ContractStatus.SIGNING, contract -> contract.updateSignatures(signedAt, null, signedAt));
        seedInStatus(ContractStatus.CONCLUSION_PENDING);
        seedInStatus(ContractStatus.CONCLUDED);
        seedInStatus(ContractStatus.CANCELED);

        summary().andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(9))
                .andExpect(jsonPath("$.tabCounts.DRAFT").value(1))
                .andExpect(jsonPath("$.tabCounts.REVIEW").value(2))
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(4))
                .andExpect(jsonPath("$.tabCounts.CONCLUDED").value(1))
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(1))
                .andExpect(jsonPath("$.actionRequiredCount").value(2));
    }

    @Test
    @DisplayName("검색은 공구명과 상대 표시명만 본다 — 계약번호로는 걸리지 않는다")
    void searchesTitleAndCounterpartyNameOnly() throws Exception {
        Creator soyeon = createConnectedCreator("뷰티_소연", "soyeon");

        seedInStatus(ContractStatus.DRAFT, contract ->
                terms(contract, "가을 앰플 신제품 공구", baseStartAt(), baseStartAt().plusDays(9)));
        seedInStatus(ContractStatus.DRAFT, contract -> {
            terms(contract, "겨울 크림 공구", baseStartAt(), baseStartAt().plusDays(9));
            contract.changeCounterparty(soyeon, null);
        });
        Contract numbered = seedInStatus(ContractStatus.REVIEW_PENDING, contract ->
                terms(contract, "Glow Ampoule 기획전", baseStartAt(), baseStartAt().plusDays(9)));

        list("keyword", "앰플").andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].title").value("가을 앰플 신제품 공구"));

        // 공구명이 아니라 상대 표시명에 걸린다.
        list("keyword", "소연").andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].title").value("겨울 크림 공구"));

        list("keyword", "공구").andExpect(jsonPath("$.pageInfo.totalResults").value(2));

        // 대소문자는 구분하지 않는다.
        list("keyword", "glow").andExpect(jsonPath("$.pageInfo.totalResults").value(1));

        // 앞뒤 공백은 서버가 떼고 본다.
        list("keyword", "  앰플  ").andExpect(jsonPath("$.pageInfo.totalResults").value(1));

        // 계약번호는 설계서 4-1의 검색 대상이 아니다 — 늘리려면 기획을 먼저 고친다.
        list("keyword", numbered.getContractNumber()).andExpect(jsonPath("$.content").isEmpty());

        list("keyword", "없는 검색어").andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("기간 필터는 겹침으로 본다 — 기간이 비어 있는 작성중 행은 조건을 걸면 빠진다")
    void filtersByOverlappingPeriod() throws Exception {
        LocalDateTime near = LocalDateTime.now().plusDays(10).withNano(0);
        LocalDateTime far = LocalDateTime.now().plusDays(40).withNano(0);

        seedInStatus(ContractStatus.DRAFT, contract -> terms(contract, "가까운 공구", near, near.plusDays(9)));
        seedInStatus(ContractStatus.DRAFT, contract -> terms(contract, "먼 공구", far, far.plusDays(9)));
        seedInStatus(ContractStatus.DRAFT, contract -> terms(contract, "기간 미정 공구", null, null));

        list().andExpect(jsonPath("$.pageInfo.totalResults").value(3));

        // 조회 시작일 이후에 끝나는 계약만 — 가까운 공구는 이미 끝났고, 기간 없는 행은 비교 자체가 안 된다.
        list("startDate", LocalDate.now().plusDays(35).toString())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].title").value("먼 공구"));

        list("endDate", LocalDate.now().plusDays(20).toString())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].title").value("가까운 공구"));

        // 두 계약 사이의 빈 구간 — 겹치는 계약이 없다. 「시작일이 구간 안」이 아니라 「기간이 겹침」이다.
        mockMvc.perform(get(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .param("startDate", LocalDate.now().plusDays(21).toString())
                        .param("endDate", LocalDate.now().plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("기본은 최근 작성순이고, 공구 시작일순에서는 기간 없는 행이 맨 뒤로 간다")
    void sortsByCreatedDescByDefaultAndPushesUndatedRowsLast() throws Exception {
        LocalDateTime far = LocalDateTime.now().plusDays(40).withNano(0);
        LocalDateTime near = LocalDateTime.now().plusDays(10).withNano(0);

        seedInStatus(ContractStatus.DRAFT, contract -> terms(contract, "먼저 만든 먼 공구", far, far.plusDays(9)));
        seedInStatus(ContractStatus.DRAFT, contract -> terms(contract, "나중에 만든 가까운 공구", near, near.plusDays(9)));
        seedInStatus(ContractStatus.DRAFT, contract -> terms(contract, "마지막에 만든 기간 미정 공구", null, null));

        list().andExpect(jsonPath("$.content[*].title", contains(
                "마지막에 만든 기간 미정 공구", "나중에 만든 가까운 공구", "먼저 만든 먼 공구")));

        // NULL을 앞에 두면 목록 첫 화면이 공구명도 기간도 없는 초안으로 채워진다.
        list("sort", "START_AT_ASC").andExpect(jsonPath("$.content[*].title", contains(
                "나중에 만든 가까운 공구", "먼저 만든 먼 공구", "마지막에 만든 기간 미정 공구")));
    }

    @Test
    @DisplayName("페이지를 잘라도 전체 건수는 그대로 내려간다")
    void paginatesWithoutLosingTotal() throws Exception {
        for (int i = 0; i < 3; i++) {
            seedInStatus(ContractStatus.DRAFT);
        }

        mockMvc.perform(get(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.currentPage").value(1))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(true));

        mockMvc.perform(get(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false));
    }

    @Test
    @DisplayName("목록과 요약은 내 브랜드 계약만 센다")
    void isolatesOtherBrandsContracts() throws Exception {
        seedInStatus(ContractStatus.DRAFT);

        BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "아더랩");
        transactionTemplate.executeWithoutResult(tx ->
                contractRepository.save(Contract.createDraft(other.market(), null, null)));

        list().andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        summary().andExpect(jsonPath("$.tabCounts.ALL").value(1));

        // 반대편에서도 같아야 한다 — 격리는 한 방향 확인으로 끝나지 않는다.
        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, sellerToken(other.seller())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].counterpartyName").doesNotExist());
    }

    @Test
    @DisplayName("행은 상태 라벨·색·진입 모드와 항목 수를 서버 판정으로 싣는다")
    void rowCarriesServerSideLabelsCountsAndEntryMode() throws Exception {
        seedInStatus(ContractStatus.REVIEW_REJECTED, contract -> contract.replaceItems(
                new ArrayList<>(List.of(seedItem(serum, 28_000), seedItem(cream, 20_000)))));
        seedInStatus(ContractStatus.SIGNING);
        // 아직 아무것도 채우지 않은 초안 — 서버는 (공구명 미입력)을 지어내지 않는다.
        seedInStatus(ContractStatus.DRAFT, contract -> {
            terms(contract, null, null, null);
            contract.replaceItems(List.of());
        });

        list("tab", "REVIEW")
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].statusLabel").value("검토 반려"))
                .andExpect(jsonPath("$.content[0].statusTone").value("WARNING"))
                // 검토 반려는 잠금 구간의 유일한 예외라 다시 작성 모드로 들어간다.
                .andExpect(jsonPath("$.content[0].entryMode").value("EDIT"))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("글로우_지민"))
                .andExpect(jsonPath("$.content[0].contractNumber").exists());

        list("tab", "SIGNING")
                .andExpect(jsonPath("$.content[0].statusLabel").value("서명 진행중"))
                .andExpect(jsonPath("$.content[0].statusTone").value("INFO"))
                .andExpect(jsonPath("$.content[0].entryMode").value("VIEW"));

        list("tab", "DRAFT")
                .andExpect(jsonPath("$.content[0].itemCount").value(0))
                .andExpect(jsonPath("$.content[0].title").doesNotExist())
                .andExpect(jsonPath("$.content[0].startAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].contractNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    // ------------------------------------------------------------------ 요청 헬퍼

    private ResultActions list(String... params) throws Exception {
        var request = get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private ResultActions summary() throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, brandToken));
    }
}
