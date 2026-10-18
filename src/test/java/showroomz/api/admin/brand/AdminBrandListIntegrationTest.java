package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.market.type.MarketStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 2절 — 브랜드 회원 목록(목록 시안 14 미수령 · 설계서 9-1 최소 구성). 시드 브랜드(글로우랩) 하나가 늘 깔려 있다.
 */
@DisplayName("어드민 브랜드 목록 — 탭 · 검색 · 정렬 · 요약 · 페이지(52 시나리오 LS)")
class AdminBrandListIntegrationTest extends AdminBrandTestSupport {

    @Test
    @DisplayName("LS-01 · LS-02 기본값 · 탭 — 대상 밖(심사 대기 셀러 · 운영자) 제외 · 휴면은 활성 탭에 활성으로 · 요약은 탭 건수와 같다")
    void defaultsAndTabs() throws Exception {
        BrandFixture.Brand suspended = cleanBrand("정지브랜드");
        BrandFixture.Brand withdrawn = cleanBrand("탈퇴브랜드");
        BrandFixture.Brand dormant = cleanBrand("휴면브랜드");
        BrandFixture.Brand pendingSeller = cleanBrand("심사브랜드");
        setMarketStatus(suspended.marketId(), MarketStatus.SUSPENDED);
        setMarketStatus(withdrawn.marketId(), MarketStatus.WITHDRAWN);
        setMarketStatus(dormant.marketId(), MarketStatus.DORMANT);
        jdbc.update("UPDATE seller SET status = 'PENDING' WHERE seller_id = ?", pendingSeller.seller().getId());

        JsonNode all = json(adminGet(BRANDS).andExpect(status().isOk()));
        assertThat(all.path("content")).hasSize(4);
        assertThat(textsAt(all.path("content"), "brandName")).doesNotContain("심사브랜드");
        assertThat(all.path("summary").path("total").asLong()).isEqualTo(4);
        assertThat(all.path("summary").path("active").asLong()).isEqualTo(2);
        assertThat(all.path("summary").path("suspended").asLong()).isEqualTo(1);
        assertThat(all.path("summary").path("withdrawn").asLong()).isEqualTo(1);
        assertThat(all.path("pageInfo").isMissingNode()).isFalse();
        JsonNode first = all.path("content").get(0);
        for (String field : List.of("marketId", "brandNo", "brandName", "managerName", "businessType", "memberStatus",
                "memberStatusLabel", "withdrawalPending", "joinedAt", "lastLoginAt")) {
            assertThat(first.has(field)).as(field).isTrue();
        }

        JsonNode active = json(adminGet(BRANDS + "?tab=ACTIVE").andExpect(status().isOk()));
        assertThat(textsAt(active.path("content"), "brandName")).containsExactlyInAnyOrder("글로우랩", "휴면브랜드");
        assertThat(textsAt(active.path("content"), "memberStatus")).containsOnly("ACTIVE");
        assertThat(textsAt(active.path("content"), "memberStatusLabel")).containsOnly("활성");
        adminGet(BRANDS + "?tab=SUSPENDED").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].brandName").value("정지브랜드"));
        adminGet(BRANDS + "?tab=WITHDRAWN").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].brandName").value("탈퇴브랜드"));
    }

    @Test
    @DisplayName("LS-03 회원번호 검색 — 대소문자 · 공백 무시 · 접두사 뒤가 숫자가 아니면 전체가 아니라 0건 · 0건이면 요약도 0")
    void memberNumberSearch() throws Exception {
        cleanBrand("다른브랜드");
        String no = "BRD-" + brand.marketId();
        for (String keyword : List.of(no, no.toLowerCase(), "  " + no + "  ")) {
            adminList("keyword", keyword).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].brandNo").value(no));
        }
        for (String keyword : List.of("BRD-abc", "BRD-", "BRD-99999999")) {
            adminList("keyword", keyword).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.summary.total").value(0))
                    .andExpect(jsonPath("$.summary.active").value(0));
        }
    }

    @Test
    @DisplayName("LS-04 이름 검색 — 부분 일치 · 앞뒤 공백 제거 · 요약은 검색 범위로 센다")
    void nameSearch() throws Exception {
        cleanBrand("글로우마켓");
        cleanBrand("베이직랩");
        adminList("keyword", "글로우").andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.summary.total").value(2));
        adminList("keyword", "랩").andExpect(jsonPath("$.content.length()").value(2));
        adminList("keyword", "  글로우  ").andExpect(jsonPath("$.content.length()").value(2));
        adminList("keyword", "없는이름").andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.summary.total").value(0));
    }

    @Test
    @DisplayName("LS-05 정렬 — 기본은 가입 최신순(동률은 id 역순) · BRAND_NO 는 id 오름차순")
    void sorting() throws Exception {
        BrandFixture.Brand older = cleanBrand("가입이른");
        BrandFixture.Brand tieA = cleanBrand("동률A");
        BrandFixture.Brand tieB = cleanBrand("동률B");
        LocalDateTime base = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE seller SET created_at = ? WHERE seller_id = ?", base.minusDays(30), older.seller().getId());
        jdbc.update("UPDATE seller SET created_at = ? WHERE seller_id = ?", base.minusDays(1), tieA.seller().getId());
        jdbc.update("UPDATE seller SET created_at = ? WHERE seller_id = ?", base.minusDays(1), tieB.seller().getId());
        jdbc.update("UPDATE seller SET created_at = ? WHERE seller_id = ?", base.minusDays(10), brand.seller().getId());

        assertThat(textsAt(json(adminGet(BRANDS)).path("content"), "brandName"))
                .containsExactly("동률B", "동률A", "글로우랩", "가입이른");
        assertThat(textsAt(json(adminGet(BRANDS + "?sort=BRAND_NO")).path("content"), "brandName"))
                .containsExactly("글로우랩", "가입이른", "동률A", "동률B");
    }

    @Test
    @DisplayName("LS-06 · LS-07 탈퇴 행은 담당자 · 사업자 구분이 null · 탈퇴 대기는 상태가 아니라 플래그(활성 · 정지 모두)")
    void withdrawnRowAndPendingFlag() throws Exception {
        BrandFixture.Brand gone = cleanBrand("떠난브랜드");
        Long requestId = withdrawalService.request(gone.marketId(), null, null, LocalDateTime.now()).getId();
        approve(gone.marketId(), requestId).andExpect(status().isOk());

        BrandFixture.Brand waitingActive = cleanBrand("대기활성");
        BrandFixture.Brand waitingSuspended = cleanBrand("대기정지");
        withdrawalService.request(waitingActive.marketId(), null, null, LocalDateTime.now());
        withdrawalService.request(waitingSuspended.marketId(), null, null, LocalDateTime.now());
        suspend(waitingSuspended.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());

        adminGet(BRANDS + "?tab=WITHDRAWN").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].brandName").value("떠난브랜드"))
                .andExpect(jsonPath("$.content[0].brandNo").value("BRD-" + gone.marketId()))
                .andExpect(jsonPath("$.content[0].managerName").doesNotExist())
                .andExpect(jsonPath("$.content[0].businessType").doesNotExist())
                .andExpect(jsonPath("$.content[0].joinedAt").exists())
                .andExpect(jsonPath("$.content[0].withdrawalPending").value(false));

        adminList("keyword", "대기활성").andExpect(jsonPath("$.content[0].withdrawalPending").value(true))
                .andExpect(jsonPath("$.content[0].memberStatus").value("ACTIVE"));
        adminList("keyword", "대기정지").andExpect(jsonPath("$.content[0].withdrawalPending").value(true))
                .andExpect(jsonPath("$.content[0].memberStatus").value("SUSPENDED"));
    }

    @Test
    @DisplayName("LS-08 · LS-10 요약의 탈퇴 대기 수는 검색 범위가 아니라 전체 · 설계서의 withdrawalPending 필터는 없어 무시된다(현재 동작 고정)")
    void pendingSummaryIsGlobal() throws Exception {
        BrandFixture.Brand a = cleanBrand("대기하나");
        BrandFixture.Brand b = cleanBrand("대기둘");
        withdrawalService.request(a.marketId(), null, null, LocalDateTime.now());
        withdrawalService.request(b.marketId(), null, null, LocalDateTime.now());

        adminList("keyword", "대기하나").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.withdrawalPending").value(2));
        adminGet(BRANDS + "?withdrawalPending=true").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    @DisplayName("LS-09 페이지 — 2페이지 · 0 이하 페이지는 1페이지로 보정")
    void paging() throws Exception {
        for (int i = 1; i <= 24; i++) {
            cleanBrand("페이지" + i);
        }
        adminGet(BRANDS + "?page=2&size=20").andExpect(jsonPath("$.content.length()").value(5));
        String firstPage = textsAt(json(adminGet(BRANDS + "?page=1&size=20")).path("content"), "marketId").toString();
        assertThat(textsAt(json(adminGet(BRANDS + "?page=0&size=20")).path("content"), "marketId").toString())
                .isEqualTo(firstPage);
        assertThat(textsAt(json(adminGet(BRANDS + "?page=-1&size=20")).path("content"), "marketId").toString())
                .isEqualTo(firstPage);
    }
}
