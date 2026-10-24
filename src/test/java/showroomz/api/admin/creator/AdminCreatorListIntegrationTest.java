package showroomz.api.admin.creator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 인플루언서 회원 목록(ui-admin-16 rev.7 · 55 계획서 M1) — 브랜드 목록(LS)과 대칭. 테스트 지원이 기본 인플루언서 1명을 깐다.
 */
@DisplayName("어드민 인플루언서 목록 — 탭 5종 · 상단 고정 · 검색 · 연결 브랜드 · 요약 · 페이지(55 M1)")
class AdminCreatorListIntegrationTest extends AdminCreatorTestSupport {

    private static final String LIST = "/v1/admin/creators";

    @Test
    @DisplayName("탭 5종은 겹치지 않는다 · 정지 중 신청도 대기 · 전체 탭은 대기 상단 고정 · 대기 탭만 최장 대기 일수 · 표시 상태 톤")
    void tabsDoNotOverlap() throws Exception {
        Creator suspended = createCreator("정지쇼룸", "ls-susp");
        Creator gone = createCreator("탈퇴쇼룸", "ls-gone");
        Creator waitingActive = createCreator("대기쇼룸", "ls-wait");
        Creator waitingSuspended = createCreator("대기정지쇼룸", "ls-wait-susp");
        jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id IN (?, ?)", suspended.getId(),
                waitingSuspended.getId());
        jdbc.update("UPDATE creator SET status = 'WITHDRAWN' WHERE creator_id = ?", gone.getId());
        pending(waitingActive, LocalDateTime.now().minusDays(4));
        pending(waitingSuspended, LocalDateTime.now().minusDays(1));

        JsonNode all = json(adminGet(LIST).andExpect(status().isOk()));
        assertThat(all.at("/summary/total").asLong()).isEqualTo(5);
        assertThat(all.at("/summary/active").asLong()).isEqualTo(1);
        assertThat(all.at("/summary/suspended").asLong()).isEqualTo(1);
        assertThat(all.at("/summary/withdrawalPending").asLong()).isEqualTo(2);
        assertThat(all.at("/summary/withdrawn").asLong()).isEqualTo(1);
        assertThat(all.at("/summary/longestPendingDays").isNull()).isTrue();
        List<String> statuses = texts(all.path("content"), "listStatus");
        assertThat(statuses.subList(0, 2)).containsOnly("WITHDRAWAL_PENDING");
        assertThat(statuses.subList(2, 5)).doesNotContain("WITHDRAWAL_PENDING");

        JsonNode waiting = json(adminGet(LIST + "?tab=WITHDRAWAL_PENDING"));
        assertThat(texts(waiting.path("content"), "showroomName")).containsExactlyInAnyOrder("대기쇼룸", "대기정지쇼룸");
        assertThat(waiting.at("/summary/longestPendingDays").asLong()).isEqualTo(4);
        assertThat(texts(waiting.path("content"), "listStatusTone")).containsOnly("WARNING");
        assertThat(texts(json(adminGet(LIST + "?tab=SUSPENDED")).path("content"), "showroomName")).containsExactly("정지쇼룸");
        assertThat(texts(json(adminGet(LIST + "?tab=WITHDRAWN")).path("content"), "listStatusTone")).containsExactly("NEUTRAL");
        JsonNode active = json(adminGet(LIST + "?tab=ACTIVE"));
        assertThat(active.path("content")).hasSize(1);
        assertThat(active.at("/content/0/creatorId").asLong()).isEqualTo(creator.getId());
        assertThat(active.at("/content/0/listStatusTone").asText()).isEqualTo("SUCCESS");

        JsonNode suspendedWaiting = rowOf(waiting, waitingSuspended);
        assertThat(suspendedWaiting.path("status").asText()).isEqualTo(CreatorStatus.SUSPENDED.name());
        assertThat(suspendedWaiting.path("withdrawalPending").asBoolean()).isTrue();
        assertThat(suspendedWaiting.path("withdrawalRequestedAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("검색 — INF- 회원번호(대소문자 · 공백 무시 · 숫자 아니면 0건) · 쇼룸명 · 업무용 이메일(대소문자 무시) · 요약은 검색 범위")
    void search() throws Exception {
        Creator harin = createCreator("하린의 뷰티랩", "harin");
        createCreator("민지네", "minji");

        adminGet(LIST + "?keyword= inf-" + harin.getId() + " ").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].memberNo").value("INF-" + harin.getId()));
        adminGet(LIST + "?keyword=INF-abc").andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.summary.total").value(0));
        adminGet(LIST + "?keyword=뷰티랩").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].showroomName").value("하린의 뷰티랩"))
                .andExpect(jsonPath("$.summary.total").value(1));
        adminGet(LIST + "?keyword=MINJI-BIZ").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].businessEmail").value("minji-biz@showroomz.test"));
    }

    @Test
    @DisplayName("행 — 연결 브랜드는 연결중 쌍만 · 사업자 여부 라벨 · 인스타 바로가기 · 탈퇴 행은 인스타 null · 업무용 이메일 보존")
    void rowFields() throws Exception {
        Creator rich = createCreator("연결쇼룸", "ls-conn");
        jdbc.update("UPDATE creator SET instagram_url = ?, business_type = 'BUSINESS' WHERE creator_id = ?",
                "https://instagram.com/ls-conn", rich.getId());
        BrandFixture.Brand first = fixture.createBrand("ls-a@showroomz.test", "연결A");
        BrandFixture.Brand second = fixture.createBrand("ls-b@showroomz.test", "연결B");
        connect(first, rich);
        Long cut = connect(second, rich).getId();
        jdbc.update("UPDATE connection SET status = 'DISCONNECTED' WHERE connection_id = ?", cut);

        JsonNode row = json(adminGet(LIST + "?keyword=연결쇼룸")).at("/content/0");
        assertThat(row.path("connectedBrandCount").asLong()).isEqualTo(1);
        assertThat(row.path("businessTypeLabel").asText()).isEqualTo("사업자");
        assertThat(row.path("instagramUrl").asText()).isEqualTo("https://instagram.com/ls-conn");
        assertThat(row.path("joinedAt").isNull()).isFalse();

        jdbc.update("UPDATE creator SET status = 'WITHDRAWN', withdrawn_at = ? WHERE creator_id = ?", LocalDateTime.now(),
                rich.getId());
        JsonNode withdrawn = json(adminGet(LIST + "?keyword=연결쇼룸")).at("/content/0");
        assertThat(withdrawn.path("instagramUrl").isNull()).isTrue();
        assertThat(withdrawn.path("businessEmail").asText()).isEqualTo("ls-conn-biz@showroomz.test");
        assertThat(withdrawn.path("listStatus").asText()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("정렬 · 페이지 — 기본 가입 최신순 · MEMBER_NO 회원번호순 · 2페이지 · 0 이하 페이지는 1페이지")
    void sortAndPaging() throws Exception {
        Creator second = createCreator("둘째쇼룸", "ls-2");
        Creator third = createCreator("셋째쇼룸", "ls-3");
        jdbc.update("UPDATE creator SET created_at = ? WHERE creator_id = ?", LocalDateTime.now().minusDays(30), third.getId());

        List<String> byNo = texts(json(adminGet(LIST + "?sort=MEMBER_NO")).path("content"), "creatorId");
        assertThat(byNo).containsExactly(String.valueOf(creator.getId()), String.valueOf(second.getId()),
                String.valueOf(third.getId()));
        List<String> recent = texts(json(adminGet(LIST)).path("content"), "creatorId");
        assertThat(recent.get(recent.size() - 1)).isEqualTo(String.valueOf(third.getId()));

        for (int i = 0; i < 20; i++) {
            createCreator("페이지쇼룸" + i, "ls-page-" + i);
        }
        adminGet(LIST + "?page=2&size=20").andExpect(jsonPath("$.content.length()").value(3));
        assertThat(texts(json(adminGet(LIST + "?page=0&size=20")).path("content"), "creatorId"))
                .isEqualTo(texts(json(adminGet(LIST + "?page=1&size=20")).path("content"), "creatorId"));
    }

    // ------------------------------------------------------------------ helpers

    private void pending(Creator target, LocalDateTime requestedAt) {
        inTransaction(() -> withdrawalRequests.save(CreatorWithdrawalRequest.request(target.getId(), "본업 집중", requestedAt,
                LocalDate.now().plusDays(5))));
    }

    private static JsonNode rowOf(JsonNode page, Creator target) {
        for (JsonNode row : page.path("content")) {
            if (row.path("creatorId").asLong() == target.getId()) {
                return row;
            }
        }
        throw new AssertionError("행 없음: " + target.getId());
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.path(field).asText());
        }
        return values;
    }
}
