package showroomz.api.admin.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostOriginalDownloadRepository;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostReportReason;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [통합] 어드민 일반 게시물 모니터링 (§54) — HTTP 진입점부터 DB까지.
 *
 * <p>목록 · 탭 건수가 네이티브 SQL(파생 상태 · 조치 필요 우선 정렬)이라, 단위 테스트로는 잡히지 않는 조건 · 정렬 · 집계를
 * 실제 쿼리로 확인한다.
 */
@DisplayName("[통합] 어드민 일반 게시물 모니터링")
class AdminPostMonitoringIntegrationTest extends IntegrationTestSupport {

    private static final String CDN = "https://cdn.showroomz.test/post/";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostReportRepository postReportRepository;
    @Autowired
    private PostOriginalDownloadRepository postOriginalDownloadRepository;

    private String admin;
    private Creator soyeon;
    private Creator harin;
    private Users soyeonOwner;

    @BeforeEach
    void setUp() {
        Seller operator = fixture.createAdmin("admin@showroomz.test", "김운영");
        admin = adminToken(operator);
        soyeon = createShowroom("김소연", "soyeon");
        soyeonOwner = soyeon.getUser();
        harin = createShowroom("유하린", "harin");
    }

    @Nested
    @DisplayName("목록 · 탭 건수")
    class ListAndSummary {

        @Test
        @DisplayName("작성중은 빠지고, 조치 필요(심사 중 · 신고 접수 — 오래 기다린 순)가 맨 앞 · 노출 중지 · 게시중 · 영구 삭제 순이다")
        void actionFirstOrderAndSummary() throws Exception {
            Long published = publish(harin, "여름 데일리 메이크업 루틴", LocalDateTime.now().minusDays(1));
            Long reported = publish(soyeon, "브랜드 협찬 없이 3개월 써본 진짜 후기", LocalDateTime.now().minusDays(2));
            report(reported, "han", "한지준", LocalDateTime.now().minusHours(5));
            report(reported, "oh", "오서린", LocalDateTime.now().minusHours(4));
            Long suspended = publish(harin, "가을 신상 언박싱", LocalDateTime.now().minusDays(3));
            suspend(suspended);
            Long underReview = publish(soyeon, "아침 루틴에 넣은 것들", LocalDateTime.now().minusDays(4));
            suspend(underReview);
            appeal(underReview);
            Long deleted = publish(harin, "직접 만든 홈케어 레시피", LocalDateTime.now().minusDays(5));
            softDelete(deleted);
            postRepository.save(Post.draft(soyeon, "임시저장", new BigDecimal("0.8000")));

            mockMvc.perform(get("/v1/admin/posts").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    // 신고는 5시간 전부터, 이의 신청은 방금 — 같은 조치 필요 순위 안에서는 오래 기다린 쪽이 앞이다
                    .andExpect(jsonPath("$.content[*].postId").value(contains(
                            reported.intValue(), underReview.intValue(), suspended.intValue(),
                            published.intValue(), deleted.intValue())))
                    .andExpect(jsonPath("$.content[0].monitoringStatus").value("REPORTED"))
                    .andExpect(jsonPath("$.content[0].reportCount").value(2))
                    .andExpect(jsonPath("$.content[1].monitoringStatus").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.content[1].reviewDueAt").isNotEmpty())
                    .andExpect(jsonPath("$.content[2].appealDeadline").isNotEmpty())
                    .andExpect(jsonPath("$.content[4].monitoringStatus").value("CLOSED"));

            mockMvc.perform(get("/v1/admin/posts/summary").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.all").value(5))
                    .andExpect(jsonPath("$.reported").value(1))
                    .andExpect(jsonPath("$.suspended").value(1))
                    .andExpect(jsonPath("$.underReview").value(1))
                    .andExpect(jsonPath("$.closed").value(1))
                    .andExpect(jsonPath("$.actionRequired").value(2));
        }

        @Test
        @DisplayName("탭 · 검색 · 신고 많은 순이 파생 상태로 동작한다")
        void tabKeywordAndSort() throws Exception {
            Long one = publish(soyeon, "각질 정리 루틴", LocalDateTime.now().minusDays(1));
            report(one, "a1", "가나다", LocalDateTime.now().minusHours(2));
            Long three = publish(harin, "세안 후 순서", LocalDateTime.now().minusDays(2));
            report(three, "b1", "라마바", LocalDateTime.now().minusHours(3));
            report(three, "b2", "사아자", LocalDateTime.now().minusHours(2));
            report(three, "b3", "차카타", LocalDateTime.now().minusHours(1));
            Long quiet = publish(harin, "100% 진짜_후기", LocalDateTime.now());

            mockMvc.perform(get("/v1/admin/posts").param("tab", "REPORTED").param("sort", "MOST_REPORTED")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[*].postId").value(contains(three.intValue(), one.intValue())));

            mockMvc.perform(get("/v1/admin/posts").param("keyword", "소연").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[*].postId").value(contains(one.intValue())));

            // LIKE 와일드카드는 글자 그대로 찾는다
            mockMvc.perform(get("/v1/admin/posts").param("keyword", "0% 진짜_").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[*].postId").value(contains(quiet.intValue())));
            mockMvc.perform(get("/v1/admin/posts").param("keyword", "_").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[*].postId").value(contains(quiet.intValue())));
        }
    }

    @Nested
    @DisplayName("조치 흐름")
    class Actions {

        @Test
        @DisplayName("신고 접수 상세 → 신고 종결 → 게시중 · 신고 수 0 · 신고 이력은 남는다")
        void reportedThenClosed() throws Exception {
            Long postId = publish(soyeon, "효과 없으면 환불받으세요", LocalDateTime.now().minusDays(1));
            report(postId, "han", "한지준", LocalDateTime.now().minusHours(3));
            report(postId, "lim", "임도현", LocalDateTime.now().minusHours(1));

            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monitoringStatus").value("REPORTED"))
                    .andExpect(jsonPath("$.aspectRatioLabel").value("4:5"))
                    .andExpect(jsonPath("$.reports.pendingCount").value(2))
                    .andExpect(jsonPath("$.reports.items[0].reporterMaskedName").value("한*준"))
                    .andExpect(jsonPath("$.reports.items[0].reporterUserId").isNumber())
                    .andExpect(jsonPath("$.images[0].fileName").value("IMG_0.jpg"))
                    .andExpect(jsonPath("$.timeline[0].label").value("신고 접수 (2건째)"))
                    .andExpect(jsonPath("$.timeline[0].actorName").value("임*현"));

            mockMvc.perform(post("/v1/admin/posts/" + postId + "/reports/close").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monitoringStatus").value("PUBLISHED"))
                    .andExpect(jsonPath("$.closedReportCount").value(2));

            mockMvc.perform(get("/v1/admin/posts").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[0].monitoringStatus").value("PUBLISHED"))
                    .andExpect(jsonPath("$.content[0].reportCount").value(0));
            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.reports.totalCount").value(2))
                    .andExpect(jsonPath("$.timeline[0].label").value("신고 종결 (2건)"))
                    .andExpect(jsonPath("$.timeline[0].actorName").value("김운영"));

            mockMvc.perform(post("/v1/admin/posts/" + postId + "/reports/close").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_REPORT_NOTHING_TO_CLOSE"));
        }

        @Test
        @DisplayName("근거 규정 없이는 내릴 수 없고, 내리면 조치 기록 5항목이 남으며 조치 취소로 되돌린다")
        void suspendThenCancel() throws Exception {
            Long postId = publish(soyeon, "가을 신상 언박싱", LocalDateTime.now().minusDays(1));

            mockMvc.perform(post("/v1/admin/posts/" + postId + "/suspend").header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reasonCode", "MISLEADING_INFO"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("POST_POLICY_CLAUSE_REQUIRED"));

            suspend(postId);

            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.monitoringStatus").value("SUSPENDED"))
                    .andExpect(jsonPath("$.suspensions[0].open").value(true))
                    .andExpect(jsonPath("$.suspensions[0].policyClauseCode").value("ARTICLE_3_2"))
                    .andExpect(jsonPath("$.suspensions[0].policyRef").value("서비스 운영정책 — 커뮤니티 장 3조 2항 — 효과 · 가격 오인 표현"))
                    .andExpect(jsonPath("$.suspensions[0].suspendedByName").value("김운영"))
                    .andExpect(jsonPath("$.suspensions[0].appealDeadline").isNotEmpty())
                    .andExpect(jsonPath("$.suspensions[0].notifiedAt").isNotEmpty())
                    .andExpect(jsonPath("$.metrics.frozenAt").isNotEmpty())
                    .andExpect(jsonPath("$.author.suspensionCount").value(1))
                    .andExpect(jsonPath("$.timeline[0].type").value("SUSPENDED"))
                    .andExpect(jsonPath("$.timeline[1].type").value("NOTIFIED"));

            mockMvc.perform(post("/v1/admin/posts/" + postId + "/suspension/cancel").header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("note", "근거 규정 적용 오류"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monitoringStatus").value("PUBLISHED"));

            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.suspensions[0].resolution").value("CANCELED_BY_ADMIN"))
                    .andExpect(jsonPath("$.suspensions[0].resolvedByName").value("김운영"))
                    // 운영자가 거둔 조치는 작성자 조치 이력에서 빠진다
                    .andExpect(jsonPath("$.author.suspensionCount").value(0))
                    .andExpect(jsonPath("$.metrics.frozenAt").doesNotExist());
        }

        @Test
        @DisplayName("반려 사유 없이는 반려할 수 없고, 반려 뒤 유예 중 원본 내려받기가 삭제 경위에 남는다")
        void rejectThenDownloadOriginals() throws Exception {
            Long postId = publish(soyeon, "직접 만든 홈케어 레시피", LocalDateTime.now().minusDays(2));
            suspend(postId);
            Long appealId = appeal(postId);

            mockMvc.perform(post("/v1/admin/post-appeals/" + appealId + "/reject").header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("POST_APPEAL_REJECT_REASON_REQUIRED"));

            mockMvc.perform(post("/v1/admin/post-appeals/" + appealId + "/reject").header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("comment", "의료 행위 대체를 암시하므로 재게시할 수 없습니다."))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monitoringStatus").value("CLOSED"));

            mockMvc.perform(get("/v1/creator/posts/" + postId + "/originals")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken()))
                    .andExpect(status().isOk());
            assertThat(postOriginalDownloadRepository.findByPostIdOrderByDownloadedAtAsc(postId)).hasSize(1);

            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.monitoringStatus").value("CLOSED"))
                    .andExpect(jsonPath("$.deletion.type").value("APPEAL_REJECTED"))
                    .andExpect(jsonPath("$.deletion.deletedByName").value("김운영"))
                    .andExpect(jsonPath("$.deletion.rejectReason").value("의료 행위 대체를 암시하므로 재게시할 수 없습니다."))
                    .andExpect(jsonPath("$.deletion.graceUntil").isNotEmpty())
                    .andExpect(jsonPath("$.deletion.originalDownloads.length()").value(1))
                    .andExpect(jsonPath("$.deletion.originalDownloads[0].imageCount").value(2))
                    .andExpect(jsonPath("$.appeal.reviewDueAt").isNotEmpty())
                    .andExpect(jsonPath("$.author.sanctionDeleteCount").value(1))
                    .andExpect(jsonPath("$.timeline[*].type").value(hasItems("DELETED", "ORIGINAL_DOWNLOADED")));

            // 반려와 영구 삭제 전환은 같은 시각이다 — 결과(삭제 전환)가 원인(반려) 위에 와야 한다.
            // 내려받기는 그 뒤 어느 시점이든 될 수 있어 맨 위 여부는 보지 않는다.
            String body = mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andReturn().getResponse().getContentAsString();
            List<String> types = new java.util.ArrayList<>();
            objectMapper.readTree(body).get("timeline").forEach(item -> types.add(item.get("type").asText()));
            assertThat(types.indexOf("DELETED")).isLessThan(types.indexOf("APPEAL_REJECTED"));
        }

        @Test
        @DisplayName("사진 「신고 지목」을 켜고 끈다 — 다른 게시물의 사진은 404다")
        void flagImage() throws Exception {
            Long postId = publish(soyeon, "3번째 사진 문구", LocalDateTime.now().minusDays(1));
            Long other = publish(harin, "다른 게시물", LocalDateTime.now().minusDays(1));
            Long imageId = inTransaction(() -> postRepository.findByIdWithImages(postId).orElseThrow()
                    .getImages().get(1).getId());

            mockMvc.perform(put("/v1/admin/posts/" + postId + "/images/" + imageId + "/report-flag")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.images[1].reportFlagged").value(true))
                    .andExpect(jsonPath("$.images[1].reportFlaggedByName").value("김운영"))
                    .andExpect(jsonPath("$.images[0].reportFlagged").value(false));

            mockMvc.perform(put("/v1/admin/posts/" + other + "/images/" + imageId + "/report-flag")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_IMAGE_NOT_FOUND"));

            mockMvc.perform(delete("/v1/admin/posts/" + postId + "/images/" + imageId + "/report-flag")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/v1/admin/posts/" + postId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.images[1].reportFlagged").value(false));
        }

        @Test
        @DisplayName("신고 대기열은 신고자를 마스킹해 싣는다")
        void reportQueueMasksReporter() throws Exception {
            Long postId = publish(soyeon, "신고 대상", LocalDateTime.now().minusDays(1));
            report(postId, "han", "한지준", LocalDateTime.now().minusHours(1));

            mockMvc.perform(get("/v1/admin/post-reports").param("status", "PENDING")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[0].reporterMaskedName").value("한*준"))
                    .andExpect(jsonPath("$.content[0].reporterUserId").isNumber())
                    .andExpect(jsonPath("$.content[*].reporterMaskedName").value(not(hasItem("한지준"))));
        }
    }

    // ------------------------------------------------------------------ 단계

    private void suspend(Long postId) throws Exception {
        mockMvc.perform(post("/v1/admin/posts/" + postId + "/suspend").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reasonCode", "MISLEADING_INFO", "policyClause", "ARTICLE_3_2"))))
                .andExpect(status().isOk());
    }

    /** 이의 신청은 작성자(스튜디오) 경로로 넣는다 — 같은 인플루언서 소유 게시물만 쓴다 */
    private Long appeal(Long postId) throws Exception {
        String body = mockMvc.perform(post("/v1/creator/posts/" + postId + "/appeal")
                        .header(HttpHeaders.AUTHORIZATION, ownerTokenOf(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("content", "개인적인 사용감을 적은 것입니다."))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("appealId").asLong();
    }

    private void softDelete(Long postId) {
        inTransaction(() -> {
            Post post = postRepository.findById(postId).orElseThrow();
            post.softDelete(PostDeleteReason.SELF, LocalDateTime.now(), LocalDateTime.now().plusMonths(6));
            return postRepository.save(post);
        });
    }

    private void report(Long postId, String username, String realName, LocalDateTime at) {
        Users reporter = createUser(username + "-" + postId, username, RoleType.USER);
        reporter.setName(realName);
        userRepository.save(reporter);
        postReportRepository.save(new PostReport(postRepository.getReferenceById(postId), reporter,
                PostReportReason.MISLEADING_INFO, null, at));
    }

    // ------------------------------------------------------------------ 픽스처

    private Long publish(Creator owner, String content, LocalDateTime publishedAt) {
        Post post = Post.published(owner, content, new BigDecimal("0.8000"), publishedAt);
        post.replaceImages(List.of(
                new PostImage(CDN + "0.jpg", CDN + "IMG_0.jpg", 1080, 1350, 812_340),
                new PostImage(CDN + "1.jpg", CDN + "IMG_1.jpg", 1080, 1350, 702_100)));
        return postRepository.save(post).getId();
    }

    private String creatorToken() {
        return bearerToken(soyeonOwner.getUsername(), RoleType.CREATOR, soyeonOwner.getId());
    }

    /** 작성자 계정은 지연 로딩이라 트랜잭션 안에서 토큰까지 만든다 */
    private String ownerTokenOf(Long postId) {
        return inTransaction(() -> {
            Users owner = postRepository.findById(postId).orElseThrow().getCreator().getUser();
            return bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
        });
    }

    private Users createUser(String username, String nickname, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }

    private Creator createShowroom(String showroomName, String handle) {
        Users owner = createUser("creator-" + handle, showroomName, RoleType.CREATOR);
        Creator creator = Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + handle)
                .accountId(handle)
                .followerCount(1000)
                .businessEmail(handle + "@showroomz.test")
                .showroomName(showroomName)
                .build();
        creator.assignShowroomAddressIfAbsent(handle);
        return creatorRepository.save(creator);
    }
}
