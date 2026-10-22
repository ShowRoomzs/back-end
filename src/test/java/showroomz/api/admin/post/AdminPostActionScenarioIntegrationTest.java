package showroomz.api.admin.post;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.service.PostPurgeService;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostReportReason;
import showroomz.global.config.properties.PostProperties;

import java.time.LocalDateTime;
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
 * [통합] 어드민 일반 게시물 — 조치 · 신고 대기열 · 이의 신청 목록 · 원본 내려받기 · 배치 · 공구 게시물 차단 · 통합 시나리오
 * (54 테스트 시나리오 11 ~ 21절).
 *
 * <p>쓰기 케이스는 응답만 보지 않고 원천 행(조치 · 신고 · 이의 · 알림 이력)을 직접 읽는다. 실패한 쓰기는 아무 행도 바꾸지 않아야 한다.
 */
@DisplayName("[통합] 어드민 일반 게시물 — 조치 시나리오")
class AdminPostActionScenarioIntegrationTest extends AdminPostScenarioSupport {

    @Autowired
    private PostPurgeService postPurgeService;
    @Autowired
    private PostProperties postProperties;

    // ------------------------------------------------------------------ 11. 노출 중지

    @Nested
    @DisplayName("노출 중지")
    class Suspend {

        @Test
        @DisplayName("[SP-01 · SP-02] 조치 행 · 응답 · 알림 payload에 조치 기록 5항목이 함께 굳는다")
        void suspendRecords() throws Exception {
            Long postId = publish(soyeon, "노출 중지", LocalDateTime.now().minusDays(1));

            JsonNode response = json(suspendRequest(postId, body("reasonCode", "MISLEADING_INFO",
                    "policyClause", "ARTICLE_3_2", "reasonDetail", "  효과를 단정하는 표현이 있습니다.  "))
                    .andExpect(status().isOk()));
            assertThat(response.get("postId").asLong()).isEqualTo(postId);
            assertThat(response.get("status").asText()).isEqualTo("SUSPENDED");
            assertThat(response.get("monitoringStatus").asText()).isEqualTo("SUSPENDED");
            assertThat(response.get("closedReportCount").asInt()).isZero();

            Map<String, Object> suspension = row("SELECT * FROM post_suspension WHERE post_id = ?", postId);
            assertThat(suspension).containsEntry("reason_code", "MISLEADING_INFO")
                    .containsEntry("reason_detail", "효과를 단정하는 표현이 있습니다.")
                    .containsEntry("policy_clause_code", "ARTICLE_3_2")
                    .containsEntry("policy_ref", "커뮤니티 운영정책 3조 2항 — 효과 · 가격 오인 표현")
                    .containsEntry("suspended_by", operator.getId())
                    .containsEntry("resolution", null);
            LocalDateTime suspendedAt = ((java.sql.Timestamp) suspension.get("suspended_at")).toLocalDateTime();
            LocalDateTime deadline = ((java.sql.Timestamp) suspension.get("appeal_deadline")).toLocalDateTime();
            assertThat(deadline).isEqualTo(suspendedAt.plusDays(7));
            // 조치 응답은 초 단위로 직렬화된다
            assertThat(at(response.get("appealDeadline"))).isEqualTo(deadline.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

            assertThat(notificationCount(postId, "SUSPENDED")).isEqualTo(1);
            JsonNode payload = latestPayload(postId, "SUSPENDED");
            assertThat(payload.get("suspensionId").asLong()).isEqualTo(((Number) suspension.get("suspension_id")).longValue());
            assertThat(payload.get("reasonCode").asText()).isEqualTo("MISLEADING_INFO");
            assertThat(payload.get("reasonLabel").asText()).isEqualTo("허위 · 과장 정보");
            assertThat(payload.get("reasonDetail").asText()).isEqualTo("효과를 단정하는 표현이 있습니다.");
            assertThat(payload.get("policyClauseCode").asText()).isEqualTo("ARTICLE_3_2");
            assertThat(payload.get("policyRef").asText()).isEqualTo("커뮤니티 운영정책 3조 2항 — 효과 · 가격 오인 표현");
            assertThat(payload.get("suspendedBy").asLong()).isEqualTo(operator.getId());
            assertThat(payload.hasNonNull("suspendedAt")).isTrue();
            assertThat(payload.hasNonNull("appealDeadline")).isTrue();
        }

        @Test
        @DisplayName("[SP-03] 대기 신고만 조치로 이어짐(ACCEPTED)으로 닫힌다 — 처리자 · 시각은 조치와 같고 이미 반려된 신고는 그대로다")
        void acceptsOnlyPendingReports() throws Exception {
            Long postId = publish(soyeon, "신고 4건", LocalDateTime.now().minusDays(1));
            Long dismissed = report(postId, "sp-0", "가나다", LocalDateTime.now().minusHours(5));
            dismiss(dismissed).andExpect(status().isNoContent());
            for (int n = 1; n <= 3; n++) {
                report(postId, "sp-" + n, "라마바", LocalDateTime.now().minusHours(5 - n));
            }

            JsonNode response = json(suspendRequest(postId, body("reasonCode", "SPAM", "policyClause", "ARTICLE_5"))
                    .andExpect(status().isOk()));

            assertThat(response.get("closedReportCount").asInt()).isEqualTo(3);
            assertThat(reportStatuses(postId)).containsExactly("DISMISSED", "ACCEPTED", "ACCEPTED", "ACCEPTED");
            Object suspendedAt = row("SELECT suspended_at FROM post_suspension WHERE post_id = ?", postId).get("suspended_at");
            List<Map<String, Object>> accepted = jdbc.queryForList(
                    "SELECT handled_by, handled_at FROM post_report WHERE post_id = ? AND status = 'ACCEPTED'", postId);
            assertThat(accepted).allSatisfy(r -> {
                assertThat(((Number) r.get("handled_by")).longValue()).isEqualTo(operator.getId());
                assertThat(r.get("handled_at")).isEqualTo(suspendedAt);
            });
        }

        @Test
        @DisplayName("[SP-04 · SP-10 · SP-11] 공백 설명은 null로 · 운영자 전용 사유 · 추천 밖 조항 조합도 정상 조치다")
        void blankDetailOperatorOnlyAndFreeCombination() throws Exception {
            Long blank = publish(soyeon, "공백 설명", LocalDateTime.now().minusDays(1));
            suspendRequest(blank, body("reasonCode", "MISLEADING_INFO", "policyClause", "ARTICLE_3_2",
                    "reasonDetail", "   ")).andExpect(status().isOk());
            assertThat(row("SELECT reason_detail FROM post_suspension WHERE post_id = ?", blank)).containsEntry("reason_detail", null);
            assertThat(latestPayload(blank, "SUSPENDED").path("reasonDetail").isMissingNode()
                    || latestPayload(blank, "SUSPENDED").get("reasonDetail").isNull()).isTrue();

            Long personal = publish(soyeon, "개인정보", LocalDateTime.now().minusDays(1));
            suspendRequest(personal, body("reasonCode", "PERSONAL_INFO", "policyClause", "ARTICLE_4_3"))
                    .andExpect(status().isOk());
            Long free = publish(harin, "조합 자유", LocalDateTime.now().minusDays(1));
            suspendRequest(free, body("reasonCode", "SPAM", "policyClause", "ARTICLE_3_4")).andExpect(status().isOk());
            assertThat(row("SELECT reason_code, policy_clause_code FROM post_suspension WHERE post_id = ?", free))
                    .containsEntry("reason_code", "SPAM").containsEntry("policy_clause_code", "ARTICLE_3_4");
        }

        @Test
        @DisplayName("[SP-05 ~ SP-09 · SP-13] 입력 검증 — 근거 규정 · 사유 · 옛 코드 · 기타 상세 · 500자 · 없는 게시물")
        void validation() throws Exception {
            Long postId = publish(soyeon, "검증", LocalDateTime.now().minusDays(1));

            expectError(suspendRequest(postId, body("reasonCode", "SPAM")), 400, "POST_POLICY_CLAUSE_REQUIRED");
            expectError(suspendRequest(postId, body("policyClause", "ARTICLE_5")), 400, "INVALID_INPUT");
            expectError(suspendRequest(postId, body("reasonCode", "MEDICAL_CLAIM", "policyClause", "ARTICLE_3_4")),
                    400, "INVALID_INPUT");
            expectError(suspendRequest(postId, body("reasonCode", "SPAM", "policyClause", "ARTICLE_9")), 400, "INVALID_INPUT");
            expectError(suspendRequest(postId, body("reasonCode", "OTHER", "policyClause", "ARTICLE_5")),
                    400, "POST_SUSPENSION_DETAIL_REQUIRED");
            expectError(suspendRequest(postId, body("reasonCode", "OTHER", "policyClause", "ARTICLE_5", "reasonDetail", "  ")),
                    400, "POST_SUSPENSION_DETAIL_REQUIRED");
            expectError(suspendRequest(postId, body("reasonCode", "SPAM", "policyClause", "ARTICLE_5",
                    "reasonDetail", "가".repeat(501))), 400, "INVALID_INPUT");
            expectError(suspendRequest(999_999L, body("reasonCode", "SPAM", "policyClause", "ARTICLE_5")), 404, "POST_NOT_FOUND");

            assertThat(row("SELECT status FROM post WHERE post_id = ?", postId)).containsEntry("status", "PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_suspension", Integer.class)).isZero();
            assertThat(notificationCount(postId)).isZero();

            suspendRequest(postId, body("reasonCode", "OTHER", "policyClause", "ARTICLE_5",
                    "reasonDetail", "가".repeat(500))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("[SP-12 · SP-14] 게시중이 아니면 409 — 단 근거 규정이 없으면 상태보다 400이 먼저다")
        void onlyPublished() throws Exception {
            Long suspended = publish(soyeon, "중지", LocalDateTime.now().minusDays(1));
            suspend(suspended);
            Long review = publish(soyeon, "심사 중", LocalDateTime.now().minusDays(1));
            suspend(review);
            appeal(review);
            Long deleted = publish(harin, "삭제", LocalDateTime.now().minusDays(1));
            softDelete(deleted, PostDeleteReason.SELF, LocalDateTime.now());
            Long draft = draft(harin, "작성중");
            int suspensionsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM post_suspension", Integer.class);

            for (Long postId : List.of(suspended, review, deleted, draft)) {
                expectError(suspendRequest(postId, body("reasonCode", "SPAM", "policyClause", "ARTICLE_5")),
                        409, "POST_NOT_EDITABLE");
            }
            expectError(suspendRequest(suspended, body("reasonCode", "SPAM")), 400, "POST_POLICY_CLAUSE_REQUIRED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_suspension", Integer.class)).isEqualTo(suspensionsBefore);
        }

        @Test
        @DisplayName("[SP-15 · SP-16 · SP-17] 중지되면 소비자에게 사라지고(신고도 404) 스튜디오는 수정을 막는다 — 운영자 · 신고자 정보는 새지 않는다")
        void otherSurfaces() throws Exception {
            Long postId = publish(soyeon, "다른 화면", LocalDateTime.now().minusDays(1));
            report(postId, "sp-r", "한지준", LocalDateTime.now().minusHours(1));
            mockMvc.perform(get("/v1/user/showrooms/posts/" + postId)).andExpect(status().isOk());

            suspend(postId);

            mockMvc.perform(get("/v1/user/showrooms/posts/" + postId)).andExpect(status().isNotFound());
            Users late = consumer("sp-late", "오서린");
            appReport(postId, late, body("reasonCode", "SPAM")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

            String studio = mockMvc.perform(get("/v1/creator/posts/" + postId)
                            .header(HttpHeaders.AUTHORIZATION, creatorToken(soyeon)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.editable").value(false))
                    .andReturn().getResponse().getContentAsString();
            assertThat(studio).doesNotContain("김운영").doesNotContain("한*준").doesNotContain("한지준");
            mockMvc.perform(put("/v1/creator/posts/" + postId).header(HttpHeaders.AUTHORIZATION, creatorToken(soyeon))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("action", "PUBLISH", "content", "고쳐서 다시 올립니다"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_NOT_EDITABLE"));
        }
    }

    // ------------------------------------------------------------------ 12. 신고 종결

    @Nested
    @DisplayName("신고 종결")
    class CloseReports {

        @Test
        @DisplayName("[RC-01 · RC-02 · RC-03] 대기 신고 전부 반려 · 처리자 · 시각 동일 · 게시물 그대로 · 누구에게도 알리지 않는다")
        void closesQuietly() throws Exception {
            Long postId = publish(soyeon, "종결", LocalDateTime.now().minusDays(1));
            for (int n = 0; n < 3; n++) {
                report(postId, "rc-" + n, "가나다", LocalDateTime.now().minusHours(3 - n));
            }
            Map<String, Object> before = row("SELECT status, published_at, edited_at, deleted_at FROM post WHERE post_id = ?", postId);

            JsonNode response = json(adminPost(ADMIN_POSTS + "/" + postId + "/reports/close", admin,
                    Map.of("note", "개인 사용감으로 판단")).andExpect(status().isOk()));

            assertThat(response.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(response.get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            assertThat(response.get("closedReportCount").asInt()).isEqualTo(3);
            List<Map<String, Object>> handled = jdbc.queryForList(
                    "SELECT status, handled_by, handled_at FROM post_report WHERE post_id = ?", postId);
            assertThat(handled).extracting(r -> r.get("status")).containsOnly("DISMISSED");
            assertThat(handled).extracting(r -> ((Number) r.get("handled_by")).longValue()).containsOnly(operator.getId());
            assertThat(handled).extracting(r -> r.get("handled_at")).containsOnly(handled.get(0).get("handled_at"));
            assertThat(row("SELECT status, published_at, edited_at, deleted_at FROM post WHERE post_id = ?", postId))
                    .isEqualTo(before);
            assertThat(notificationCount(postId)).isZero();

            Long emptyBody = publish(harin, "빈 본문", LocalDateTime.now().minusDays(1));
            report(emptyBody, "rc-e", "라마바", LocalDateTime.now().minusHours(1));
            adminPost(ADMIN_POSTS + "/" + emptyBody + "/reports/close", admin, "{}").andExpect(status().isOk());
        }

        @Test
        @DisplayName("[RC-04 · RC-05 · RC-06] 메모 201자 400 · 대기 신고가 없거나 게시중이 아니면 409")
        void rejectsInvalid() throws Exception {
            Long postId = publish(soyeon, "메모", LocalDateTime.now().minusDays(1));
            report(postId, "rc-n", "가나다", LocalDateTime.now().minusHours(1));
            expectError(adminPost(ADMIN_POSTS + "/" + postId + "/reports/close", admin, Map.of("note", "가".repeat(201))),
                    400, "INVALID_INPUT");
            assertThat(reportStatuses(postId)).containsExactly("PENDING");
            adminPost(ADMIN_POSTS + "/" + postId + "/reports/close", admin, Map.of("note", "가".repeat(200)))
                    .andExpect(status().isOk());

            // 신고 이력은 있지만 대기 신고가 없다
            expectError(closeReports(postId), 409, "POST_REPORT_NOTHING_TO_CLOSE");

            Long suspended = publish(soyeon, "중지", LocalDateTime.now().minusDays(1));
            suspend(suspended);
            Long review = publish(soyeon, "심사", LocalDateTime.now().minusDays(1));
            suspend(review);
            appeal(review);
            Long deleted = publish(harin, "삭제", LocalDateTime.now().minusDays(1));
            softDelete(deleted, PostDeleteReason.SELF, LocalDateTime.now());
            Long draft = draft(harin, "작성중");
            for (Long target : List.of(suspended, review, deleted, draft)) {
                expectError(closeReports(target), 409, "POST_REPORT_NOTHING_TO_CLOSE");
            }
        }

        @Test
        @DisplayName("[RC-07 · RC-08] 종결 뒤 같은 신고자는 다시 신고할 수 없고 새 소비자의 신고는 다시 신고 접수로 만든다")
        void reReportAfterClose() throws Exception {
            Long postId = publish(soyeon, "재신고", LocalDateTime.now().minusDays(1));
            Users first = consumer("rc-first", "한지준");
            appReport(postId, first, body("reasonCode", "MISLEADING_INFO")).andExpect(status().isNoContent());
            closeReports(postId).andExpect(status().isOk());

            appReport(postId, first, body("reasonCode", "MISLEADING_INFO")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_REPORT_ALREADY_SUBMITTED"));
            appReport(postId, consumer("rc-second", "오서린"), body("reasonCode", "SPAM"))
                    .andExpect(status().isNoContent());

            JsonNode row = rowOf(list(), postId);
            assertThat(row.get("monitoringStatus").asText()).isEqualTo("REPORTED");
            assertThat(row.get("pendingReportCount").asLong()).isEqualTo(1);
            assertThat(detail(postId).get("reports").get("totalCount").asInt()).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------ 13. 조치 취소 · 재게시

    @Nested
    @DisplayName("조치 취소 · 재게시")
    class CancelSuspension {

        @Test
        @DisplayName("[CC-01 · CC-02 · CC-04] 운영자 취소로 닫고 재게시 · 처리자 기록 · 통지에 메모를 굳힌다 · 조치로 닫힌 신고는 그대로")
        void cancels() throws Exception {
            Long postId = publish(soyeon, "취소", LocalDateTime.now().minusDays(1));
            report(postId, "cc-1", "한지준", LocalDateTime.now().minusHours(2));
            report(postId, "cc-2", "오서린", LocalDateTime.now().minusHours(1));
            suspend(postId);

            JsonNode response = json(adminPost(ADMIN_POSTS + "/" + postId + "/suspension/cancel", admin2,
                    Map.of("note", "  근거 규정 적용 오류  ")).andExpect(status().isOk()));

            assertThat(response.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(response.get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            Map<String, Object> suspension = row("SELECT * FROM post_suspension WHERE post_id = ?", postId);
            assertThat(suspension).containsEntry("resolution", "CANCELED_BY_ADMIN")
                    .containsEntry("resolved_by", operator2.getId());
            assertThat(suspension.get("resolved_at")).isNotNull();
            assertThat(row("SELECT status FROM post WHERE post_id = ?", postId)).containsEntry("status", "PUBLISHED");
            assertThat(reportStatuses(postId)).containsExactly("ACCEPTED", "ACCEPTED");

            assertThat(notificationCount(postId, "SUSPENSION_CANCELED")).isEqualTo(1);
            JsonNode payload = latestPayload(postId, "SUSPENSION_CANCELED");
            assertThat(payload.get("suspensionId").asLong()).isEqualTo(((Number) suspension.get("suspension_id")).longValue());
            assertThat(payload.get("canceledBy").asLong()).isEqualTo(operator2.getId());
            assertThat(payload.hasNonNull("canceledAt")).isTrue();
            assertThat(payload.get("note").asText()).isEqualTo("근거 규정 적용 오류");
        }

        @Test
        @DisplayName("[CC-03] 메모는 선택 — 본문 없음 · 공백은 null로 굳고 201자는 400이다")
        void noteOptional() throws Exception {
            Long noBody = publish(soyeon, "본문 없음", LocalDateTime.now().minusDays(1));
            suspend(noBody);
            cancelSuspension(noBody, null).andExpect(status().isOk());
            assertThat(latestPayload(noBody, "SUSPENSION_CANCELED").get("note").isNull()).isTrue();

            Long blank = publish(soyeon, "공백", LocalDateTime.now().minusDays(1));
            suspend(blank);
            expectError(cancelSuspension(blank, Map.of("note", "가".repeat(201))), 400, "INVALID_INPUT");
            assertThat(row("SELECT status FROM post WHERE post_id = ?", blank)).containsEntry("status", "SUSPENDED");
            cancelSuspension(blank, Map.of("note", "   ")).andExpect(status().isOk());
            assertThat(latestPayload(blank, "SUSPENSION_CANCELED").get("note").isNull()).isTrue();
        }

        @Test
        @DisplayName("[CC-05 · CC-06] 노출 중지 상태에서만 — 심사 중 · 삭제 · 게시중 · 작성중은 409 · 승인 뒤 재중지는 취소할 수 있다")
        void onlySuspended() throws Exception {
            Long review = publish(soyeon, "심사", LocalDateTime.now().minusDays(1));
            suspend(review);
            appeal(review);
            Long deleted = publish(soyeon, "삭제", LocalDateTime.now().minusDays(1));
            softDelete(deleted, PostDeleteReason.SELF, LocalDateTime.now());
            Long published = publish(harin, "게시중", LocalDateTime.now().minusDays(1));
            Long draft = draft(harin, "작성중");
            for (Long postId : List.of(review, deleted, published, draft)) {
                expectError(cancelSuspension(postId, null), 409, "POST_SUSPENSION_NOT_CANCELABLE");
            }

            Long again = publish(soyeon, "재중지", LocalDateTime.now().minusDays(2));
            suspend(again);
            approve(appeal(again), Map.of()).andExpect(status().isOk());
            suspend(again);
            cancelSuspension(again, null).andExpect(status().isOk());
            assertThat(row("SELECT status FROM post WHERE post_id = ?", again)).containsEntry("status", "PUBLISHED");
        }

        @Test
        @DisplayName("[CC-08 · CC-09 · CC-10] 취소된 조치는 만료 배치 대상이 아니고, 소비자에게 다시 보이며, 다시 내리면 새 조치 · 이의 신청이 열린다")
        void afterCancel() throws Exception {
            Long postId = publish(soyeon, "취소 뒤", LocalDateTime.now().minusDays(9));
            suspend(postId);
            cancelSuspension(postId, null).andExpect(status().isOk());
            jdbc.update("UPDATE post_suspension SET appeal_deadline = ? WHERE post_id = ?",
                    LocalDateTime.now().minusMinutes(1), postId);
            int notices = notificationCount(postId);

            postLifecycleService.expireOverdueSuspensions(LocalDateTime.now(), 100);

            assertThat(row("SELECT status FROM post WHERE post_id = ?", postId)).containsEntry("status", "PUBLISHED");
            assertThat(notificationCount(postId)).isEqualTo(notices);
            mockMvc.perform(get("/v1/user/showrooms/posts/" + postId)).andExpect(status().isOk());
            mockMvc.perform(get("/v1/creator/posts/" + postId).header(HttpHeaders.AUTHORIZATION, creatorToken(soyeon)))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));

            suspend(postId);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_suspension WHERE post_id = ?", Integer.class, postId))
                    .isEqualTo(2);
            appeal(postId);
        }
    }

    // ------------------------------------------------------------------ 14. 이의 신청 심사

    @Nested
    @DisplayName("이의 신청 심사")
    class AppealReview {

        @Test
        @DisplayName("[AA-01 · AA-02 · AA-06] 승인 — 재게시 · 조치는 재게시로 닫힘 · 공백 의견은 null · 게시 시각 유지 · 통지")
        void approveRepublishes() throws Exception {
            Long postId = publish(soyeon, "승인", LocalDateTime.now().minusDays(3));
            Object publishedAt = row("SELECT published_at FROM post WHERE post_id = ?", postId).get("published_at");
            suspend(postId);
            Long appealId = appeal(postId);

            JsonNode response = json(approve(appealId, Map.of("comment", "  ")).andExpect(status().isOk()));

            assertThat(response.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(response.get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            Map<String, Object> appeal = row("SELECT * FROM post_appeal WHERE appeal_id = ?", appealId);
            assertThat(appeal).containsEntry("status", "APPROVED").containsEntry("reviewed_by", operator.getId())
                    .containsEntry("review_comment", null);
            assertThat(appeal.get("reviewed_at")).isNotNull();
            assertThat(row("SELECT resolution FROM post_suspension WHERE post_id = ?", postId))
                    .containsEntry("resolution", "REPUBLISHED");
            assertThat(row("SELECT status, published_at FROM post WHERE post_id = ?", postId))
                    .containsEntry("status", "PUBLISHED").containsEntry("published_at", publishedAt);
            JsonNode payload = latestPayload(postId, "APPEAL_APPROVED");
            assertThat(payload.get("appealId").asLong()).isEqualTo(appealId);
            assertThat(payload.get("reviewedBy").asLong()).isEqualTo(operator.getId());
            assertThat(payload.get("comment").isNull()).isTrue();
        }

        @Test
        @DisplayName("[AA-04 · AA-05 · AA-07] 의견 501자 400 · 심사가 끝난 신청 409 · 없는 신청 404 · 재중지 뒤 이의는 다시 못 낸다")
        void approveRules() throws Exception {
            Long postId = publish(soyeon, "규칙", LocalDateTime.now().minusDays(1));
            suspend(postId);
            Long appealId = appeal(postId);

            expectError(approve(appealId, Map.of("comment", "가".repeat(501))), 400, "INVALID_INPUT");
            assertThat(row("SELECT status FROM post_appeal WHERE appeal_id = ?", appealId)).containsEntry("status", "PENDING");
            approve(appealId, Map.of()).andExpect(status().isOk());
            expectError(approve(appealId, Map.of()), 409, "POST_APPEAL_ALREADY_REVIEWED");
            expectError(approve(999_999L, Map.of()), 404, "POST_APPEAL_NOT_FOUND");

            suspend(postId);
            mockMvc.perform(post("/v1/creator/posts/" + postId + "/appeal")
                            .header(HttpHeaders.AUTHORIZATION, ownerTokenOf(postId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("content", "다시 신청합니다."))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_APPEAL_ALREADY_SUBMITTED"));
        }

        @Test
        @DisplayName("[AR-01 · AR-02] 반려 — 영구 삭제 · 유예 = 반려 + 7일 · 보관 = +6개월 · 조치는 반려 삭제로 닫힘 · 사유를 통지에 굳힌다")
        void rejectDeletes() throws Exception {
            Long postId = publish(soyeon, "반려", LocalDateTime.now().minusDays(2));
            suspend(postId);
            Long appealId = appeal(postId);

            JsonNode response = json(reject(appealId, Map.of("comment", "의료 행위 대체를 암시합니다."))
                    .andExpect(status().isOk()));

            assertThat(response.get("status").asText()).isEqualTo("DELETED");
            assertThat(response.get("monitoringStatus").asText()).isEqualTo("CLOSED");
            Map<String, Object> appeal = row("SELECT * FROM post_appeal WHERE appeal_id = ?", appealId);
            LocalDateTime reviewedAt = ((java.sql.Timestamp) appeal.get("reviewed_at")).toLocalDateTime();
            assertThat(appeal).containsEntry("status", "REJECTED")
                    .containsEntry("review_comment", "의료 행위 대체를 암시합니다.");
            assertThat(((java.sql.Timestamp) appeal.get("grace_until")).toLocalDateTime()).isEqualTo(reviewedAt.plusDays(7));
            assertThat(row("SELECT resolution FROM post_suspension WHERE post_id = ?", postId))
                    .containsEntry("resolution", "DELETED_BY_REJECT");
            Map<String, Object> post = row("SELECT status, delete_reason, deleted_at, purge_at FROM post WHERE post_id = ?", postId);
            assertThat(post).containsEntry("status", "DELETED").containsEntry("delete_reason", "APPEAL_REJECTED");
            LocalDateTime deletedAt = ((java.sql.Timestamp) post.get("deleted_at")).toLocalDateTime();
            assertThat(deletedAt).isEqualTo(reviewedAt);
            assertThat(((java.sql.Timestamp) post.get("purge_at")).toLocalDateTime()).isEqualTo(deletedAt.plusMonths(6));

            JsonNode payload = latestPayload(postId, "APPEAL_REJECTED");
            assertThat(payload.get("appealId").asLong()).isEqualTo(appealId);
            assertThat(payload.get("comment").asText()).isEqualTo("의료 행위 대체를 암시합니다.");
            assertThat(payload.hasNonNull("graceUntil")).isTrue();
            assertThat(payload.get("retentionMonths").asInt()).isEqualTo(6);
        }

        @Test
        @DisplayName("[AR-03 · AR-04 · AR-05] 반려 사유 필수(상태 판정보다 먼저) · 501자 400 · 심사가 끝난 신청 409")
        void rejectRules() throws Exception {
            Long postId = publish(soyeon, "반려 규칙", LocalDateTime.now().minusDays(1));
            suspend(postId);
            Long appealId = appeal(postId);

            expectError(reject(appealId, "{}"), 400, "POST_APPEAL_REJECT_REASON_REQUIRED");
            expectError(reject(appealId, Map.of("comment", "")), 400, "POST_APPEAL_REJECT_REASON_REQUIRED");
            expectError(reject(appealId, Map.of("comment", "   ")), 400, "POST_APPEAL_REJECT_REASON_REQUIRED");
            expectError(reject(appealId, Map.of("comment", "가".repeat(501))), 400, "INVALID_INPUT");
            assertThat(row("SELECT status FROM post_appeal WHERE appeal_id = ?", appealId)).containsEntry("status", "PENDING");
            assertThat(row("SELECT status FROM post WHERE post_id = ?", postId)).containsEntry("status", "UNDER_REVIEW");
            assertThat(notificationCount(postId, "APPEAL_REJECTED")).isZero();

            approve(appealId, Map.of()).andExpect(status().isOk());
            expectError(reject(appealId, Map.of("comment", "반려")), 409, "POST_APPEAL_ALREADY_REVIEWED");
            expectError(reject(appealId, "{}"), 400, "POST_APPEAL_REJECT_REASON_REQUIRED");
        }

        @Test
        @DisplayName("[AR-06 · AR-07] 반려 뒤 스튜디오 목록 · 소비자에게서 사라지고, 유예 일수는 설정값을 따른다")
        void afterReject() throws Exception {
            int original = postProperties.getDownloadGraceDays();
            assertThat(original).isEqualTo(7);
            postProperties.setDownloadGraceDays(3);
            try {
                Long postId = publish(soyeon, "유예 3일", LocalDateTime.now().minusDays(2));
                rejectedPost(postId, "재게시할 수 없습니다.");

                Map<String, Object> appeal = row("SELECT reviewed_at, grace_until FROM post_appeal WHERE post_id = ?", postId);
                assertThat(((java.sql.Timestamp) appeal.get("grace_until")).toLocalDateTime())
                        .isEqualTo(((java.sql.Timestamp) appeal.get("reviewed_at")).toLocalDateTime().plusDays(3));
                mockMvc.perform(get("/v1/user/showrooms/posts/" + postId)).andExpect(status().isNotFound());
                String studioList = mockMvc.perform(get("/v1/creator/posts")
                                .header(HttpHeaders.AUTHORIZATION, creatorToken(soyeon)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertThat(postIds(objectMapper.readTree(studioList))).doesNotContain(postId);
            } finally {
                postProperties.setDownloadGraceDays(original);
            }
        }
    }

    // ------------------------------------------------------------------ 15. 신고 지목

    @Nested
    @DisplayName("신고 지목")
    class Flag {

        @Test
        @DisplayName("[FL-02 · FL-03 · FL-08] 이미 켜진 표식은 덮지 않고, 끄기는 멱등이며, 알림 · 이력이 남지 않는다")
        void flagRules() throws Exception {
            Long postId = publish(soyeon, "지목", LocalDateTime.now().minusDays(1));
            Long imageId = imageIds(postId).get(1);
            int timelineBefore = detail(postId).get("timeline").size();

            flag(postId, imageId, admin).andExpect(status().isNoContent());
            Map<String, Object> first = row("SELECT report_flagged_at, report_flagged_by FROM post_image WHERE post_image_id = ?", imageId);
            flag(postId, imageId, admin2).andExpect(status().isNoContent());
            assertThat(row("SELECT report_flagged_at, report_flagged_by FROM post_image WHERE post_image_id = ?", imageId))
                    .isEqualTo(first).containsEntry("report_flagged_by", operator.getId());

            assertThat(notificationCount(postId)).isZero();
            assertThat(detail(postId).get("timeline").size()).isEqualTo(timelineBefore);

            unflag(postId, imageId).andExpect(status().isNoContent());
            unflag(postId, imageId).andExpect(status().isNoContent());
            assertThat(row("SELECT report_flagged_at, report_flagged_by FROM post_image WHERE post_image_id = ?", imageId))
                    .containsEntry("report_flagged_at", null).containsEntry("report_flagged_by", null);
        }

        @Test
        @DisplayName("[FL-04 · FL-05] 없는 사진 · 없는 게시물은 404 · 노출 중지 · 심사 중 · 보관 중에도 표식을 남길 수 있다")
        void flagTargets() throws Exception {
            Long postId = publish(soyeon, "대상", LocalDateTime.now().minusDays(1));
            expectError(flag(postId, 999_999L, admin), 404, "POST_IMAGE_NOT_FOUND");
            expectError(flag(999_999L, imageIds(postId).get(0), admin), 404, "POST_NOT_FOUND");

            Long suspended = publish(soyeon, "중지", LocalDateTime.now().minusDays(1));
            suspend(suspended);
            Long review = publish(soyeon, "심사", LocalDateTime.now().minusDays(1));
            suspend(review);
            appeal(review);
            Long deleted = publish(harin, "보관", LocalDateTime.now().minusDays(1));
            rejectedPost(deleted, "재게시할 수 없습니다.");
            for (Long target : List.of(suspended, review, deleted)) {
                flag(target, imageIds(target).get(0), admin).andExpect(status().isNoContent());
                assertThat(detail(target).get("images").get(0).get("reportFlagged").asBoolean()).isTrue();
            }
        }

        private ResultActions flag(Long postId, Long imageId, String token) throws Exception {
            return mockMvc.perform(put(ADMIN_POSTS + "/" + postId + "/images/" + imageId + "/report-flag")
                    .header(HttpHeaders.AUTHORIZATION, token));
        }

        private ResultActions unflag(Long postId, Long imageId) throws Exception {
            return mockMvc.perform(delete(ADMIN_POSTS + "/" + postId + "/images/" + imageId + "/report-flag")
                    .header(HttpHeaders.AUTHORIZATION, admin));
        }
    }

    // ------------------------------------------------------------------ 16. 신고 대기열 · 이의 신청 목록

    @Nested
    @DisplayName("신고 대기열 · 이의 신청 목록")
    class Queues {

        @Test
        @DisplayName("[RQ-01 · RQ-02] 대기 신고는 오래된 순, 상태 없이는 처리분 포함 최신순 · 게시물 · 상태로 거른다")
        void reportQueueOrderAndFilter() throws Exception {
            LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
            Long a = publish(soyeon, "A", base.minusDays(1));
            Long b = publish(harin, "B", base.minusDays(1));
            Long a1 = report(a, "q-a1", "가나다", base);
            Long a2 = report(a, "q-a2", "라마바", base.plusHours(2));
            Long b1 = report(b, "q-b1", "사아자", base.plusHours(1));
            Long b2 = report(b, "q-b2", "아자차", base.plusHours(3));
            Long b3 = report(b, "q-b3", "자차카", base.plusHours(4));
            dismiss(b2).andExpect(status().isNoContent());
            dismiss(b3).andExpect(status().isNoContent());

            assertThat(reportIds(adminJson("/v1/admin/post-reports", "status", "PENDING"))).containsExactly(a1, b1, a2);
            assertThat(reportIds(adminJson("/v1/admin/post-reports"))).containsExactly(b3, b2, a2, b1, a1);
            assertThat(reportIds(adminJson("/v1/admin/post-reports", "postId", String.valueOf(b))))
                    .containsExactly(b3, b2, b1);
            assertThat(reportIds(adminJson("/v1/admin/post-reports", "postId", String.valueOf(b), "status", "DISMISSED")))
                    .containsExactly(b3, b2);
        }

        @Test
        @DisplayName("[RQ-03 · RQ-04 · RQ-05] 신고자 마스킹 · 조치 사유 코드 · 게시물 대기 건수 · 미리보기 40자 · 처리자 · 공구 게시물 표기")
        void reportQueueFields() throws Exception {
            Long postId = publish(soyeon, "가".repeat(60), LocalDateTime.now().minusDays(1));
            Long handled = report(postId, consumer("q-h", "오서린"), PostReportReason.ABUSE_HATE, null,
                    LocalDateTime.now().minusHours(3));
            dismiss(handled).andExpect(status().isNoContent());
            report(postId, consumer("q-1", "한지준"), PostReportReason.OTHER, "기타 사유", LocalDateTime.now().minusHours(2));
            report(postId, consumer("q-2", "임서현"), PostReportReason.SPAM, null, LocalDateTime.now().minusHours(1));
            Long groupBuy = groupBuyPost(harin, "공구 게시물");
            Long groupBuyReport = report(groupBuy, "q-gb", "박다은이", LocalDateTime.now().minusMinutes(30));

            String raw = adminGet("/v1/admin/post-reports").andReturn().getResponse().getContentAsString();
            JsonNode items = objectMapper.readTree(raw).get("content");
            assertThat(raw).doesNotContain("한지준").doesNotContain("오서린").doesNotContain("임서현");

            JsonNode other = null;
            JsonNode dismissed = null;
            JsonNode gb = null;
            for (JsonNode item : items) {
                if ("OTHER".equals(item.get("reasonCode").asText())) {
                    other = item;
                } else if (item.get("reportId").asLong() == handled) {
                    dismissed = item;
                } else if (item.get("reportId").asLong() == groupBuyReport) {
                    gb = item;
                }
            }
            assertThat(other).isNotNull();
            assertThat(other.get("reporterMaskedName").asText()).isEqualTo("한*준");
            assertThat(other.get("reporterUserId").isNumber()).isTrue();
            assertThat(other.get("reasonLabel").asText()).isEqualTo("기타");
            assertThat(other.get("suspensionReasonCode").asText()).isEqualTo("OTHER");
            assertThat(other.get("pendingCountOnPost").asLong()).isEqualTo(2);
            assertThat(other.get("contentPreview").asText()).isEqualTo("가".repeat(40) + "…");
            assertThat(other.get("postType").asText()).isEqualTo("GENERAL");
            assertThat(dismissed.get("status").asText()).isEqualTo("DISMISSED");
            assertThat(dismissed.get("handledBy").asLong()).isEqualTo(operator.getId());
            assertThat(dismissed.get("handledByName").asText()).isEqualTo("김운영");
            assertThat(dismissed.get("handledAt").isNull()).isFalse();
            assertThat(gb.get("postType").asText()).isEqualTo("GROUP_BUY");

            dismiss(groupBuyReport).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("[RQ-06 · RQ-07 · RQ-08] 1건 반려 — 두 번째는 409 · 없는 신고 404 · 남은 대기가 있으면 신고 접수 유지 · 조치로 닫힌 신고는 409")
        void dismissOne() throws Exception {
            Long postId = publish(soyeon, "1건 반려", LocalDateTime.now().minusDays(1));
            Long first = report(postId, "d-1", "가나다", LocalDateTime.now().minusHours(2));
            Long second = report(postId, "d-2", "라마바", LocalDateTime.now().minusHours(1));

            dismiss(first).andExpect(status().isNoContent());
            expectError(dismiss(first), 409, "POST_REPORT_ALREADY_HANDLED");
            expectError(dismiss(999_999L), 404, "POST_REPORT_NOT_FOUND");
            assertThat(rowOf(list(), postId).get("monitoringStatus").asText()).isEqualTo("REPORTED");
            assertThat(notificationCount(postId)).isZero();

            dismiss(second).andExpect(status().isNoContent());
            assertThat(rowOf(list(), postId).get("monitoringStatus").asText()).isEqualTo("PUBLISHED");

            Long suspended = publish(harin, "조치로 닫힘", LocalDateTime.now().minusDays(1));
            Long accepted = report(suspended, "d-3", "사아자", LocalDateTime.now().minusHours(1));
            suspend(suspended);
            expectError(dismiss(accepted), 409, "POST_REPORT_ALREADY_HANDLED");
        }

        @Test
        @DisplayName("[AQ-01 · AQ-02] 이의 신청 목록 — 전체는 최신순, 심사 대기는 오래된 순 · 심사 기한 · 처리자 · 원 조치 사유")
        void appealQueue() throws Exception {
            LocalDateTime base = LocalDateTime.now().minusDays(2).withNano(0);
            Long[] posts = new Long[4];
            Long[] appeals = new Long[4];
            for (int n = 0; n < 4; n++) {
                posts[n] = publish(n % 2 == 0 ? soyeon : harin, "이의 " + n, base.minusDays(1));
                suspend(posts[n]);
                appeals[n] = appeal(posts[n]);
                setAppealSubmittedAt(posts[n], base.plusHours(n));
            }
            approve(appeals[1], Map.of()).andExpect(status().isOk());
            reject(appeals[3], Map.of("comment", "재게시할 수 없습니다.")).andExpect(status().isOk());

            assertThat(appealIds(adminJson("/v1/admin/post-appeals"))).containsExactly(appeals[3], appeals[2], appeals[1], appeals[0]);
            assertThat(appealIds(adminJson("/v1/admin/post-appeals", "status", "PENDING")))
                    .containsExactly(appeals[0], appeals[2]);

            JsonNode rejected = adminJson("/v1/admin/post-appeals", "status", "REJECTED").get("content").get(0);
            assertThat(rejected.get("appealId").asLong()).isEqualTo(appeals[3]);
            assertThat(rejected.get("reviewDueAt").isNull()).isFalse();
            assertThat(rejected.get("reviewedAt").isNull()).isFalse();
            assertThat(rejected.get("reviewedByName").asText()).isEqualTo("김운영");
            assertThat(rejected.get("reviewComment").asText()).isEqualTo("재게시할 수 없습니다.");
            assertThat(rejected.get("graceUntil").isNull()).isFalse();
            assertThat(rejected.get("reasonCode").asText()).isEqualTo("MISLEADING_INFO");
            assertThat(rejected.get("showroomName").asText()).isEqualTo("유하린");
        }

        private List<Long> reportIds(JsonNode page) {
            List<Long> ids = new java.util.ArrayList<>();
            page.get("content").forEach(item -> ids.add(item.get("reportId").asLong()));
            return ids;
        }

        private List<Long> appealIds(JsonNode page) {
            List<Long> ids = new java.util.ArrayList<>();
            page.get("content").forEach(item -> ids.add(item.get("appealId").asLong()));
            return ids;
        }
    }

    // ------------------------------------------------------------------ 18. 원본 내려받기 기록

    @Nested
    @DisplayName("원본 내려받기 기록")
    class OriginalDownloads {

        @Test
        @DisplayName("[OD-01 · OD-02] 반려 유예 중 호출마다 1행 — 어느 반려의 유예인지 · 몇 장인지 남고 어드민 삭제 경위에 오래된 순으로 실린다")
        void recordsEachDownload() throws Exception {
            Long postId = publish(soyeon, "내려받기", LocalDateTime.now().minusDays(2), 3);
            Long appealId = rejectedPost(postId, "재게시할 수 없습니다.");
            String token = creatorToken(soyeon);

            downloadOriginals(postId, token).andExpect(status().isOk()).andExpect(jsonPath("$.graceUntil").isNotEmpty());
            downloadOriginals(postId, token).andExpect(status().isOk());

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT creator_id, appeal_id, image_count FROM post_original_download WHERE post_id = ? ORDER BY download_id",
                    postId);
            assertThat(rows).hasSize(2).allSatisfy(r -> {
                assertThat(((Number) r.get("creator_id")).longValue()).isEqualTo(soyeon.getId());
                assertThat(((Number) r.get("appeal_id")).longValue()).isEqualTo(appealId);
                assertThat(((Number) r.get("image_count")).intValue()).isEqualTo(3);
            });
            JsonNode downloads = detail(postId).get("deletion").get("originalDownloads");
            assertThat(downloads.size()).isEqualTo(2);
            assertThat(at(downloads.get(0).get("downloadedAt"))).isBeforeOrEqualTo(at(downloads.get(1).get("downloadedAt")));
        }

        @Test
        @DisplayName("[OD-03 ~ OD-06] 살아 있는 게시물은 기록하지 않고, 유예가 지났거나 반려 삭제가 아니면 403, 남의 게시물은 403이다")
        void notRecordedOtherwise() throws Exception {
            String token = creatorToken(soyeon);
            Long published = publish(soyeon, "게시중", LocalDateTime.now().minusDays(1));
            Long suspended = publish(soyeon, "중지", LocalDateTime.now().minusDays(1));
            suspend(suspended);
            downloadOriginals(published, token).andExpect(status().isOk());
            downloadOriginals(suspended, token).andExpect(status().isOk());

            Long overdue = publish(soyeon, "유예 경과", LocalDateTime.now().minusDays(2));
            rejectedPost(overdue, "재게시할 수 없습니다.");
            setGraceUntil(overdue, LocalDateTime.now().minusMinutes(1));
            expectError(downloadOriginals(overdue, token), 403, "POST_ORIGINAL_DOWNLOAD_UNAVAILABLE");

            Long expired = publish(soyeon, "기한 경과", LocalDateTime.now().minusDays(9));
            suspend(expired);
            expireSuspension(expired);
            Long self = publish(soyeon, "본인 삭제", LocalDateTime.now().minusDays(1));
            selfDelete(self).andExpect(status().is2xxSuccessful());
            expectError(downloadOriginals(expired, token), 403, "POST_ORIGINAL_DOWNLOAD_UNAVAILABLE");
            expectError(downloadOriginals(self, token), 403, "POST_ORIGINAL_DOWNLOAD_UNAVAILABLE");

            Long others = publish(harin, "남의 반려", LocalDateTime.now().minusDays(2));
            rejectedPost(others, "재게시할 수 없습니다.");
            expectError(downloadOriginals(others, token), 403, "POST_ACCESS_DENIED");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_original_download", Integer.class)).isZero();
        }
    }

    // ------------------------------------------------------------------ 19. 배치 연계

    @Nested
    @DisplayName("배치 연계")
    class Batches {

        @Test
        @DisplayName("[BT-01] 기한 경과 — 기한 경과 삭제 · 보관 만료 = +6개월 · 통지 · 요약이 노출 중지에서 종결로 옮긴다")
        void expire() throws Exception {
            Long postId = publish(soyeon, "기한 경과", LocalDateTime.now().minusDays(9));
            suspend(postId);
            assertThat(summary().get("suspended").asLong()).isEqualTo(1);

            expireSuspension(postId);

            Map<String, Object> post = row("SELECT status, delete_reason, deleted_at, purge_at FROM post WHERE post_id = ?", postId);
            assertThat(post).containsEntry("status", "DELETED").containsEntry("delete_reason", "APPEAL_EXPIRED");
            assertThat(((java.sql.Timestamp) post.get("purge_at")).toLocalDateTime())
                    .isEqualTo(((java.sql.Timestamp) post.get("deleted_at")).toLocalDateTime().plusMonths(6));
            assertThat(row("SELECT resolution FROM post_suspension WHERE post_id = ?", postId))
                    .containsEntry("resolution", "DELETED_BY_EXPIRE");
            assertThat(notificationCount(postId, "DELETED_BY_EXPIRE")).isEqualTo(1);
            JsonNode summary = summary();
            assertThat(summary.get("suspended").asLong()).isZero();
            assertThat(summary.get("closed").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("[BT-02 · BT-03] 기한과 같은 시각 · 조치 취소된 조치 · 이의가 걸린 조치는 만료 대상이 아니다")
        void notExpired() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            Long boundary = publish(soyeon, "경계", LocalDateTime.now().minusDays(9));
            suspend(boundary);
            setAppealDeadline(boundary, now);
            Long canceled = publish(soyeon, "취소", LocalDateTime.now().minusDays(9));
            suspend(canceled);
            setAppealDeadline(canceled, now.minusDays(1));
            cancelSuspension(canceled, null).andExpect(status().isOk());
            Long review = publish(harin, "심사 중", LocalDateTime.now().minusDays(9));
            suspend(review);
            appeal(review);
            setAppealDeadline(review, now.minusDays(1));

            postLifecycleService.expireOverdueSuspensions(now, 100);

            assertThat(row("SELECT status FROM post WHERE post_id = ?", boundary)).containsEntry("status", "SUSPENDED");
            assertThat(row("SELECT status FROM post WHERE post_id = ?", canceled)).containsEntry("status", "PUBLISHED");
            assertThat(row("SELECT status FROM post WHERE post_id = ?", review)).containsEntry("status", "UNDER_REVIEW");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_notification_log WHERE event_type = 'DELETED_BY_EXPIRE'",
                    Integer.class)).isZero();
        }

        @Test
        @DisplayName("[BT-04 · BT-05 · OD-07 · SC-04 · DT-05] 파기 — 드라이런은 아무것도 지우지 않고, 실제 파기 뒤에는 상세 404 · 알림 · 내려받기 이력 · 작성자 조치 이력이 남는다")
        void purge() throws Exception {
            // 사진이 있으면 파기가 S3를 부른다 — 이 테스트는 사진 없는 게시물로 DB 경로만 본다
            Long postId = publishWithoutImages(soyeon, "파기", LocalDateTime.now().minusDays(9));
            rejectedPost(postId, "재게시할 수 없습니다.");
            downloadOriginals(postId, creatorToken(soyeon)).andExpect(status().isOk());
            Long viewer = publish(soyeon, "남는 게시물", LocalDateTime.now().minusDays(1));
            JsonNode authorBefore = detail(viewer).get("author");
            jdbc.update("UPDATE post SET purge_at = ? WHERE post_id = ?", LocalDateTime.now().minusMinutes(1), postId);
            int notices = notificationCount(postId);

            assertThat(postPurgeService.purgeExpired(LocalDateTime.now())).isZero();
            adminGet(ADMIN_POSTS + "/" + postId).andExpect(status().isOk());

            boolean original = postProperties.isPurgeEnabled();
            postProperties.setPurgeEnabled(true);
            try {
                assertThat(postPurgeService.purgeExpired(LocalDateTime.now())).isEqualTo(1);
            } finally {
                postProperties.setPurgeEnabled(original);
            }

            expectError(adminGet(ADMIN_POSTS + "/" + postId), 404, "POST_NOT_FOUND");
            assertThat(postIds(list())).doesNotContain(postId);
            assertThat(summary().get("closed").asLong()).isZero();
            assertThat(notificationCount(postId)).isEqualTo(notices);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_original_download WHERE post_id = ?", Integer.class, postId))
                    .isEqualTo(1);
            assertThat(detail(viewer).get("author")).isEqualTo(authorBefore);
            assertThat(authorBefore.get("sanctionDeleteCount").asLong()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ 20-1 · 20-3. 공구 게시물 · 앱 사유 코드

    @Nested
    @DisplayName("공구 게시물 차단 · 앱 사유 코드")
    class GroupBuyAndReportCodes {

        @Test
        @DisplayName("[GB-03 · GB-04 · RC-09 · CC-11 · FL-07] 공구 게시물 조치는 전부 409 — 근거 규정보다 먼저, 빈 사유 검증보다는 나중")
        void groupBuyActionsBlocked() throws Exception {
            Long postId = groupBuyPost(soyeon, "공구");
            report(postId, "gb-r", "한지준", LocalDateTime.now().minusHours(1));

            for (ResultActions result : List.of(
                    suspendRequest(postId, body("reasonCode", "SPAM", "policyClause", "ARTICLE_5")),
                    suspendRequest(postId, body("reasonCode", "SPAM")),
                    closeReports(postId),
                    cancelSuspension(postId, null),
                    mockMvc.perform(put(ADMIN_POSTS + "/" + postId + "/images/1/report-flag")
                            .header(HttpHeaders.AUTHORIZATION, admin)),
                    mockMvc.perform(delete(ADMIN_POSTS + "/" + postId + "/images/1/report-flag")
                            .header(HttpHeaders.AUTHORIZATION, admin)))) {
                result.andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("POST_NOT_EDITABLE"))
                        .andExpect(jsonPath("$.message").value("공구 게시물은 공구 관리에서 노출 중지해 주세요."));
            }
            expectError(suspendRequest(postId, body("policyClause", "ARTICLE_5")), 400, "INVALID_INPUT");
            assertThat(reportStatuses(postId)).containsExactly("PENDING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_suspension", Integer.class)).isZero();
        }

        @Test
        @DisplayName("[MG-05 · MG-06] 앱 신고는 6종만 받는다 — 옛 코드 · 운영자 전용 코드 400 · 기타는 상세 필수")
        void appReportCodes() throws Exception {
            Long postId = publish(soyeon, "앱 신고", LocalDateTime.now().minusDays(1));
            Users reporter = consumer("mg-user", "한지준");

            for (String code : List.of("MISLEADING_AD", "MEDICAL_CLAIM", "PERSONAL_INFO", "VIOLENCE_HATE")) {
                expectError(appReport(postId, reporter, body("reasonCode", code)), 400, "INVALID_INPUT");
            }
            expectError(appReport(postId, reporter, body("reasonCode", "OTHER")), 400, "POST_REPORT_DETAIL_REQUIRED");
            assertThat(reportStatuses(postId)).isEmpty();
            appReport(postId, reporter, body("reasonCode", "OTHER", "reasonDetail", "저작권 침해 의심"))
                    .andExpect(status().isNoContent());
            assertThat(row("SELECT reason_code, reason_detail FROM post_report WHERE post_id = ?", postId))
                    .containsEntry("reason_code", "OTHER").containsEntry("reason_detail", "저작권 침해 의심");
        }
    }

    // ------------------------------------------------------------------ 21. 통합 시나리오

    @Nested
    @DisplayName("통합 시나리오")
    class EndToEnd {

        @Test
        @DisplayName("[E2E-1] 신고 → 종결 → 재신고 → 노출 중지 → 조치 취소")
        void reportCloseSuspendCancel() throws Exception {
            Long postId = publish(soyeon, "E2E-1", LocalDateTime.now().minusDays(2));
            Users a = consumer("e1-a", "한지준");
            Users b = consumer("e1-b", "오서린");
            Users c = consumer("e1-c", "임서현");

            appReport(postId, a, body("reasonCode", "MISLEADING_INFO")).andExpect(status().isNoContent());
            appReport(postId, b, body("reasonCode", "ABUSE_HATE")).andExpect(status().isNoContent());
            assertRow(postId, "REPORTED", 2);
            assertThat(summary().get("actionRequired").asLong()).isEqualTo(1);
            assertThat(texts(detail(postId).get("timeline"), "label")).contains("신고 접수 (2건째)");

            closeReports(postId).andExpect(status().isOk());
            assertRow(postId, "PUBLISHED", 0);
            assertThat(detail(postId).get("reports").get("totalCount").asInt()).isEqualTo(2);
            assertThat(notificationCount(postId)).isZero();

            appReport(postId, a, body("reasonCode", "MISLEADING_INFO")).andExpect(status().isConflict());
            appReport(postId, c, body("reasonCode", "ABUSE_HATE")).andExpect(status().isNoContent());
            assertRow(postId, "REPORTED", 1);
            assertThat(texts(detail(postId).get("timeline"), "label")).contains("신고 접수 (3건째)");

            suspendRequest(postId, body("reasonCode", "ABUSE_HATE", "policyClause", "ARTICLE_4_1"))
                    .andExpect(status().isOk());
            assertRow(postId, "SUSPENDED", 1);
            assertThat(reportStatuses(postId)).containsExactly("DISMISSED", "DISMISSED", "ACCEPTED");
            mockMvc.perform(get("/v1/user/showrooms/posts/" + postId)).andExpect(status().isNotFound());
            assertThat(detail(postId).get("author").get("suspensionCount").asLong()).isEqualTo(1);

            cancelSuspension(postId, Map.of("note", "개인 사용감 표현")).andExpect(status().isOk());
            JsonNode body = detail(postId);
            assertThat(body.get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            assertThat(body.get("suspensions").get(0).get("resolution").asText()).isEqualTo("CANCELED_BY_ADMIN");
            assertThat(body.get("author").get("suspensionCount").asLong()).isZero();
            assertThat(notificationCount(postId, "SUSPENSION_CANCELED")).isEqualTo(1);
            mockMvc.perform(get("/v1/user/showrooms/posts/" + postId)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("[E2E-2] 노출 중지 → 이의 → 승인 → 다시 중지(이의 불가) → 기한 만료")
        void suspendAppealApproveResuspendExpire() throws Exception {
            Long postId = publish(soyeon, "E2E-2", LocalDateTime.now().minusDays(9));
            like(consumer("e2-like", null), postId, LocalDateTime.now().minusDays(5));
            suspend(postId);
            Long appealId = appeal(postId);

            JsonNode row = rowOf(list(), postId);
            assertThat(row.get("monitoringStatus").asText()).isEqualTo("UNDER_REVIEW");
            assertThat(row.get("appealDeadline").isNull()).isTrue();
            assertThat(row.get("reviewDueAt").isNull()).isFalse();
            assertThat(summary().get("underReview").asLong()).isEqualTo(1);

            approve(appealId, Map.of()).andExpect(status().isOk());
            assertThat(detail(postId).get("metrics").get("likes").asLong()).isEqualTo(1);
            assertThat(notificationCount(postId, "APPEAL_APPROVED")).isEqualTo(1);

            jdbc.update("UPDATE post_suspension SET suspended_at = suspended_at - INTERVAL '1' DAY WHERE post_id = ?", postId);
            suspend(postId);
            Long secondSuspension = jdbc.queryForObject("SELECT MAX(suspension_id) FROM post_suspension WHERE post_id = ?",
                    Long.class, postId);
            mockMvc.perform(post("/v1/creator/posts/" + postId + "/appeal")
                            .header(HttpHeaders.AUTHORIZATION, ownerTokenOf(postId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("content", "다시 신청합니다."))))
                    .andExpect(status().isConflict());

            expireSuspension(postId);
            JsonNode body = detail(postId);
            assertThat(body.get("monitoringStatus").asText()).isEqualTo("CLOSED");
            assertThat(body.get("deletion").get("type").asText()).isEqualTo("APPEAL_EXPIRED");
            assertThat(body.get("deletion").get("originSuspensionId").asLong()).isEqualTo(secondSuspension);
            assertThat(body.get("author").get("suspensionCount").asLong()).isEqualTo(2);
            assertThat(body.get("author").get("sanctionDeleteCount").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("[E2E-3] 노출 중지 → 이의 → 반려(사유 필수) → 유예 중 내려받기 → 유예 경과 403")
        void rejectAndGrace() throws Exception {
            Long postId = publish(soyeon, "E2E-3", LocalDateTime.now().minusDays(3));
            suspend(postId);
            Long appealId = appeal(postId);

            expectError(reject(appealId, "{}"), 400, "POST_APPEAL_REJECT_REASON_REQUIRED");
            reject(appealId, Map.of("comment", "재게시할 수 없습니다.")).andExpect(status().isOk());
            String token = creatorToken(soyeon);
            downloadOriginals(postId, token).andExpect(status().isOk());

            JsonNode body = detail(postId);
            assertThat(body.get("deletion").get("originalDownloads").size()).isEqualTo(1);
            assertThat(texts(body.get("timeline"), "label")).contains("원본 내려받기 (2장)");

            setGraceUntil(postId, LocalDateTime.now().minusMinutes(1));
            expectError(downloadOriginals(postId, token), 403, "POST_ORIGINAL_DOWNLOAD_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_original_download WHERE post_id = ?",
                    Integer.class, postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("[E2E-4] 노출 중지 → 다음날 작성자 삭제 → 만료 배치는 아무 일도 하지 않는다")
        void suspendThenSelfDelete() throws Exception {
            Long postId = publish(soyeon, "E2E-4", LocalDateTime.now().minusDays(3));
            report(postId, "e4", "한지준", LocalDateTime.now().minusDays(2));
            suspend(postId);
            setSuspendedAt(postId, LocalDateTime.now().minusDays(1));
            selfDelete(postId).andExpect(status().is2xxSuccessful());
            int notices = notificationCount(postId);

            setAppealDeadline(postId, LocalDateTime.now().minusMinutes(1));
            postLifecycleService.expireOverdueSuspensions(LocalDateTime.now(), 100);

            JsonNode body = detail(postId);
            assertThat(body.get("deletion").get("type").asText()).isEqualTo("SELF");
            assertThat(body.get("deletion").get("daysAfterSuspension").asLong()).isEqualTo(1);
            assertThat(body.get("suspensions").get(0).get("resolution").asText()).isEqualTo("DELETED_BY_SELF");
            assertThat(reportStatuses(postId)).containsExactly("ACCEPTED");
            assertThat(notificationCount(postId)).isEqualTo(notices);
            assertThat(body.get("author").get("suspensionCount").asLong()).isEqualTo(1);
            assertThat(body.get("author").get("sanctionDeleteCount").asLong()).isZero();
            expectError(downloadOriginals(postId, creatorToken(soyeon)), 403, "POST_ORIGINAL_DOWNLOAD_UNAVAILABLE");
        }

        private void assertRow(Long postId, String monitoringStatus, long reportCount) throws Exception {
            JsonNode row = rowOf(list(), postId);
            assertThat(row.get("monitoringStatus").asText()).isEqualTo(monitoringStatus);
            assertThat(row.get("reportCount").asLong()).isEqualTo(reportCount);
        }
    }

    // ------------------------------------------------------------------ 보조

    private ResultActions appReport(Long postId, Users reporter, Object body) throws Exception {
        return mockMvc.perform(post("/v1/user/posts/" + postId + "/reports")
                .header(HttpHeaders.AUTHORIZATION, userToken(reporter))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    private void expectError(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
