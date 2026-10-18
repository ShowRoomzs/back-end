package showroomz.api.admin.post;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostReportReason;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [통합] 어드민 일반 게시물 — 권한 · 목록 · 요약 · 코드 목록 (54 테스트 시나리오 2 · 3 · 4 · 10 · 20-1절).
 *
 * <p>목록은 네이티브 SQL(파생 상태 · 순위별 2차 정렬)이라 단위 테스트로 잡히지 않는다. 순위 <b>안</b>의 순서와 경계를 여기서 본다.
 */
@DisplayName("[통합] 어드민 일반 게시물 — 권한 · 목록 · 요약 시나리오")
class AdminPostListScenarioIntegrationTest extends AdminPostScenarioSupport {

    private static final DateTimeFormatter POST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ------------------------------------------------------------------ 2. 접근 · 권한

    @Nested
    @DisplayName("접근 · 권한")
    class Access {

        /** 15개 엔드포인트 — 경로 변수는 존재 여부와 무관하다(인가가 먼저다) */
        private List<MockHttpServletRequestBuilder> endpoints() {
            return List.of(
                    get(ADMIN_POSTS), get(ADMIN_POSTS + "/summary"), get(ADMIN_POSTS + "/1"),
                    get(ADMIN_POSTS + "/suspension-reasons"), get(ADMIN_POSTS + "/policy-clauses"),
                    post(ADMIN_POSTS + "/1/suspend"), post(ADMIN_POSTS + "/1/reports/close"),
                    post(ADMIN_POSTS + "/1/suspension/cancel"),
                    put(ADMIN_POSTS + "/1/images/1/report-flag"), delete(ADMIN_POSTS + "/1/images/1/report-flag"),
                    get("/v1/admin/post-reports"), post("/v1/admin/post-reports/1/dismiss"),
                    get("/v1/admin/post-appeals"), post("/v1/admin/post-appeals/1/approve"),
                    post("/v1/admin/post-appeals/1/reject"));
        }

        @Test
        @DisplayName("[AU-01] 토큰 없이는 15개 엔드포인트 모두 401이다")
        void anonymousIsUnauthorized() throws Exception {
            for (MockHttpServletRequestBuilder request : endpoints()) {
                mockMvc.perform(request).andExpect(status().isUnauthorized());
            }
        }

        @Test
        @DisplayName("[AU-02] 소비자 · 브랜드 · 인플루언서 토큰은 403이고 조치가 일어나지 않는다")
        void nonAdminIsForbidden() throws Exception {
            Long postId = publish(soyeon, "권한 확인", LocalDateTime.now().minusDays(1));
            Users user = consumer("au-user", "한지준");
            List<String> tokens = List.of(userToken(user), sellerToken(fixture.createBrand("brand@showroomz.test", "glowlab").seller()),
                    creatorToken(harin));

            for (String token : tokens) {
                for (MockHttpServletRequestBuilder request : endpoints()) {
                    mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
                }
                adminPost(ADMIN_POSTS + "/" + postId + "/suspend", token,
                        Map.of("reasonCode", "SPAM", "policyClause", "ARTICLE_5"))
                        .andExpect(status().isForbidden());
            }
            assertThat(row("SELECT status FROM post WHERE post_id = ?", postId)).containsEntry("status", "PUBLISHED");
            assertThat(notificationCount(postId)).isZero();
        }
    }

    // ------------------------------------------------------------------ 3-1 · 3-2. 대상 범위 · 파생 상태 · 탭

    @Nested
    @DisplayName("대상 범위 · 파생 상태 · 탭")
    class ScopeAndTabs {

        @Test
        @DisplayName("[LS-01 · GB-01] 작성중 · 공구 게시물은 목록 · 요약 · 검색 어디에도 없고 영구 삭제는 보인다")
        void scope() throws Exception {
            Long published = publish(soyeon, "보이는 게시물", LocalDateTime.now().minusDays(2));
            Long deleted = publish(soyeon, "삭제된 게시물", LocalDateTime.now().minusDays(3));
            softDelete(deleted, PostDeleteReason.SELF, LocalDateTime.now().minusDays(1));
            draft(soyeon, "임시저장 본문");
            Long groupBuy = groupBuyPost(soyeon, "공구 게시물 본문");
            report(groupBuy, "gb1", "한지준", LocalDateTime.now().minusHours(2));
            report(groupBuy, "gb2", "오서린", LocalDateTime.now().minusHours(1));

            assertThat(postIds(list())).containsExactly(published, deleted);
            for (String tab : List.of("ALL", "REPORTED", "SUSPENDED", "UNDER_REVIEW", "CLOSED")) {
                assertThat(postIds(list("tab", tab))).doesNotContain(groupBuy);
            }
            assertThat(postIds(list("keyword", "공구 게시물"))).isEmpty();
            assertThat(postIds(list("keyword", "임시저장"))).isEmpty();

            JsonNode summary = summary();
            assertThat(summary.get("all").asLong()).isEqualTo(2);
            assertThat(summary.get("reported").asLong()).isZero();
            assertThat(summary.get("actionRequired").asLong()).isZero();
        }

        @Test
        @DisplayName("[LS-02 · LS-05 · LS-06] 파생 상태 5종 — 라벨 · 저장 상태 · 탭이 같은 정의를 쓴다")
        void derivedStatusAndTabs() throws Exception {
            Long[] ids = fiveStates();
            JsonNode all = list();

            List<String> expectedMonitoring = List.of("PUBLISHED", "REPORTED", "SUSPENDED", "UNDER_REVIEW", "CLOSED");
            List<String> expectedLabel = List.of("게시중", "신고 접수", "노출 중지", "이의 신청 심사 중", "영구 삭제");
            List<String> expectedStored = List.of("PUBLISHED", "PUBLISHED", "SUSPENDED", "UNDER_REVIEW", "DELETED");
            for (int i = 0; i < ids.length; i++) {
                JsonNode row = rowOf(all, ids[i]);
                assertThat(row.get("monitoringStatus").asText()).isEqualTo(expectedMonitoring.get(i));
                assertThat(row.get("monitoringStatusLabel").asText()).isEqualTo(expectedLabel.get(i));
                assertThat(row.get("status").asText()).isEqualTo(expectedStored.get(i));
            }

            assertThat(postIds(list("tab", "ALL"))).isEqualTo(postIds(all)).hasSize(5);
            assertThat(postIds(list("tab", "REPORTED"))).containsExactly(ids[1]);
            assertThat(postIds(list("tab", "SUSPENDED"))).containsExactly(ids[2]);
            assertThat(postIds(list("tab", "UNDER_REVIEW"))).containsExactly(ids[3]);
            assertThat(postIds(list("tab", "CLOSED"))).containsExactly(ids[4]);
        }

        @Test
        @DisplayName("[LS-03 · LS-04] 신고 접수 → 게시중은 상태 전이가 아니다 — 종결 · 대기열 1건 반려 모두 대기 신고 0으로 돌아간다")
        void reportedBackToPublished() throws Exception {
            Long closed = publish(soyeon, "종결할 게시물", LocalDateTime.now().minusDays(1));
            report(closed, "c1", "한지준", LocalDateTime.now().minusHours(1));
            Long dismissed = publish(harin, "1건 반려할 게시물", LocalDateTime.now().minusDays(1));
            Long reportId = report(dismissed, "d1", "오서린", LocalDateTime.now().minusHours(1));

            closeReports(closed).andExpect(status().isOk());
            dismiss(reportId).andExpect(status().isNoContent());

            JsonNode page = list();
            assertThat(rowOf(page, closed).get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            assertThat(rowOf(page, dismissed).get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForList("SELECT status FROM post WHERE post_id IN (?, ?)", String.class, closed, dismissed))
                    .containsOnly("PUBLISHED");
        }

        @Test
        @DisplayName("[LS-07] 처리된 신고만 남은 게시중 게시물은 신고 접수 탭에 오지 않는다 — 대기 신고만 센다")
        void onlyPendingReportsCount() throws Exception {
            Long postId = publish(soyeon, "조치 후 취소", LocalDateTime.now().minusDays(1));
            report(postId, "p1", "한지준", LocalDateTime.now().minusHours(2));
            suspend(postId);
            cancelSuspension(postId, null).andExpect(status().isOk());

            assertThat(postIds(list("tab", "REPORTED"))).isEmpty();
            assertThat(rowOf(list(), postId).get("pendingReportCount").asLong()).isZero();
        }

        @Test
        @DisplayName("[LS-08] 없는 탭 · 정렬 값은 400이다")
        void unknownEnumIsBadRequest() throws Exception {
            adminGet(ADMIN_POSTS, "tab", "REPORT").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            adminGet(ADMIN_POSTS, "sort", "NEWEST").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("[LS-09] 폐기된 status 파라미터는 무시된다 — 저장 상태 필터는 파생 상태와 어긋난다")
        void legacyStatusParamIgnored() throws Exception {
            fiveStates();
            assertThat(postIds(list("status", "PUBLISHED"))).isEqualTo(postIds(list()));
        }
    }

    // ------------------------------------------------------------------ 3-3. 정렬

    @Nested
    @DisplayName("정렬")
    class Sorting {

        @Test
        @DisplayName("[LS-11] 조치 필요 순위 안에서는 신고의 「가장 오래된 대기 신고」와 이의 접수 시각을 함께 비교한다")
        void waitingSinceWithinActionRank() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            Long a = publish(soyeon, "A", now.minusDays(3));
            report(a, "a1", "가나다", now.minusHours(3));
            report(a, "a2", "라마바", now.minusHours(1));
            Long b = publish(soyeon, "B", now.minusDays(2));
            report(b, "b1", "사아자", now.minusHours(2));
            Long c = publish(harin, "C", now.minusDays(1));
            suspend(c);
            appeal(c);
            setAppealSubmittedAt(c, now.minusMinutes(150));

            assertThat(postIds(list())).containsExactly(a, c, b);
        }

        @Test
        @DisplayName("[LS-12] 노출 중지끼리는 이의 신청 기한이 급한 것부터")
        void suspendedByDeadline() throws Exception {
            Long late = publish(soyeon, "기한 6일", LocalDateTime.now().minusDays(2));
            Long soon = publish(harin, "기한 2일", LocalDateTime.now().minusDays(1));
            suspend(late);
            suspend(soon);
            setAppealDeadline(late, LocalDateTime.now().plusDays(6));
            setAppealDeadline(soon, LocalDateTime.now().plusDays(2));

            assertThat(postIds(list())).containsExactly(soon, late);
        }

        @Test
        @DisplayName("[LS-13] 게시중은 최신 게시순, 영구 삭제는 최신 삭제순 — 게시중이 영구 삭제보다 앞")
        void publishedThenClosed() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            Long f = publish(soyeon, "F", now.minusDays(3));
            Long g = publish(soyeon, "G", now.minusDays(1));
            Long h = publish(harin, "H", now.minusDays(9));
            softDelete(h, PostDeleteReason.SELF, now.minusDays(5));
            Long i = publish(harin, "I", now.minusDays(10));
            softDelete(i, PostDeleteReason.SELF, now.minusDays(1));

            assertThat(postIds(list())).containsExactly(g, f, i, h);
        }

        @Test
        @DisplayName("[LS-14] 정렬 키가 같으면 postId가 큰 쪽이 앞 — 페이지를 넘겨도 중복 · 누락이 없다")
        void tieBreakByPostId() throws Exception {
            LocalDateTime same = LocalDateTime.now().minusDays(1).withNano(0);
            Long first = publish(soyeon, "같은 시각 1", same);
            Long second = publish(soyeon, "같은 시각 2", same);
            Long third = publish(harin, "같은 시각 3", same);

            assertThat(postIds(list())).containsExactly(third, second, first);
            assertThat(postIds(list("sort", "LATEST"))).containsExactly(third, second, first);
            List<Long> paged = new java.util.ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                paged.addAll(postIds(list("page", String.valueOf(page), "size", "1")));
            }
            assertThat(paged).containsExactly(third, second, first);
        }

        @Test
        @DisplayName("[LS-15] 최신 등록순은 상태와 무관하게 게시 시각 내림차순")
        void latest() throws Exception {
            Long[] ids = fiveStates();
            // fiveStates는 게시 시각을 1일 ~ 5일 전으로 둔다
            assertThat(postIds(list("sort", "LATEST"))).containsExactly(ids);
        }

        @Test
        @DisplayName("[LS-16] 신고 많은 순은 대기 + 조치로 이어진 신고로 센다 — 종결분은 빠지고 동수면 최신 게시순")
        void mostReported() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            Long a = publish(soyeon, "A 대기 3", now.minusDays(4));
            for (int n = 0; n < 3; n++) {
                report(a, "a" + n, "가나다", now.minusHours(5 - n));
            }
            Long b = publish(soyeon, "B 조치 2", now.minusDays(3));
            report(b, "b0", "라마바", now.minusHours(4));
            report(b, "b1", "사아자", now.minusHours(3));
            suspend(b);
            Long c = publish(harin, "C 대기 1 · 종결 4", now.minusDays(2));
            for (int n = 0; n < 4; n++) {
                report(c, "c" + n, "차카타", now.minusHours(6 - n));
            }
            closeReports(c).andExpect(status().isOk());
            report(c, "c9", "파하가", now.minusMinutes(10));
            Long d = publish(harin, "D 조치 2 · 더 최신", now.minusDays(1));
            report(d, "d0", "나다라", now.minusHours(2));
            report(d, "d1", "다라마", now.minusHours(1));
            suspend(d);

            JsonNode page = list("sort", "MOST_REPORTED");
            assertThat(postIds(page)).containsExactly(a, d, b, c);
            assertThat(rowOf(page, c).get("reportCount").asLong()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ 3-4. 검색 · 필터 · 페이징

    @Nested
    @DisplayName("검색 · 필터 · 페이징")
    class SearchAndPaging {

        @Test
        @DisplayName("[LS-17 · LS-20] 쇼룸명 OR 본문 · 앞뒤 공백은 떼고 빈 검색어는 필터가 아니다")
        void keywordMatchesShowroomOrContent() throws Exception {
            Long byName = publish(soyeon, "가을 루틴", LocalDateTime.now().minusDays(2));
            Long byContent = publish(harin, "소연이 추천한 세럼", LocalDateTime.now().minusDays(1));
            Long none = publish(harin, "관계없는 글", LocalDateTime.now().minusDays(3));

            assertThat(postIds(list("keyword", "소연"))).containsExactlyInAnyOrder(byName, byContent);
            assertThat(postIds(list("keyword", "  소연  "))).containsExactlyInAnyOrder(byName, byContent);
            assertThat(postIds(list("keyword", ""))).containsExactlyInAnyOrder(byName, byContent, none);
            assertThat(postIds(list("keyword", "   "))).containsExactlyInAnyOrder(byName, byContent, none);
        }

        @Test
        @DisplayName("[LS-18 · LS-24] 닉네임은 쇼룸명이 없을 때만 검색 · 표시 대상이다")
        void nicknameOnlyWhenNoShowroomName() throws Exception {
            Creator nickOnly = createShowroom(null, "nick_only");
            Long anonymous = publish(nickOnly, "쇼룸명 없는 인플루언서", LocalDateTime.now().minusDays(1));
            Creator named = createShowroom("이름있음", "named", "nick_named");
            Long withName = publish(named, "쇼룸명 있는 인플루언서", LocalDateTime.now().minusDays(2));

            assertThat(postIds(list("keyword", "nick_"))).containsExactly(anonymous);

            JsonNode page = list();
            assertThat(rowOf(page, anonymous).get("showroomName").asText()).isEqualTo("nick_only");
            assertThat(rowOf(page, anonymous).get("showroomId").asLong()).isEqualTo(nickOnly.getId());
            assertThat(rowOf(page, withName).get("showroomName").asText()).isEqualTo("이름있음");
        }

        @Test
        @DisplayName("[LS-21] showroomId는 탭 · 검색과 AND로 묶인다")
        void showroomFilter() throws Exception {
            Long mine1 = publish(soyeon, "소연 게시물 1", LocalDateTime.now().minusDays(1));
            Long mine2 = publish(soyeon, "소연 게시물 2", LocalDateTime.now().minusDays(2));
            Long mineDeleted = publish(soyeon, "소연 삭제", LocalDateTime.now().minusDays(3));
            softDelete(mineDeleted, PostDeleteReason.SELF, LocalDateTime.now());
            publish(harin, "하린 게시물", LocalDateTime.now().minusDays(1));
            String showroomId = String.valueOf(soyeon.getId());

            assertThat(postIds(list("showroomId", showroomId))).containsExactly(mine1, mine2, mineDeleted);
            assertThat(postIds(list("showroomId", showroomId, "tab", "CLOSED"))).containsExactly(mineDeleted);
            assertThat(postIds(list("showroomId", showroomId, "keyword", "게시물 2"))).containsExactly(mine2);
        }

        @Test
        @DisplayName("[LS-22] 기본 20건 · size=50 · 범위를 넘는 페이지는 빈 목록이고 전체 건수는 같다")
        void paging() throws Exception {
            for (int n = 0; n < 25; n++) {
                publishWithoutImages(n % 2 == 0 ? soyeon : harin, "게시물 " + n, LocalDateTime.now().minusHours(n + 1));
            }

            JsonNode first = list();
            assertThat(first.get("content").size()).isEqualTo(20);
            assertThat(total(first)).isEqualTo(25);
            assertThat(list("size", "50").get("content").size()).isEqualTo(25);
            assertThat(list("page", "2", "size", "20").get("content").size()).isEqualTo(5);
            JsonNode beyond = list("page", "10");
            assertThat(beyond.get("content").size()).isZero();
            assertThat(total(beyond)).isEqualTo(25);
        }
    }

    // ------------------------------------------------------------------ 3-5. 행 값

    @Nested
    @DisplayName("행 값")
    class RowValues {

        @Test
        @DisplayName("[LS-23 · LS-25 · LS-26 · LS-27] 게시물 번호 · 대표 사진 · 사진 수 · 미리보기 · 수정일시")
        void rowFields() throws Exception {
            LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 11, 21, 5);
            String longContent = "브랜드 협찬 없이\n\n3개월   써본 " + "진짜 후기 ".repeat(30);
            Long postId = publish(soyeon, longContent, publishedAt, 3);
            Long empty = publish(harin, null, publishedAt.minusDays(1));
            Long edited = publish(harin, "수정한 게시물", publishedAt.minusDays(2));
            LocalDateTime editedAt = publishedAt.minusDays(1);
            jdbc.update("UPDATE post SET edited_at = ? WHERE post_id = ?", editedAt, edited);

            JsonNode page = list();
            JsonNode row = rowOf(page, postId);
            assertThat(row.get("postNo").asText())
                    .isEqualTo("POST-" + publishedAt.format(POST_NO_DATE) + "-" + String.format("%04d", postId));
            assertThat(row.get("thumbnailUrl").asText()).isEqualTo(CDN + "0.jpg");
            assertThat(row.get("imageCount").asInt()).isEqualTo(3);
            String preview = row.get("contentPreview").asText();
            assertThat(preview).startsWith("브랜드 협찬 없이 3개월 써본 진짜 후기").endsWith("…").hasSize(81)
                    .doesNotContain("\n").doesNotContain("  ");
            assertThat(row.get("editedAt").isNull()).isTrue();

            assertThat(rowOf(page, empty).get("contentPreview").isNull()).isTrue();
            assertThat(rowOf(page, edited).get("editedAt").asText()).startsWith(editedAt.toString().substring(0, 16));
        }

        @Test
        @DisplayName("[LS-28] 수정일시가 가장 오래된 대기 신고보다 뒤일 때만 editedAfterFirstReport")
        void editedAfterFirstReport() throws Exception {
            LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
            Long afterReport = publish(soyeon, "신고 뒤 수정", base.minusDays(1));
            report(afterReport, "e1", "한지준", base);
            Long beforeReport = publish(soyeon, "수정 뒤 신고", base.minusDays(1));
            report(beforeReport, "e2", "오서린", base);
            Long noReport = publish(harin, "신고 없이 수정", base.minusDays(1));
            Long closedAfter = publish(harin, "신고 뒤 수정 · 종결", base.minusDays(1));
            report(closedAfter, "e3", "임서현", base);
            jdbc.update("UPDATE post SET edited_at = ? WHERE post_id IN (?, ?, ?)",
                    base.plusHours(1), afterReport, noReport, closedAfter);
            jdbc.update("UPDATE post SET edited_at = ? WHERE post_id = ?", base.minusHours(1), beforeReport);
            closeReports(closedAfter).andExpect(status().isOk());

            JsonNode page = list();
            assertThat(rowOf(page, afterReport).get("editedAfterFirstReport").asBoolean()).isTrue();
            assertThat(rowOf(page, beforeReport).get("editedAfterFirstReport").asBoolean()).isFalse();
            assertThat(rowOf(page, noReport).get("editedAfterFirstReport").asBoolean()).isFalse();
            assertThat(rowOf(page, closedAfter).get("editedAfterFirstReport").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("[LS-29 · LS-30] 「신고」 열은 대기 + 조치로 이어진 신고 — 노출 중지 행에도 건수가 있고, 1건 반려분은 빠진다")
        void reportCountColumn() throws Exception {
            Long suspended = publish(soyeon, "신고 3 → 중지", LocalDateTime.now().minusDays(1));
            for (int n = 0; n < 3; n++) {
                report(suspended, "s" + n, "가나다", LocalDateTime.now().minusHours(3 - n));
            }
            suspend(suspended);
            Long partly = publish(harin, "신고 3 → 1건 반려", LocalDateTime.now().minusDays(1));
            Long firstReport = report(partly, "p0", "라마바", LocalDateTime.now().minusHours(3));
            report(partly, "p1", "사아자", LocalDateTime.now().minusHours(2));
            report(partly, "p2", "아자차", LocalDateTime.now().minusHours(1));
            dismiss(firstReport).andExpect(status().isNoContent());

            JsonNode page = list();
            assertThat(rowOf(page, suspended).get("reportCount").asLong()).isEqualTo(3);
            assertThat(rowOf(page, suspended).get("pendingReportCount").asLong()).isZero();
            assertThat(rowOf(page, partly).get("reportCount").asLong()).isEqualTo(2);
            assertThat(rowOf(page, partly).get("pendingReportCount").asLong()).isEqualTo(2);
        }

        @Test
        @DisplayName("[LS-31] 기한은 상태에 맞는 것 하나만 — 심사 중에는 이의 기한이 정지돼 null이다")
        void deadlinesPerState() throws Exception {
            Long[] ids = fiveStates();
            JsonNode page = list();

            JsonNode published = rowOf(page, ids[0]);
            for (String field : List.of("appealDeadline", "reviewDueAt", "deletedAt", "deleteReason", "purgeAt")) {
                assertThat(published.get(field).isNull()).as(field).isTrue();
            }
            JsonNode suspended = rowOf(page, ids[2]);
            assertThat(suspended.get("appealDeadline").isNull()).isFalse();
            assertThat(suspended.get("reviewDueAt").isNull()).isTrue();
            JsonNode underReview = rowOf(page, ids[3]);
            assertThat(underReview.get("appealDeadline").isNull()).isTrue();
            assertThat(underReview.get("reviewDueAt").isNull()).isFalse();
            JsonNode closed = rowOf(page, ids[4]);
            assertThat(closed.get("deletedAt").isNull()).isFalse();
            assertThat(closed.get("deleteReason").asText()).isEqualTo("APPEAL_REJECTED");
            assertThat(closed.get("purgeAt").isNull()).isFalse();
            assertThat(closed.get("appealDeadline").isNull()).isTrue();
        }
    }

    // ------------------------------------------------------------------ 4. 요약

    @Nested
    @DisplayName("요약")
    class Summary {

        @Test
        @DisplayName("[SM-03] C9 — 신고 접수 0 · 심사 중 1이면 배지는 1이고 신고 접수 탭은 빈 목록이다")
        void badgeWithOnlyReview() throws Exception {
            Long postId = publish(soyeon, "심사 중", LocalDateTime.now().minusDays(1));
            suspend(postId);
            appeal(postId);

            JsonNode summary = summary();
            assertThat(summary.get("reported").asLong()).isZero();
            assertThat(summary.get("underReview").asLong()).isEqualTo(1);
            assertThat(summary.get("actionRequired").asLong()).isEqualTo(1);
            JsonNode reportedTab = list("tab", "REPORTED");
            assertThat(reportedTab.get("content").size()).isZero();
            assertThat(total(reportedTab)).isZero();
        }

        @Test
        @DisplayName("[SM-02 · SM-04 · SM-05] 요약은 검색 · 필터와 무관한 전체 기준이고 탭 목록 건수와 맞는다")
        void summaryMatchesTabs() throws Exception {
            fiveStates();
            draft(soyeon, "작성중");
            groupBuyPost(harin, "공구");

            JsonNode summary = summary();
            JsonNode filtered = adminJson(ADMIN_POSTS + "/summary", "keyword", "소연", "showroomId",
                    String.valueOf(soyeon.getId()), "tab", "CLOSED");
            assertThat(filtered).isEqualTo(summary);
            assertTabsMatch(summary);
            long publishedWithoutReport = summary.get("all").asLong() - summary.get("reported").asLong()
                    - summary.get("suspended").asLong() - summary.get("underReview").asLong()
                    - summary.get("closed").asLong();
            assertThat(publishedWithoutReport).isEqualTo(1);
        }

        @Test
        @DisplayName("[SM-06] 조치마다 요약이 즉시 움직이고 매번 탭 건수와 맞는다")
        void summaryMovesWithActions() throws Exception {
            Long postId = publish(soyeon, "흐름", LocalDateTime.now().minusDays(1));
            report(postId, "m1", "한지준", LocalDateTime.now().minusHours(2));
            assertSummary(1, 1, 0, 0, 0, 1);

            closeReports(postId).andExpect(status().isOk());
            assertSummary(1, 0, 0, 0, 0, 0);

            suspend(postId);
            assertSummary(1, 0, 1, 0, 0, 0);

            Long appealId = appeal(postId);
            assertSummary(1, 0, 0, 1, 0, 1);

            reject(appealId, Map.of("comment", "재게시할 수 없습니다.")).andExpect(status().isOk());
            assertSummary(1, 0, 0, 0, 1, 0);
        }

        private void assertSummary(long all, long reported, long suspended, long underReview, long closed,
                                   long actionRequired) throws Exception {
            JsonNode summary = summary();
            assertThat(summary.get("all").asLong()).isEqualTo(all);
            assertThat(summary.get("reported").asLong()).isEqualTo(reported);
            assertThat(summary.get("suspended").asLong()).isEqualTo(suspended);
            assertThat(summary.get("underReview").asLong()).isEqualTo(underReview);
            assertThat(summary.get("closed").asLong()).isEqualTo(closed);
            assertThat(summary.get("actionRequired").asLong()).isEqualTo(actionRequired);
            assertTabsMatch(summary);
        }

        private void assertTabsMatch(JsonNode summary) throws Exception {
            assertThat(total(list("tab", "ALL"))).isEqualTo(summary.get("all").asLong());
            assertThat(total(list("tab", "REPORTED"))).isEqualTo(summary.get("reported").asLong());
            assertThat(total(list("tab", "SUSPENDED"))).isEqualTo(summary.get("suspended").asLong());
            assertThat(total(list("tab", "UNDER_REVIEW")))
                    .isEqualTo(summary.get("underReview").asLong());
            assertThat(total(list("tab", "CLOSED"))).isEqualTo(summary.get("closed").asLong());
        }
    }

    // ------------------------------------------------------------------ 10. 코드 목록

    @Nested
    @DisplayName("코드 목록")
    class Codes {

        @Test
        @DisplayName("[CD-02] 근거 규정 5개 — 라벨 · FE 기본 선택용 추천 사유")
        void policyClauses() throws Exception {
            JsonNode clauses = adminJson(ADMIN_POSTS + "/policy-clauses");

            assertThat(texts(clauses, "code"))
                    .containsExactly("ARTICLE_3_2", "ARTICLE_3_4", "ARTICLE_4_1", "ARTICLE_4_3", "ARTICLE_5");
            assertThat(texts(clauses, "label")).allMatch(label -> label.startsWith("커뮤니티 운영정책 "));
            assertThat(suggested(clauses.get(0))).containsExactly("MISLEADING_INFO");
            assertThat(suggested(clauses.get(1))).containsExactly("MISLEADING_INFO");
            assertThat(suggested(clauses.get(2))).containsExactlyInAnyOrder("ABUSE_HATE", "SEXUAL_CONTENT");
            assertThat(suggested(clauses.get(3))).containsExactlyInAnyOrder("FRAUD_IMPERSONATION", "PERSONAL_INFO");
            assertThat(suggested(clauses.get(4))).containsExactly("SPAM");
        }

        @Test
        @DisplayName("[CD-01 · CD-03] 앱 신고 사유는 조치 사유 7종에서 운영자 전용(개인정보 노출)만 뺀 6종이고 라벨이 같다")
        void reportReasonsAreSuspensionReasonsMinusOperatorOnly() throws Exception {
            JsonNode suspension = adminJson(ADMIN_POSTS + "/suspension-reasons");
            String body = mockMvc.perform(get("/v1/user/posts/report-reasons"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode report = objectMapper.readTree(body);

            assertThat(texts(suspension, "code")).containsExactly("MISLEADING_INFO", "FRAUD_IMPERSONATION",
                    "ABUSE_HATE", "SEXUAL_CONTENT", "SPAM", "PERSONAL_INFO", "OTHER");
            List<String> reportCodes = texts(report, "code");
            assertThat(reportCodes).containsExactly("MISLEADING_INFO", "FRAUD_IMPERSONATION", "ABUSE_HATE",
                    "SEXUAL_CONTENT", "SPAM", "OTHER");
            for (JsonNode item : suspension) {
                if (item.get("operatorOnly").asBoolean()) {
                    assertThat(reportCodes).doesNotContain(item.get("code").asText());
                    continue;
                }
                JsonNode same = null;
                for (JsonNode candidate : report) {
                    if (candidate.get("code").asText().equals(item.get("code").asText())) {
                        same = candidate;
                    }
                }
                assertThat(same).as(item.get("code").asText()).isNotNull();
                assertThat(same.get("label").asText()).isEqualTo(item.get("label").asText());
            }
        }

        private List<String> suggested(JsonNode clause) {
            List<String> values = new java.util.ArrayList<>();
            clause.get("suggestedFor").forEach(node -> values.add(node.asText()));
            return values;
        }
    }

    // ------------------------------------------------------------------ 픽스처

    /**
     * 상태 5종 — [게시중 · 신고 접수 · 노출 중지 · 심사 중 · 반려 삭제]. 게시 시각은 1일 ~ 5일 전이다(최신 등록순 = 배열 순서).
     */
    private Long[] fiveStates() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long published = publish(soyeon, "게시중 소연", now.minusDays(1));
        Long reported = publish(soyeon, "신고 접수 소연", now.minusDays(2));
        Users reporter = consumer("five-reporter", "한지준");
        report(reported, reporter, PostReportReason.MISLEADING_INFO, null, now.minusHours(3));
        Long suspended = publish(harin, "노출 중지 하린", now.minusDays(3));
        suspend(suspended);
        Long underReview = publish(soyeon, "심사 중 소연", now.minusDays(4));
        suspend(underReview);
        appeal(underReview);
        Long closed = publish(harin, "반려 삭제 하린", now.minusDays(5));
        rejectedPost(closed, "재게시할 수 없습니다.");
        return new Long[]{published, reported, suspended, underReview, closed};
    }
}
