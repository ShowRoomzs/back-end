package showroomz.api.admin.post;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostReportReason;
import showroomz.global.utils.NicknameMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [통합] 어드민 일반 게시물 — 상세 · 상태별 화면 · 지표 · 처리 이력 · 작성자 조치 이력 (54 테스트 시나리오 5 ~ 9절).
 *
 * <p>상세는 한 번의 호출로 모든 카드의 재료를 내린다. 카드 배치는 FE 몫이라 여기서는 <b>재료가 맞는지</b>만 본다.
 */
@DisplayName("[통합] 어드민 일반 게시물 — 상세 시나리오")
class AdminPostDetailScenarioIntegrationTest extends AdminPostScenarioSupport {

    // ------------------------------------------------------------------ 5-1 · 5-2. 존재 · 원문 · 작성자 · 사진

    @Nested
    @DisplayName("존재 · 원문 · 작성자 · 사진")
    class Basics {

        @Test
        @DisplayName("[DT-02 · DT-03 · DT-04] 작성중은 직접 열면 보이고, 없는 id · 공구 게시물은 404다")
        void existence() throws Exception {
            Long draft = draft(soyeon, "임시저장");
            Long groupBuy = groupBuyPost(soyeon, "공구 게시물");

            JsonNode body = detail(draft);
            assertThat(body.get("monitoringStatus").asText()).isEqualTo("DRAFT");
            assertThat(body.get("monitoringStatusLabel").asText()).isEqualTo("작성중");

            adminGet(ADMIN_POSTS + "/999999").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
            adminGet(ADMIN_POSTS + "/" + groupBuy).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }

        @Test
        @DisplayName("[DT-06 · DT-07] 본문은 자르지 않은 원문이고 비율은 표준 이름 · 오차 0.005 · 그 밖은 N:1로 적는다")
        void contentAndAspectRatio() throws Exception {
            String longContent = "가".repeat(2000);
            Long postId = publish(soyeon, longContent, LocalDateTime.now().minusDays(1));
            assertThat(detail(postId).get("content").asText()).isEqualTo(longContent);

            Map<String, String> expected = Map.of("1.0000", "1:1", "1.3333", "4:3", "1.9100", "1.91:1",
                    "0.8040", "4:5", "0.8500", "0.85:1");
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                Long ratioPost = publishWithRatio(harin, entry.getKey());
                assertThat(detail(ratioPost).get("aspectRatioLabel").asText()).as(entry.getKey())
                        .isEqualTo(entry.getValue());
            }
        }

        @Test
        @DisplayName("[DT-08] 작성자 팔로워 수는 앱 내 팔로우 수다 — SNS 팔로워(1000)가 아니다")
        void authorFollowerCountIsInApp() throws Exception {
            Long postId = publish(soyeon, "팔로워", LocalDateTime.now().minusDays(1));
            for (int n = 0; n < 3; n++) {
                creatorFollowRepository.save(new CreatorFollow(consumer("follower-" + n, null), soyeon));
            }

            JsonNode author = detail(postId).get("author");
            assertThat(author.get("followerCount").asLong()).isEqualTo(3);
            assertThat(author.get("showroomId").asLong()).isEqualTo(soyeon.getId());
            assertThat(author.get("showroomName").asText()).isEqualTo("김소연");
            assertThat(author.get("userId").asLong()).isEqualTo(inTransaction(
                    () -> creatorRepository.findById(soyeon.getId()).orElseThrow().getUser().getId()));
        }

        @Test
        @DisplayName("[DT-09 · DT-10] 사진은 14장 전부 순서대로 · 원본 뷰어 메타(파일명은 쿼리를 뗀 원본 URL 마지막 조각)")
        void imagesAndViewerMeta() throws Exception {
            Long postId = publish(soyeon, "14장", LocalDateTime.now().minusDays(1), 14);
            Long signed = inTransaction(() -> {
                Post post = Post.published(harin, "서명 URL", new BigDecimal("0.8000"), LocalDateTime.now().minusDays(1));
                post.replaceImages(List.of(new PostImage(CDN + "342/0.jpg",
                        CDN + "342/IMG_3300.jpg?X-Amz-Signature=abc", 1080, 1350, 812_340)));
                return postRepository.save(post).getId();
            });

            JsonNode images = detail(postId).get("images");
            assertThat(images.size()).isEqualTo(14);
            for (int i = 0; i < 14; i++) {
                assertThat(images.get(i).get("sortOrder").asInt()).isEqualTo(i);
            }

            JsonNode image = detail(signed).get("images").get(0);
            assertThat(image.get("fileName").asText()).isEqualTo("IMG_3300.jpg");
            assertThat(image.get("originalUrl").asText()).endsWith("?X-Amz-Signature=abc");
            assertThat(image.get("width").asInt()).isEqualTo(1080);
            assertThat(image.get("height").asInt()).isEqualTo(1350);
            assertThat(image.get("fileSize").asInt()).isEqualTo(812_340);
            assertThat(image.get("uploadedAt").isNull()).isFalse();
            assertThat(image.get("reportFlagged").asBoolean()).isFalse();
            assertThat(image.get("reportFlaggedByName").isNull()).isTrue();
        }
    }

    // ------------------------------------------------------------------ 5-3. 신고 내역 카드

    @Nested
    @DisplayName("신고 내역 카드")
    class ReportCard {

        @Test
        @DisplayName("[DT-12 · DT-13 · DT-14] 접수 순 · 사유별 건수(많은 순) · 최초 대기 · 신고자는 가운데를 가리고 실명은 어디에도 없다")
        void reportsCard() throws Exception {
            Long postId = publish(soyeon, "효과 없으면 환불", LocalDateTime.now().minusDays(2));
            LocalDateTime base = LocalDateTime.now().minusHours(12).withNano(0);
            Users four = consumer("r-four", "박다은이");
            Users two = consumer("r-two", "한결");
            Users noName = consumer("r-nick-only", null);
            Users three = consumer("r-three", "한지준");
            // 접수 시각과 저장 순서를 일부러 엇갈리게 둔다
            report(postId, four, PostReportReason.MISLEADING_INFO, null, base.plusHours(2));
            report(postId, two, PostReportReason.OTHER, "3번째 사진 문구", base.plusHours(3));
            report(postId, noName, PostReportReason.SPAM, null, base.plusHours(1));
            report(postId, three, PostReportReason.MISLEADING_INFO, "효과 단정", base);

            String raw = adminGet(ADMIN_POSTS + "/" + postId).andReturn().getResponse().getContentAsString();
            JsonNode reports = objectMapper.readTree(raw).get("reports");

            assertThat(reports.get("pendingCount").asInt()).isEqualTo(4);
            assertThat(reports.get("totalCount").asInt()).isEqualTo(4);
            assertThat(at(reports.get("firstPendingAt"))).isEqualTo(base);
            assertThat(texts(reports.get("items"), "reporterMaskedName"))
                    .containsExactly("한*준", NicknameMasker.mask("r-nick-only"), "박**이", "한*");
            assertThat(reports.get("items").get(0).get("reporterUserId").asLong()).isEqualTo(three.getId());
            assertThat(texts(reports.get("countsByReason"), "reasonCode"))
                    .containsExactly("MISLEADING_INFO", "SPAM", "OTHER");
            assertThat(reports.get("countsByReason").get(0).get("count").asInt()).isEqualTo(2);
            assertThat(reports.get("countsByReason").get(0).get("reasonLabel").asText()).isEqualTo("허위 · 과장 정보");
            for (String realName : List.of("박다은이", "한결", "한지준")) {
                assertThat(raw).doesNotContain(realName);
            }
        }

        @Test
        @DisplayName("[DT-15] 노출 중지 뒤 — 신고는 조치로 이어짐(ACCEPTED) · 처리자 · 메타는 ACCEPTED 기준")
        void afterSuspension() throws Exception {
            Long postId = threeReports();
            suspend(postId);

            JsonNode reports = detail(postId).get("reports");
            assertThat(reports.get("pendingCount").asInt()).isZero();
            assertThat(reports.get("totalCount").asInt()).isEqualTo(3);
            assertThat(reports.get("firstPendingAt").isNull()).isTrue();
            assertThat(texts(reports.get("items"), "status")).containsOnly("ACCEPTED");
            assertThat(texts(reports.get("items"), "handledByName")).containsOnly("김운영");
            assertThat(texts(reports.get("items"), "handledAt")).doesNotContainNull();
            assertThat(texts(reports.get("countsByReason"), "reasonCode")).containsExactly("MISLEADING_INFO", "OTHER");
        }

        @Test
        @DisplayName("[DT-16 · DT-17] 신고 종결분은 이력에 남되 메타에서 빠진다 — 대기가 있으면 대기 기준")
        void dismissedLeaveMeta() throws Exception {
            Long closed = threeReports();
            closeReports(closed).andExpect(status().isOk());
            JsonNode reports = detail(closed).get("reports");
            assertThat(reports.get("totalCount").asInt()).isEqualTo(3);
            assertThat(texts(reports.get("items"), "status")).containsOnly("DISMISSED");
            assertThat(reports.get("countsByReason").size()).isZero();

            Long mixed = publish(harin, "대기 2 · 반려 1", LocalDateTime.now().minusDays(1));
            Long dismissedOne = report(mixed, consumer("x-1", "가나다"), PostReportReason.SPAM, null,
                    LocalDateTime.now().minusHours(3));
            report(mixed, consumer("x-2", "라마바"), PostReportReason.ABUSE_HATE, null, LocalDateTime.now().minusHours(2));
            report(mixed, consumer("x-3", "사아자"), PostReportReason.ABUSE_HATE, null, LocalDateTime.now().minusHours(1));
            dismiss(dismissedOne).andExpect(status().isNoContent());

            JsonNode meta = detail(mixed).get("reports").get("countsByReason");
            assertThat(texts(meta, "reasonCode")).containsExactly("ABUSE_HATE");
            assertThat(meta.get(0).get("count").asInt()).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------ 5-4. 조치 · 이의 신청 카드

    @Nested
    @DisplayName("조치 · 이의 신청 카드")
    class SuspensionAndAppealCards {

        @Test
        @DisplayName("[DT-18 · DT-19] 조치 카드 — 기한 = 조치 + 7일 · 알림 시각 · FCM 미설정이면 미발송")
        void suspensionCard() throws Exception {
            Long postId = publish(soyeon, "조치 카드", LocalDateTime.now().minusDays(1));
            suspendRequest(postId, body("reasonCode", "ABUSE_HATE", "policyClause", "ARTICLE_4_1",
                    "reasonDetail", "욕설 표현이 있습니다.")).andExpect(status().isOk());

            JsonNode card = detail(postId).get("suspensions").get(0);
            assertThat(card.get("open").asBoolean()).isTrue();
            assertThat(card.get("reasonCode").asText()).isEqualTo("ABUSE_HATE");
            assertThat(card.get("reasonLabel").asText()).isEqualTo("욕설 · 혐오 표현");
            assertThat(card.get("reasonDetail").asText()).isEqualTo("욕설 표현이 있습니다.");
            assertThat(card.get("policyClauseCode").asText()).isEqualTo("ARTICLE_4_1");
            assertThat(card.get("policyRef").asText()).isEqualTo("서비스 운영정책 — 커뮤니티 장 4조 1항 — 욕설 · 혐오 표현");
            assertThat(card.get("suspendedBy").asLong()).isEqualTo(operator.getId());
            assertThat(at(card.get("appealDeadline"))).isEqualTo(at(card.get("suspendedAt")).plusDays(7));
            assertThat(card.get("deadlinePaused").asBoolean()).isFalse();
            assertThat(card.get("resolution").isNull()).isTrue();
            assertThat(at(card.get("notifiedAt"))).isCloseTo(at(card.get("suspendedAt")), within(5, ChronoUnit.SECONDS));
            assertThat(card.get("notificationDelivered").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("[DT-18a] 근거 규정 라벨이 바뀌어도 과거 조치의 문구는 그 시점 스냅샷 그대로다(55 계획서 A5 · D20)")
        void policyLabelChangeIsNotRetroactive() throws Exception {
            Long postId = publish(soyeon, "옛 라벨 조치", LocalDateTime.now().minusDays(1));
            suspend(postId);
            // rev.7 이전 라벨로 굳은 조치 — 코드는 같고 문구만 옛 머리말이다
            jdbc.update("UPDATE post_suspension SET policy_ref = ? WHERE post_id = ?",
                    "커뮤니티 운영정책 3조 2항 — 효과 · 가격 오인 표현", postId);

            JsonNode body = detail(postId);
            assertThat(body.get("suspensions").get(0).get("policyClauseCode").asText()).isEqualTo("ARTICLE_3_2");
            assertThat(body.get("suspensions").get(0).get("policyRef").asText())
                    .isEqualTo("커뮤니티 운영정책 3조 2항 — 효과 · 가격 오인 표현");
            JsonNode suspended = null;
            for (JsonNode item : body.get("timeline")) {
                if ("SUSPENDED".equals(item.get("type").asText())) {
                    suspended = item;
                }
            }
            assertThat(suspended).isNotNull();
            assertThat(suspended.get("detail").asText()).isEqualTo("커뮤니티 운영정책 3조 2항 — 효과 · 가격 오인 표현");
        }

        @Test
        @DisplayName("[DT-20] 승인 뒤 다시 중지 — 조치 2건 최신 우선 · 이의가 걸린 조치만 기한 정지 · 알림은 자기 조치와 짝짓는다")
        void resuspended() throws Exception {
            Long postId = publish(soyeon, "다시 중지", LocalDateTime.now().minusDays(3));
            suspend(postId);
            Long firstSuspension = jdbc.queryForObject("SELECT suspension_id FROM post_suspension WHERE post_id = ?",
                    Long.class, postId);
            Long appealId = appeal(postId);
            approve(appealId, Map.of()).andExpect(status().isOk());
            jdbc.update("UPDATE post_notification_log SET sent_at = sent_at - INTERVAL '1' DAY WHERE post_id = ?", postId);
            jdbc.update("UPDATE post_suspension SET suspended_at = suspended_at - INTERVAL '1' DAY WHERE post_id = ?", postId);
            suspend(postId);

            JsonNode suspensions = detail(postId).get("suspensions");
            assertThat(suspensions.size()).isEqualTo(2);
            JsonNode latest = suspensions.get(0);
            JsonNode previous = suspensions.get(1);
            assertThat(previous.get("suspensionId").asLong()).isEqualTo(firstSuspension);
            assertThat(latest.get("open").asBoolean()).isTrue();
            assertThat(latest.get("deadlinePaused").asBoolean()).isFalse();
            assertThat(previous.get("open").asBoolean()).isFalse();
            assertThat(previous.get("resolution").asText()).isEqualTo("REPUBLISHED");
            assertThat(previous.get("deadlinePaused").asBoolean()).isTrue();
            assertThat(at(latest.get("notifiedAt"))).isCloseTo(at(latest.get("suspendedAt")), within(5, ChronoUnit.SECONDS));
            assertThat(at(previous.get("notifiedAt"))).isCloseTo(at(previous.get("suspendedAt")), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("[DT-22] 이의 신청 카드 — 없음 / 심사 중 / 승인")
        void appealCard() throws Exception {
            Long postId = publish(soyeon, "이의", LocalDateTime.now().minusDays(1));
            suspend(postId);
            assertThat(detail(postId).get("appeal").isNull()).isTrue();

            Long appealId = appeal(postId);
            JsonNode pending = detail(postId).get("appeal");
            assertThat(pending.get("appealId").asLong()).isEqualTo(appealId);
            assertThat(pending.get("status").asText()).isEqualTo("PENDING");
            assertThat(pending.get("content").asText()).isEqualTo("개인적인 사용감을 적은 것입니다.");
            assertThat(pending.get("reviewDueAt").isNull()).isFalse();
            assertThat(pending.get("reviewedAt").isNull()).isTrue();

            approve(appealId, Map.of("comment", "개인 사용감으로 판단")).andExpect(status().isOk());
            JsonNode approved = detail(postId).get("appeal");
            assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
            assertThat(approved.get("reviewedByName").asText()).isEqualTo("김운영");
            assertThat(approved.get("reviewComment").asText()).isEqualTo("개인 사용감으로 판단");
            assertThat(approved.get("graceUntil").isNull()).isTrue();
        }

        @Test
        @DisplayName("[DT-23] 운영자가 여럿이면 자리마다 맞는 이름이 붙는다")
        void operatorNamesPerSlot() throws Exception {
            Long postId = publish(soyeon, "운영자 여럿", LocalDateTime.now().minusDays(1));
            report(postId, "n1", "한지준", LocalDateTime.now().minusHours(2));
            Long imageId = imageIds(postId).get(0);
            mockMvc.perform(put(ADMIN_POSTS + "/" + postId + "/images/" + imageId + "/report-flag")
                    .header(HttpHeaders.AUTHORIZATION, admin2)).andExpect(status().isNoContent());
            suspend(postId);
            Long appealId = appeal(postId);
            adminPost("/v1/admin/post-appeals/" + appealId + "/approve", admin2, Map.of()).andExpect(status().isOk());

            JsonNode body = detail(postId);
            assertThat(body.get("images").get(0).get("reportFlaggedByName").asText()).isEqualTo("박운영");
            assertThat(body.get("reports").get("items").get(0).get("handledByName").asText()).isEqualTo("김운영");
            assertThat(body.get("suspensions").get(0).get("suspendedByName").asText()).isEqualTo("김운영");
            assertThat(body.get("appeal").get("reviewedByName").asText()).isEqualTo("박운영");
        }
    }

    // ------------------------------------------------------------------ 6. 상태별 화면

    @Nested
    @DisplayName("상태별 화면")
    class States {

        @Test
        @DisplayName("[ST-01] C2 게시중 — 신고 · 조치 · 이의 · 삭제 경위 없음 · 지표 동결 없음")
        void published() throws Exception {
            Long postId = publish(soyeon, "게시중", LocalDateTime.now().minusDays(1));

            JsonNode body = detail(postId);
            assertThat(body.get("monitoringStatus").asText()).isEqualTo("PUBLISHED");
            assertThat(body.get("reports").get("items").size()).isZero();
            assertThat(body.get("suspensions").size()).isZero();
            assertThat(body.get("appeal").isNull()).isTrue();
            assertThat(body.get("deletion").isNull()).isTrue();
            assertThat(body.get("metrics").get("frozenAt").isNull()).isTrue();
        }

        @Test
        @DisplayName("[ST-02] C3 신고 접수 — 14장 · 신고 3건(사유 2종) · 3번째 사진 지목")
        void reported() throws Exception {
            Long postId = publish(soyeon, "14장 신고", LocalDateTime.now().minusDays(1), 14);
            report(postId, consumer("c3-1", "한지준"), PostReportReason.MISLEADING_INFO, null, LocalDateTime.now().minusHours(3));
            report(postId, consumer("c3-2", "오서린"), PostReportReason.MISLEADING_INFO, null, LocalDateTime.now().minusHours(2));
            report(postId, consumer("c3-3", "임서현"), PostReportReason.OTHER, "3번째 사진", LocalDateTime.now().minusHours(1));
            Long third = imageIds(postId).get(2);
            mockMvc.perform(put(ADMIN_POSTS + "/" + postId + "/images/" + third + "/report-flag")
                    .header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNoContent());

            JsonNode body = detail(postId);
            assertThat(body.get("monitoringStatus").asText()).isEqualTo("REPORTED");
            assertThat(body.get("images").size()).isEqualTo(14);
            assertThat(body.get("images").get(2).get("reportFlagged").asBoolean()).isTrue();
            assertThat(body.get("images").get(2).get("reportFlaggedByName").asText()).isEqualTo("김운영");
            assertThat(body.get("images").get(1).get("reportFlagged").asBoolean()).isFalse();
            assertThat(texts(body.get("reports").get("countsByReason"), "reasonCode"))
                    .containsExactly("MISLEADING_INFO", "OTHER");
        }

        @Test
        @DisplayName("[ST-03 · ST-04] C5 노출 중지 · C6 심사 중 — 지표는 조치 시각에 동결 · 심사 중에는 기한 정지")
        void suspendedAndUnderReview() throws Exception {
            Long postId = threeReports();
            suspend(postId);
            JsonNode suspended = detail(postId);
            assertThat(suspended.get("monitoringStatus").asText()).isEqualTo("SUSPENDED");
            assertThat(suspended.get("appeal").isNull()).isTrue();
            assertThat(suspended.get("metrics").get("frozenAt").asText())
                    .isEqualTo(suspended.get("suspensions").get(0).get("suspendedAt").asText());

            appeal(postId);
            JsonNode review = detail(postId);
            assertThat(review.get("monitoringStatus").asText()).isEqualTo("UNDER_REVIEW");
            assertThat(review.get("appeal").get("status").asText()).isEqualTo("PENDING");
            assertThat(review.get("suspensions").get(0).get("open").asBoolean()).isTrue();
            assertThat(review.get("suspensions").get(0).get("deadlinePaused").asBoolean()).isTrue();
            assertThat(review.get("metrics").get("frozenAt").asText())
                    .isEqualTo(review.get("suspensions").get(0).get("suspendedAt").asText());
        }

        @Test
        @DisplayName("[ST-05] C8 반려 삭제 — 유예 = 반려 + 7일 · 보관 만료 = 삭제 + 6개월 · 원 조치 · 지표는 삭제 시각에 동결")
        void rejected() throws Exception {
            Long postId = publish(soyeon, "반려", LocalDateTime.now().minusDays(2));
            rejectedPost(postId, "의료 행위 대체를 암시합니다.");
            Long suspensionId = jdbc.queryForObject("SELECT suspension_id FROM post_suspension WHERE post_id = ?",
                    Long.class, postId);

            JsonNode body = detail(postId);
            JsonNode deletion = body.get("deletion");
            assertThat(deletion.get("type").asText()).isEqualTo("APPEAL_REJECTED");
            assertThat(deletion.get("deletedBySelf").asBoolean()).isFalse();
            assertThat(deletion.get("deletedByName").asText()).isEqualTo("김운영");
            assertThat(deletion.get("originSuspensionId").asLong()).isEqualTo(suspensionId);
            assertThat(deletion.get("rejectReason").asText()).isEqualTo("의료 행위 대체를 암시합니다.");
            assertThat(at(deletion.get("graceFrom"))).isEqualTo(at(body.get("appeal").get("reviewedAt")));
            assertThat(at(deletion.get("graceUntil"))).isEqualTo(at(deletion.get("graceFrom")).plusDays(7));
            assertThat(at(deletion.get("purgeAt"))).isEqualTo(at(deletion.get("deletedAt")).plusMonths(6));
            assertThat(at(deletion.get("deletedAt"))).isEqualTo(at(deletion.get("graceFrom")));
            assertThat(deletion.get("originalDownloads").size()).isZero();
            assertThat(deletion.get("daysAfterSuspension").isNull()).isTrue();
            assertThat(deletion.get("appealDeadlineAtDeletion").isNull()).isTrue();
            assertThat(body.get("metrics").get("frozenAt").asText()).isEqualTo(deletion.get("deletedAt").asText());
        }

        @Test
        @DisplayName("[ST-06] C8' 기한 경과 삭제 — 원 조치만 있고 처리자 · 반려 사유 · 유예 · 내려받기는 없다")
        void expired() throws Exception {
            Long postId = publish(soyeon, "기한 경과", LocalDateTime.now().minusDays(9));
            suspend(postId);
            expireSuspension(postId);

            JsonNode body = detail(postId);
            JsonNode deletion = body.get("deletion");
            assertThat(body.get("monitoringStatus").asText()).isEqualTo("CLOSED");
            assertThat(deletion.get("type").asText()).isEqualTo("APPEAL_EXPIRED");
            assertThat(deletion.get("originSuspensionId").asLong())
                    .isEqualTo(body.get("suspensions").get(0).get("suspensionId").asLong());
            for (String field : List.of("deletedByName", "rejectReason", "graceFrom", "graceUntil")) {
                assertThat(deletion.get(field).isNull()).as(field).isTrue();
            }
            assertThat(deletion.get("originalDownloads").size()).isZero();
            assertThat(body.get("suspensions").get(0).get("resolution").asText()).isEqualTo("DELETED_BY_EXPIRE");
        }

        @Test
        @DisplayName("[ST-07] C10 작성자 삭제 — 중지 다음날 삭제면 「중지 후 1일」(날짜 차) · 그 조치의 기한 · 원 조치 기록은 남는다")
        void selfDeletedWhileSuspended() throws Exception {
            Long postId = publish(soyeon, "작성자 삭제", LocalDateTime.now().minusDays(3));
            suspend(postId);
            setSuspendedAt(postId, LocalDateTime.now().minusDays(1).withHour(23).withMinute(50));
            selfDelete(postId).andExpect(status().is2xxSuccessful());

            JsonNode body = detail(postId);
            JsonNode deletion = body.get("deletion");
            JsonNode origin = body.get("suspensions").get(0);
            assertThat(deletion.get("type").asText()).isEqualTo("SELF");
            assertThat(deletion.get("deletedBySelf").asBoolean()).isTrue();
            assertThat(deletion.get("deletedByName").asText()).isEqualTo("김소연");
            assertThat(deletion.get("originSuspensionId").asLong()).isEqualTo(origin.get("suspensionId").asLong());
            assertThat(deletion.get("daysAfterSuspension").asLong()).isEqualTo(1);
            assertThat(deletion.get("appealDeadlineAtDeletion").asText()).isEqualTo(origin.get("appealDeadline").asText());
            assertThat(deletion.get("graceFrom").isNull()).isTrue();
            assertThat(deletion.get("graceUntil").isNull()).isTrue();
            assertThat(deletion.get("originalDownloads").size()).isZero();
            assertThat(origin.get("resolution").asText()).isEqualTo("DELETED_BY_SELF");
            assertThat(origin.get("policyClauseCode").asText()).isEqualTo("ARTICLE_3_2");
        }

        @Test
        @DisplayName("[ST-08 · ST-09] 게시중 본인 삭제는 원 조치가 없고, 재중지 뒤 삭제면 원 조치는 최신 조치다")
        void selfDeletedOtherwise() throws Exception {
            Long plain = publish(soyeon, "그냥 삭제", LocalDateTime.now().minusDays(1));
            selfDelete(plain).andExpect(status().is2xxSuccessful());
            JsonNode plainDeletion = detail(plain).get("deletion");
            assertThat(plainDeletion.get("type").asText()).isEqualTo("SELF");
            assertThat(plainDeletion.get("originSuspensionId").isNull()).isTrue();
            assertThat(plainDeletion.get("daysAfterSuspension").isNull()).isTrue();
            assertThat(postIds(list("tab", "CLOSED"))).contains(plain);

            Long again = publish(harin, "재중지 뒤 삭제", LocalDateTime.now().minusDays(3));
            suspend(again);
            approve(appeal(again), Map.of()).andExpect(status().isOk());
            jdbc.update("UPDATE post_suspension SET suspended_at = suspended_at - INTERVAL '1' DAY WHERE post_id = ?", again);
            suspend(again);
            Long latest = jdbc.queryForObject("SELECT MAX(suspension_id) FROM post_suspension WHERE post_id = ?",
                    Long.class, again);
            selfDelete(again).andExpect(status().is2xxSuccessful());

            assertThat(detail(again).get("deletion").get("originSuspensionId").asLong()).isEqualTo(latest);
        }

        @Test
        @DisplayName("[ST-10] 심사 중에는 작성자가 삭제할 수 없다 — C10은 이의 신청 전에만 생긴다")
        void cannotSelfDeleteDuringReview() throws Exception {
            Long postId = publish(soyeon, "심사 중 삭제 시도", LocalDateTime.now().minusDays(1));
            suspend(postId);
            appeal(postId);

            selfDelete(postId).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_NOT_DELETABLE"));
            assertThat(detail(postId).get("monitoringStatus").asText()).isEqualTo("UNDER_REVIEW");
        }
    }

    // ------------------------------------------------------------------ 7. 지표

    @Nested
    @DisplayName("지표")
    class Metrics {

        @Test
        @DisplayName("[MT-01 · MT-07 · MT-09] 게시중 — 원천 로그 집계 · 이 게시물 귀속분만 · 누적 카운터는 쓰지 않는다")
        void publishedMetrics() throws Exception {
            Long postId = publish(soyeon, "지표", LocalDateTime.now().minusDays(5));
            Long other = publish(soyeon, "다른 게시물", LocalDateTime.now().minusDays(5));
            LocalDateTime t = LocalDateTime.now().minusDays(2);
            for (int n = 0; n < 10; n++) {
                impression(postId, soyeon, "v" + n, t.plusMinutes(n));
            }
            impression(other, soyeon, "o1", t);
            like(consumer("l1", null), postId, t);
            like(consumer("l2", null), postId, t);
            for (int n = 0; n < 3; n++) {
                visit(soyeon, null, "visit-" + n, postId, t.plusHours(1));
            }
            visit(soyeon, null, "visit-other", other, t.plusHours(1));
            visit(soyeon, null, "visit-none", null, t.plusHours(1));
            follow(consumer("f1", null), soyeon, postId, t.plusHours(2));
            follow(consumer("f2", null), soyeon, other, t.plusHours(2));
            jdbc.update("UPDATE post SET impression_count = 999, like_count = 999 WHERE post_id = ?", postId);

            JsonNode metrics = detail(postId).get("metrics");
            assertThat(metrics.get("impressions").asLong()).isEqualTo(10);
            assertThat(metrics.get("likes").asLong()).isEqualTo(2);
            assertThat(metrics.get("likeRate").asDouble()).isEqualTo(20.0);
            assertThat(metrics.get("showroomVisits").asLong()).isEqualTo(3);
            assertThat(metrics.get("follows").asLong()).isEqualTo(1);
            assertThat(metrics.get("frozenAt").isNull()).isTrue();
        }

        @Test
        @DisplayName("[MT-02 · MT-03] 좋아요율은 소수 첫째 자리 반올림 · 노출 0이면 null")
        void likeRate() throws Exception {
            Long postId = publish(soyeon, "반올림", LocalDateTime.now().minusDays(5));
            for (int n = 0; n < 17; n++) {
                impression(postId, soyeon, "r" + n, LocalDateTime.now().minusDays(1));
            }
            like(consumer("rl1", null), postId, LocalDateTime.now().minusDays(1));
            like(consumer("rl2", null), postId, LocalDateTime.now().minusDays(1));
            assertThat(detail(postId).get("metrics").get("likeRate").asDouble()).isEqualTo(11.8);

            Long empty = publish(harin, "노출 없음", LocalDateTime.now().minusDays(1));
            JsonNode metrics = detail(empty).get("metrics");
            assertThat(metrics.get("impressions").asLong()).isZero();
            assertThat(metrics.get("likeRate").isNull()).isTrue();
        }

        @Test
        @DisplayName("[MT-04] 노출 중지 · 심사 중 — 조치 시각 이후 · 같은 시각 로그는 빠진다(반열림)")
        void frozenAtSuspension() throws Exception {
            Long postId = publish(soyeon, "동결", LocalDateTime.now().minusDays(5));
            suspend(postId);
            LocalDateTime t = LocalDateTime.now().minusDays(1).withNano(0);
            setSuspendedAt(postId, t);
            for (int n = 0; n < 5; n++) {
                impression(postId, soyeon, "before-" + n, t.minusHours(n + 1));
            }
            impression(postId, soyeon, "same", t);
            for (int n = 0; n < 3; n++) {
                impression(postId, soyeon, "after-" + n, t.plusHours(n + 1));
            }

            JsonNode suspended = detail(postId).get("metrics");
            assertThat(suspended.get("impressions").asLong()).isEqualTo(5);
            assertThat(at(suspended.get("frozenAt"))).isEqualTo(t);

            appeal(postId);
            JsonNode review = detail(postId).get("metrics");
            assertThat(review.get("impressions").asLong()).isEqualTo(5);
            assertThat(at(review.get("frozenAt"))).isEqualTo(t);
        }

        @Test
        @DisplayName("[MT-05] 영구 삭제 — 삭제 시각에 동결")
        void frozenAtDeletion() throws Exception {
            Long postId = publish(soyeon, "삭제 동결", LocalDateTime.now().minusDays(5));
            LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).withNano(0);
            impression(postId, soyeon, "before", deletedAt.minusHours(1));
            impression(postId, soyeon, "after", deletedAt.plusHours(1));
            softDelete(postId, PostDeleteReason.SELF, deletedAt);

            JsonNode metrics = detail(postId).get("metrics");
            assertThat(metrics.get("impressions").asLong()).isEqualTo(1);
            assertThat(at(metrics.get("frozenAt"))).isEqualTo(deletedAt);
        }

        @Test
        @DisplayName("[MT-06] 조치 취소 · 승인 재게시 뒤에도 좋아요는 그대로이고 동결이 풀린다")
        void restoredAfterRepublish() throws Exception {
            Long canceled = publish(soyeon, "취소", LocalDateTime.now().minusDays(5));
            Long approved = publish(harin, "승인", LocalDateTime.now().minusDays(5));
            for (Long postId : List.of(canceled, approved)) {
                like(consumer("keep1-" + postId, null), postId, LocalDateTime.now().minusDays(2));
                like(consumer("keep2-" + postId, null), postId, LocalDateTime.now().minusDays(2));
                suspend(postId);
            }
            cancelSuspension(canceled, null).andExpect(status().isOk());
            approve(appeal(approved), Map.of()).andExpect(status().isOk());

            for (Long postId : List.of(canceled, approved)) {
                JsonNode metrics = detail(postId).get("metrics");
                assertThat(metrics.get("likes").asLong()).isEqualTo(2);
                assertThat(metrics.get("frozenAt").isNull()).isTrue();
            }
        }

        @Test
        @DisplayName("[MT-08] 스튜디오 인사이트(최근 30일)와 같은 숫자 — 중지 중에도 둘 다 조치 시각에서 자른다")
        void sameAsStudioInsights() throws Exception {
            Long postId = publish(soyeon, "숫자 일치", LocalDateTime.now().minusDays(10));
            LocalDateTime t = LocalDateTime.now().minusDays(3);
            for (int n = 0; n < 7; n++) {
                impression(postId, soyeon, "s" + n, t.plusMinutes(n));
            }
            like(consumer("sl1", null), postId, t);
            visit(soyeon, null, "sv1", postId, t.plusHours(1));
            visit(soyeon, null, "sv2", postId, t.plusHours(1));
            follow(consumer("sf1", null), soyeon, postId, t.plusHours(2));
            assertSameAsStudio(postId);

            suspend(postId);
            setSuspendedAt(postId, t.plusMinutes(3).plusSeconds(30));
            assertSameAsStudio(postId);
            assertThat(detail(postId).get("metrics").get("impressions").asLong()).isEqualTo(4);
        }

        private void assertSameAsStudio(Long postId) throws Exception {
            JsonNode admin = detail(postId).get("metrics");
            String body = mockMvc.perform(get("/v1/creator/posts/" + postId + "/insights")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken(soyeon)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode studio = objectMapper.readTree(body);
            assertThat(admin.get("impressions").asLong()).isEqualTo(studio.get("reaction").get("impressions").asLong());
            assertThat(admin.get("likes").asLong()).isEqualTo(studio.get("reaction").get("likes").asLong());
            assertThat(admin.get("likeRate")).isEqualTo(studio.get("reaction").get("likeRate"));
            assertThat(admin.get("showroomVisits").asLong())
                    .isEqualTo(studio.get("behavior").get("showroomVisits").asLong());
            assertThat(admin.get("follows").asLong()).isEqualTo(studio.get("behavior").get("follows").asLong());
        }
    }

    // ------------------------------------------------------------------ 8. 처리 이력

    @Nested
    @DisplayName("처리 이력")
    class Timeline {

        @Test
        @DisplayName("[TL-01 · TL-02] 등록 · 마지막 수정 1건 · 신고 순번은 접수 시각 순이고 주체는 마스킹된 소비자다")
        void publishedEditedReported() throws Exception {
            LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
            Long postId = publish(soyeon, "이력", base.minusDays(1));
            jdbc.update("UPDATE post SET edited_at = ? WHERE post_id = ?", base.minusHours(5), postId);
            Long later = report(postId, consumer("tl-later", "오서린"), PostReportReason.OTHER, "기타 사유", base.plusHours(2));
            Long earlier = report(postId, consumer("tl-earlier", "한지준"), PostReportReason.SPAM, null, base);

            JsonNode timeline = detail(postId).get("timeline");
            JsonNode published = item(timeline, "PUBLISHED", 0);
            assertThat(published.get("label").asText()).isEqualTo("게시물 등록");
            assertThat(published.get("actorType").asText()).isEqualTo("CREATOR");
            assertThat(published.get("actorName").asText()).isEqualTo("김소연");
            assertThat(published.get("tone").asText()).isEqualTo("INFO");
            assertThat(count(timeline, "EDITED")).isEqualTo(1);
            assertThat(item(timeline, "EDITED", 0).get("label").asText()).isEqualTo("게시물 수정");

            JsonNode second = item(timeline, "REPORTED", 0);
            JsonNode first = item(timeline, "REPORTED", 1);
            assertThat(first.get("label").asText()).isEqualTo("신고 접수 (1건째)");
            assertThat(first.get("refId").asLong()).isEqualTo(earlier);
            assertThat(first.get("actorType").asText()).isEqualTo("USER");
            assertThat(first.get("actorName").asText()).isEqualTo("한*준");
            assertThat(first.get("detail").asText()).isEqualTo("스팸 · 반복 게시");
            assertThat(first.get("tone").asText()).isEqualTo("WARNING");
            assertThat(second.get("label").asText()).isEqualTo("신고 접수 (2건째)");
            assertThat(second.get("refId").asLong()).isEqualTo(later);
            assertDescending(timeline);
        }

        @Test
        @DisplayName("[TL-05 · TL-06] 노출 중지로 닫힌 신고는 종결 줄을 만들지 않고, 조치 취소의 주체는 취소한 운영자다")
        void suspendAndCancel() throws Exception {
            Long postId = threeReports();
            suspend(postId);
            adminPost(ADMIN_POSTS + "/" + postId + "/suspension/cancel", admin2, null).andExpect(status().isOk());

            JsonNode timeline = detail(postId).get("timeline");
            assertThat(count(timeline, "REPORTS_CLOSED")).isZero();
            JsonNode suspended = item(timeline, "SUSPENDED", 0);
            assertThat(suspended.get("label").asText()).isEqualTo("노출 중지 · 허위 · 과장 정보");
            assertThat(suspended.get("detail").asText()).isEqualTo("서비스 운영정책 — 커뮤니티 장 3조 2항 — 효과 · 가격 오인 표현");
            assertThat(suspended.get("actorName").asText()).isEqualTo("김운영");
            assertThat(suspended.get("tone").asText()).isEqualTo("DANGER");
            JsonNode canceled = item(timeline, "SUSPENSION_CANCELED", 0);
            assertThat(canceled.get("label").asText()).isEqualTo("조치 취소 · 재게시");
            assertThat(canceled.get("actorType").asText()).isEqualTo("ADMIN");
            assertThat(canceled.get("actorName").asText()).isEqualTo("박운영");
            assertThat(canceled.get("tone").asText()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("[TL-07 · TL-08] 이의 접수 · 승인 줄과 작성자 알림 — 알림 줄은 작성자에게 간 통지만, 문구는 이벤트별")
        void appealAndNotices() throws Exception {
            Long postId = publish(soyeon, "알림 줄", LocalDateTime.now().minusDays(3));
            suspend(postId);
            approve(appeal(postId), Map.of()).andExpect(status().isOk());
            jdbc.update("UPDATE post_notification_log SET sent_at = sent_at - INTERVAL '1' DAY WHERE post_id = ?", postId);
            jdbc.update("UPDATE post_suspension SET suspended_at = suspended_at - INTERVAL '1' DAY, "
                    + "resolved_at = resolved_at - INTERVAL '1' DAY WHERE post_id = ?", postId);
            jdbc.update("UPDATE post_appeal SET submitted_at = submitted_at - INTERVAL '1' DAY, "
                    + "reviewed_at = reviewed_at - INTERVAL '1' DAY WHERE post_id = ?", postId);
            suspend(postId);
            cancelSuspension(postId, null).andExpect(status().isOk());

            JsonNode timeline = detail(postId).get("timeline");
            JsonNode submitted = item(timeline, "APPEAL_SUBMITTED", 0);
            assertThat(submitted.get("label").asText()).isEqualTo("이의 신청 접수");
            assertThat(submitted.get("actorType").asText()).isEqualTo("CREATOR");
            assertThat(submitted.get("tone").asText()).isEqualTo("WARNING");
            JsonNode approved = item(timeline, "APPEAL_APPROVED", 0);
            assertThat(approved.get("label").asText()).isEqualTo("이의 신청 승인 · 재게시");
            assertThat(approved.get("tone").asText()).isEqualTo("SUCCESS");

            List<String> notices = new ArrayList<>();
            timeline.forEach(entry -> {
                if (entry.get("type").asText().equals("NOTIFIED")) {
                    notices.add(entry.get("detail").asText());
                    assertThat(entry.get("actorType").asText()).isEqualTo("SYSTEM");
                    assertThat(entry.get("label").asText()).isEqualTo("작성자 알림 발송");
                }
            });
            assertThat(notices).containsExactly("조치 취소 안내", "노출 중지 안내", "재게시 안내", "이의 신청 접수 안내",
                    "노출 중지 안내");

            Long selfDeleted = publish(harin, "본인 삭제 알림", LocalDateTime.now().minusDays(1));
            suspend(selfDeleted);
            selfDelete(selfDeleted).andExpect(status().is2xxSuccessful());
            assertThat(notificationCount(selfDeleted, "DELETED_BY_SELF")).isEqualTo(1);
            List<String> selfNotices = new ArrayList<>();
            detail(selfDeleted).get("timeline").forEach(entry -> {
                if (entry.get("type").asText().equals("NOTIFIED")) {
                    selfNotices.add(entry.get("detail").asText());
                }
            });
            assertThat(selfNotices).containsExactly("노출 중지 안내");
        }

        @Test
        @DisplayName("[TL-09 · TL-10] 원본 내려받기 줄 · 삭제 줄의 주체 — 반려(운영자) · 기한 경과(시스템) · 본인(인플루언서)")
        void downloadsAndDeletionActors() throws Exception {
            Long rejected = publish(soyeon, "반려 삭제", LocalDateTime.now().minusDays(2));
            rejectedPost(rejected, "재게시할 수 없습니다.");
            downloadOriginals(rejected, creatorToken(soyeon)).andExpect(status().isOk());
            downloadOriginals(rejected, creatorToken(soyeon)).andExpect(status().isOk());
            Long expired = publish(soyeon, "기한 경과", LocalDateTime.now().minusDays(9));
            suspend(expired);
            expireSuspension(expired);
            Long self = publish(harin, "본인 삭제", LocalDateTime.now().minusDays(1));
            selfDelete(self).andExpect(status().is2xxSuccessful());

            JsonNode rejectedTimeline = detail(rejected).get("timeline");
            assertThat(count(rejectedTimeline, "ORIGINAL_DOWNLOADED")).isEqualTo(2);
            assertThat(item(rejectedTimeline, "ORIGINAL_DOWNLOADED", 0).get("label").asText()).isEqualTo("원본 내려받기 (2장)");
            assertThat(item(rejectedTimeline, "ORIGINAL_DOWNLOADED", 0).get("actorType").asText()).isEqualTo("CREATOR");
            JsonNode rejectedDeletion = item(rejectedTimeline, "DELETED", 0);
            assertThat(rejectedDeletion.get("label").asText()).isEqualTo("영구 삭제 · 비공개 보관 전환");
            assertThat(rejectedDeletion.get("actorType").asText()).isEqualTo("ADMIN");
            assertThat(rejectedDeletion.get("actorName").asText()).isEqualTo("김운영");

            JsonNode expiredTimeline = detail(expired).get("timeline");
            JsonNode expiredDeletion = item(expiredTimeline, "DELETED", 0);
            assertThat(expiredDeletion.get("label").asText()).isEqualTo("기한 경과 · 영구 삭제 · 비공개 보관 전환");
            assertThat(expiredDeletion.get("actorType").asText()).isEqualTo("SYSTEM");
            assertThat(expiredDeletion.get("actorName").isNull()).isTrue();
            assertThat(texts(expiredTimeline, "detail")).contains("기한 경과 삭제 안내");

            JsonNode selfDeletion = item(detail(self).get("timeline"), "DELETED", 0);
            assertThat(selfDeletion.get("label").asText()).isEqualTo("작성자 삭제 · 비공개 보관 전환");
            assertThat(selfDeletion.get("actorType").asText()).isEqualTo("CREATOR");
            assertThat(selfDeletion.get("actorName").asText()).isEqualTo("유하린");
        }

        @Test
        @DisplayName("[TL-11 · TL-13] 전 과정은 최신순이고, 반려와 같은 초에 생긴 줄은 삭제 → 반려 → 반려 안내 순이다")
        void rejectSameSecondOrdering() throws Exception {
            Long postId = publish(soyeon, "전 과정", LocalDateTime.now().minusDays(3));
            report(postId, "full", "한지준", LocalDateTime.now().minusDays(2));
            rejectedPost(postId, "재게시할 수 없습니다.");
            // 조치 시각과 알림 적재 시각은 각자 시계를 읽는다 — 같은 초 안에서 알림이 늦은 경우를 고정한다
            LocalDateTime t = LocalDateTime.now().minusMinutes(5).withNano(100_000_000);
            jdbc.update("UPDATE post_appeal SET reviewed_at = ? WHERE post_id = ?", t, postId);
            jdbc.update("UPDATE post SET deleted_at = ? WHERE post_id = ?", t, postId);
            jdbc.update("UPDATE post_notification_log SET sent_at = ? WHERE post_id = ? AND event_type = 'APPEAL_REJECTED'",
                    t.plusNanos(800_000_000), postId);

            JsonNode timeline = detail(postId).get("timeline");
            List<String> types = texts(timeline, "type");
            int deleted = types.indexOf("DELETED");
            int rejected = types.indexOf("APPEAL_REJECTED");
            int notice = texts(timeline, "detail").indexOf("반려 안내");
            assertThat(deleted).isLessThan(rejected);
            assertThat(rejected).isLessThan(notice);
            assertDescending(timeline);
        }

        @Test
        @DisplayName("[TL-14] 같은 초 — 조치 취소가 그 알림 위, 이의 접수 안내가 이의 접수 위(결과가 원인 위)")
        void cancelAndAppealSameSecondOrdering() throws Exception {
            Long canceled = publish(soyeon, "취소", LocalDateTime.now().minusDays(2));
            suspend(canceled);
            cancelSuspension(canceled, null).andExpect(status().isOk());
            LocalDateTime t = LocalDateTime.now().minusMinutes(5).withNano(100_000_000);
            jdbc.update("UPDATE post_suspension SET resolved_at = ? WHERE post_id = ?", t, canceled);
            jdbc.update("UPDATE post_notification_log SET sent_at = ? WHERE post_id = ? AND event_type = 'SUSPENSION_CANCELED'",
                    t.plusNanos(800_000_000), canceled);

            JsonNode canceledTimeline = detail(canceled).get("timeline");
            assertThat(texts(canceledTimeline, "type").indexOf("SUSPENSION_CANCELED"))
                    .isLessThan(texts(canceledTimeline, "detail").indexOf("조치 취소 안내"));

            Long appealed = publish(harin, "이의", LocalDateTime.now().minusDays(2));
            suspend(appealed);
            appeal(appealed);
            jdbc.update("UPDATE post_appeal SET submitted_at = ? WHERE post_id = ?", t.plusNanos(800_000_000), appealed);
            jdbc.update("UPDATE post_notification_log SET sent_at = ? WHERE post_id = ? AND event_type = 'APPEAL_RECEIVED'",
                    t, appealed);

            JsonNode appealedTimeline = detail(appealed).get("timeline");
            assertThat(texts(appealedTimeline, "detail").indexOf("이의 신청 접수 안내"))
                    .isLessThan(texts(appealedTimeline, "type").indexOf("APPEAL_SUBMITTED"));
        }

        private JsonNode item(JsonNode timeline, String type, int nth) {
            int seen = 0;
            for (JsonNode entry : timeline) {
                if (entry.get("type").asText().equals(type) && seen++ == nth) {
                    return entry;
                }
            }
            throw new AssertionError("처리 이력에 " + type + " #" + nth + " 이(가) 없다");
        }

        private int count(JsonNode timeline, String type) {
            int count = 0;
            for (JsonNode entry : timeline) {
                if (entry.get("type").asText().equals(type)) {
                    count++;
                }
            }
            return count;
        }

        private void assertDescending(JsonNode timeline) {
            LocalDateTime previous = null;
            for (JsonNode entry : timeline) {
                LocalDateTime current = at(entry.get("at")).truncatedTo(ChronoUnit.SECONDS);
                if (previous != null) {
                    assertThat(current).isBeforeOrEqualTo(previous);
                }
                previous = current;
            }
        }
    }

    // ------------------------------------------------------------------ 9. 작성자 조치 이력

    @Nested
    @DisplayName("작성자 조치 이력")
    class SanctionCounts {

        @Test
        @DisplayName("[SC-01 · SC-03 · SC-06] 노출 중지는 거둔 조치를 빼고 작성자 전체 게시물 기준으로 센다 — 다른 작성자는 섞이지 않는다")
        void suspensionCount() throws Exception {
            Long viewed = publish(soyeon, "보는 게시물", LocalDateTime.now().minusDays(1));
            assertThat(detail(viewed).get("author").get("suspensionCount").asLong()).isZero();
            assertThat(detail(viewed).get("author").get("sanctionDeleteCount").asLong()).isZero();

            Long kept = publish(soyeon, "중지 유지", LocalDateTime.now().minusDays(2));
            Long canceled = publish(soyeon, "중지 취소", LocalDateTime.now().minusDays(3));
            suspend(kept);
            suspend(canceled);
            cancelSuspension(canceled, null).andExpect(status().isOk());
            for (int n = 0; n < 3; n++) {
                suspend(publish(harin, "하린 중지 " + n, LocalDateTime.now().minusDays(1)));
            }

            JsonNode author = detail(viewed).get("author");
            assertThat(author.get("suspensionCount").asLong()).isEqualTo(1);
            assertThat(detail(publish(harin, "하린 보기", LocalDateTime.now())).get("author").get("suspensionCount")
                    .asLong()).isEqualTo(3);
        }

        @Test
        @DisplayName("[SC-02] 삭제는 제재로 끝난 것만 — 반려 + 기한 경과 · 본인 삭제는 세지 않는다")
        void sanctionDeleteCount() throws Exception {
            rejectedPost(publish(soyeon, "반려", LocalDateTime.now().minusDays(3)), "재게시할 수 없습니다.");
            Long expired = publish(soyeon, "기한 경과", LocalDateTime.now().minusDays(9));
            suspend(expired);
            expireSuspension(expired);
            Long self = publish(soyeon, "본인 삭제", LocalDateTime.now().minusDays(2));
            suspend(self);
            selfDelete(self).andExpect(status().is2xxSuccessful());

            JsonNode author = detail(publish(soyeon, "보기", LocalDateTime.now())).get("author");
            assertThat(author.get("sanctionDeleteCount").asLong()).isEqualTo(2);
            assertThat(author.get("suspensionCount").asLong()).isEqualTo(3);
        }

        @Test
        @DisplayName("[SC-05] 어드민 인플루언서 상세의 숨김 · 노출 중지 횟수와 같다(공구 숨김이 없을 때)")
        void sameAsInfluencerDetail() throws Exception {
            Long kept = publish(soyeon, "중지", LocalDateTime.now().minusDays(1));
            Long canceled = publish(soyeon, "취소", LocalDateTime.now().minusDays(2));
            suspend(kept);
            suspend(canceled);
            cancelSuspension(canceled, null).andExpect(status().isOk());

            long fromPost = detail(kept).get("author").get("suspensionCount").asLong();
            long fromInfluencer = adminJson("/v1/admin/creators/" + soyeon.getId() + "/overview")
                    .get("activity").get("hiddenCount").asLong();
            assertThat(fromPost).isEqualTo(1);
            assertThat(fromInfluencer).isEqualTo(fromPost);
        }
    }

    // ------------------------------------------------------------------ 픽스처

    /** 신고 3건 — 허위 · 과장 정보 2 · 기타 1 */
    private Long threeReports() {
        Long postId = publish(soyeon, "신고 3건", LocalDateTime.now().minusDays(2));
        report(postId, consumer("t-1-" + postId, "한지준"), PostReportReason.MISLEADING_INFO, null,
                LocalDateTime.now().minusHours(3));
        report(postId, consumer("t-2-" + postId, "오서린"), PostReportReason.MISLEADING_INFO, null,
                LocalDateTime.now().minusHours(2));
        report(postId, consumer("t-3-" + postId, "임서현"), PostReportReason.OTHER, "기타 사유",
                LocalDateTime.now().minusHours(1));
        return postId;
    }
}
