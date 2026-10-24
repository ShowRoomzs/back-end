package showroomz.api.admin.post;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.entity.PostLike;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.service.PostLifecycleService;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 일반 게시물 모니터링 시나리오 테스트(54 테스트 시나리오) 공통 픽스처 · 단계.
 *
 * <p>조치 · 이의 신청 · 본인 삭제는 <b>실제 API</b>로 밟는다 — 조치 행 · 신고 전이 · 알림 이력이 운영과 같은 경로로 쌓여야
 * 상세 · 처리 이력 · 작성자 조치 이력의 조립을 그대로 검증할 수 있다. 정렬 · 기한처럼 시각이 걸린 값만 SQL로 옮긴다.
 *
 * <p>하위 클래스는 {@code @MockitoBean}을 더하지 않는다 — 테스트 컨텍스트가 갈라지면 스위트가 그만큼 느려진다.
 */
abstract class AdminPostScenarioSupport extends IntegrationTestSupport {

    protected static final String CDN = "https://cdn.showroomz.test/post/";
    protected static final String ADMIN_POSTS = "/v1/admin/posts";

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected CreatorRepository creatorRepository;
    @Autowired
    protected CreatorFollowRepository creatorFollowRepository;
    @Autowired
    protected PostRepository postRepository;
    @Autowired
    protected PostReportRepository postReportRepository;
    @Autowired
    protected PostImpressionRepository postImpressionRepository;
    @Autowired
    protected PostLikeRepository postLikeRepository;
    @Autowired
    protected ShowroomVisitRepository showroomVisitRepository;
    @Autowired
    protected PostLifecycleService postLifecycleService;
    @Autowired
    protected JdbcTemplate jdbc;

    /** 김운영 */
    protected Seller operator;
    protected String admin;
    /** 박운영 — 처리자 이름 일괄 조회 · 동시성 */
    protected Seller operator2;
    protected String admin2;

    protected Creator soyeon;
    protected Creator harin;

    @BeforeEach
    void setUpScenario() {
        operator = fixture.createAdmin("admin@showroomz.test", "김운영");
        admin = adminToken(operator);
        operator2 = fixture.createAdmin("admin2@showroomz.test", "박운영");
        admin2 = adminToken(operator2);
        soyeon = createShowroom("김소연", "soyeon");
        harin = createShowroom("유하린", "harin");
    }

    // ------------------------------------------------------------------ 픽스처 — 계정

    protected Users createUser(String username, String nickname, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }

    /** 실명(본인인증)이 있는 소비자 — {@code realName}이 null이면 닉네임만 있다 */
    protected Users consumer(String username, String realName) {
        Users user = createUser(username, username, RoleType.USER);
        if (realName != null) {
            user.setName(realName);
            user = userRepository.save(user);
        }
        return user;
    }

    /** 쇼룸명이 없으면 닉네임 = handle */
    protected Creator createShowroom(String showroomName, String handle) {
        return createShowroom(showroomName, handle, showroomName == null ? handle : showroomName);
    }

    protected Creator createShowroom(String showroomName, String handle, String nickname) {
        Users owner = createUser("creator-" + handle, nickname, RoleType.CREATOR);
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

    protected String userToken(Users user) {
        return bearerToken(user.getUsername(), RoleType.USER, user.getId());
    }

    protected String creatorToken(Creator creator) {
        return inTransaction(() -> {
            Users owner = creatorRepository.findById(creator.getId()).orElseThrow().getUser();
            return bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
        });
    }

    /** 작성자 계정은 지연 로딩이라 트랜잭션 안에서 토큰까지 만든다 */
    protected String ownerTokenOf(Long postId) {
        return inTransaction(() -> {
            Users owner = postRepository.findById(postId).orElseThrow().getCreator().getUser();
            return bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
        });
    }

    // ------------------------------------------------------------------ 픽스처 — 게시물

    /** 기본형 — 사진 2장(원본 IMG_0.jpg · IMG_1.jpg · 1080×1350) · 비율 4:5 */
    protected Long publish(Creator owner, String content, LocalDateTime publishedAt) {
        return publish(owner, content, publishedAt, 2);
    }

    protected Long publish(Creator owner, String content, LocalDateTime publishedAt, int imageCount) {
        Post post = Post.published(owner, content, new BigDecimal("0.8000"), publishedAt);
        post.replaceImages(images(imageCount));
        return postRepository.save(post).getId();
    }

    /** 사진 없는 게시물 — 파기 배치가 S3를 부르지 않게 할 때만 쓴다 */
    protected Long publishWithoutImages(Creator owner, String content, LocalDateTime publishedAt) {
        return postRepository.save(Post.published(owner, content, new BigDecimal("0.8000"), publishedAt)).getId();
    }

    protected Long publishWithRatio(Creator owner, String ratio) {
        Post post = Post.published(owner, "비율 " + ratio, new BigDecimal(ratio), LocalDateTime.now().minusDays(1));
        post.replaceImages(images(1));
        return postRepository.save(post).getId();
    }

    protected Long draft(Creator owner, String content) {
        return postRepository.save(Post.draft(owner, content, new BigDecimal("0.8000"))).getId();
    }

    /** 공구 게시물 — 이 화면 대상이 아니다. 확장 테이블 없이 게시물 행만 둔다(모니터링 경로는 행만 본다) */
    protected Long groupBuyPost(Creator owner, String content) {
        Post post = Post.groupBuyDraft(owner, content);
        post.changeGroupBuyExposure(PostStatus.PUBLISHED, LocalDateTime.now().minusDays(1));
        return postRepository.save(post).getId();
    }

    protected static List<PostImage> images(int count) {
        List<PostImage> images = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            images.add(new PostImage(CDN + i + ".jpg", CDN + "IMG_" + i + ".jpg", 1080, 1350, 800_000 + i));
        }
        return images;
    }

    protected List<Long> imageIds(Long postId) {
        return inTransaction(() -> postRepository.findByIdWithImages(postId).orElseThrow()
                .getImages().stream().map(PostImage::getId).toList());
    }

    // ------------------------------------------------------------------ 픽스처 — 신고 · 지표 원천

    /** 신고 1건 — 시각을 정하려고 리포지토리로 넣는다. 접수 규칙(본인 · 중복 · 비노출)은 앱 경로 케이스가 따로 본다 */
    protected Long report(Long postId, Users reporter, PostReportReason reason, String detail, LocalDateTime at) {
        return postReportRepository.save(new PostReport(postRepository.getReferenceById(postId), reporter,
                reason, detail, at)).getId();
    }

    protected Long report(Long postId, String username, String realName, LocalDateTime at) {
        return report(postId, consumer(username + "-" + postId, realName), PostReportReason.MISLEADING_INFO, null, at);
    }

    protected void impression(Long postId, Creator owner, String viewerKey, LocalDateTime viewedAt) {
        postImpressionRepository.save(new PostImpression(postRepository.getReferenceById(postId), owner.getId(),
                null, viewerKey, viewedAt));
    }

    /** 좋아요 시각은 {@code @CreatedDate}가 지금으로 찍으므로 손으로 되돌린다 */
    protected void like(Users user, Long postId, LocalDateTime likedAt) {
        postLikeRepository.save(new PostLike(user, postRepository.getReferenceById(postId)));
        jdbc.update("UPDATE post_like SET created_at = ? WHERE user_id = ? AND post_id = ?", likedAt, user.getId(), postId);
    }

    protected void visit(Creator showroom, Users user, String visitorKey, Long attributedPostId, LocalDateTime at) {
        ShowroomVisit visit = new ShowroomVisit(showroom, user, visitorKey, ShowroomVisitSource.DIRECT, at);
        visit.attributeTo(attributedPostId);
        showroomVisitRepository.save(visit);
    }

    protected void follow(Users user, Creator showroom, Long attributedPostId, LocalDateTime at) {
        CreatorFollow follow = new CreatorFollow(user, showroom);
        follow.attributeTo(attributedPostId);
        creatorFollowRepository.save(follow);
        jdbc.update("UPDATE creator_follow SET created_at = ? WHERE user_id = ? AND creator_id = ?",
                at, user.getId(), showroom.getId());
    }

    // ------------------------------------------------------------------ 단계 — 어드민

    protected ResultActions adminGet(String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path).header(HttpHeaders.AUTHORIZATION, admin);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode adminJson(String path, String... params) throws Exception {
        String body = adminGet(path, params).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    protected JsonNode detail(Long postId) throws Exception {
        return adminJson(ADMIN_POSTS + "/" + postId);
    }

    protected JsonNode list(String... params) throws Exception {
        return adminJson(ADMIN_POSTS, params);
    }

    protected JsonNode summary() throws Exception {
        return adminJson(ADMIN_POSTS + "/summary");
    }

    /** 전체 건수 — {@code PageResponse.pageInfo.totalResults}(페이지 번호는 1부터) */
    protected static long total(JsonNode page) {
        return page.get("pageInfo").get("totalResults").asLong();
    }

    /** 목록 행의 postId — 응답 순서 그대로 */
    protected static List<Long> postIds(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(row -> ids.add(row.get("postId").asLong()));
        return ids;
    }

    protected static JsonNode rowOf(JsonNode page, Long postId) {
        for (JsonNode row : page.get("content")) {
            if (row.get("postId").asLong() == postId) {
                return row;
            }
        }
        throw new AssertionError("목록에 게시물 " + postId + "이(가) 없다");
    }

    protected ResultActions adminPost(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String raw ? raw : toJson(body));
        }
        return mockMvc.perform(request);
    }

    protected ResultActions suspendRequest(Long postId, Object body) throws Exception {
        return adminPost(ADMIN_POSTS + "/" + postId + "/suspend", admin, body);
    }

    protected void suspend(Long postId) throws Exception {
        suspendRequest(postId, Map.of("reasonCode", "MISLEADING_INFO", "policyClause", "ARTICLE_3_2"))
                .andExpect(status().isOk());
    }

    protected ResultActions closeReports(Long postId) throws Exception {
        return adminPost(ADMIN_POSTS + "/" + postId + "/reports/close", admin, null);
    }

    protected ResultActions cancelSuspension(Long postId, Object body) throws Exception {
        return adminPost(ADMIN_POSTS + "/" + postId + "/suspension/cancel", admin, body);
    }

    protected ResultActions approve(Long appealId, Object body) throws Exception {
        return adminPost("/v1/admin/post-appeals/" + appealId + "/approve", admin, body);
    }

    protected ResultActions reject(Long appealId, Object body) throws Exception {
        return adminPost("/v1/admin/post-appeals/" + appealId + "/reject", admin, body);
    }

    protected ResultActions dismiss(Long reportId) throws Exception {
        return adminPost("/v1/admin/post-reports/" + reportId + "/dismiss", admin, null);
    }

    protected static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------ 단계 — 스튜디오 · 앱

    /** 이의 신청은 작성자(스튜디오) 경로로 넣는다 */
    protected Long appeal(Long postId) throws Exception {
        String response = mockMvc.perform(post("/v1/creator/posts/" + postId + "/appeal")
                        .header(HttpHeaders.AUTHORIZATION, ownerTokenOf(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("content", "개인적인 사용감을 적은 것입니다."))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("appealId").asLong();
    }

    protected ResultActions selfDelete(Long postId) throws Exception {
        return mockMvc.perform(delete("/v1/creator/posts/" + postId).header(HttpHeaders.AUTHORIZATION, ownerTokenOf(postId)));
    }

    protected ResultActions downloadOriginals(Long postId, String creatorToken) throws Exception {
        return mockMvc.perform(get("/v1/creator/posts/" + postId + "/originals")
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    /** 노출 중지 → 이의 신청 → 반려까지 — 반려된 이의 신청 id */
    protected Long rejectedPost(Long postId, String comment) throws Exception {
        suspend(postId);
        Long appealId = appeal(postId);
        reject(appealId, Map.of("comment", comment)).andExpect(status().isOk());
        return appealId;
    }

    // ------------------------------------------------------------------ 시각 조작 · 원천 조회

    protected void setPublishedAt(Long postId, LocalDateTime at) {
        jdbc.update("UPDATE post SET published_at = ? WHERE post_id = ?", at, postId);
    }

    protected void setAppealDeadline(Long postId, LocalDateTime at) {
        jdbc.update("UPDATE post_suspension SET appeal_deadline = ? WHERE post_id = ? AND resolution IS NULL", at, postId);
    }

    protected void setSuspendedAt(Long postId, LocalDateTime at) {
        jdbc.update("UPDATE post_suspension SET suspended_at = ? WHERE post_id = ? AND resolution IS NULL", at, postId);
    }

    protected void setAppealSubmittedAt(Long postId, LocalDateTime at) {
        jdbc.update("UPDATE post_appeal SET submitted_at = ? WHERE post_id = ?", at, postId);
    }

    protected void setGraceUntil(Long postId, LocalDateTime at) {
        jdbc.update("UPDATE post_appeal SET grace_until = ? WHERE post_id = ?", at, postId);
    }

    protected void softDelete(Long postId, PostDeleteReason reason, LocalDateTime deletedAt) {
        inTransaction(() -> {
            Post post = postRepository.findById(postId).orElseThrow();
            post.softDelete(reason, deletedAt, deletedAt.plusMonths(6));
            return postRepository.save(post);
        });
    }

    protected int notificationCount(Long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post_notification_log WHERE post_id = ?", Integer.class, postId);
    }

    protected int notificationCount(Long postId, String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post_notification_log WHERE post_id = ? AND event_type = ?",
                Integer.class, postId, eventType);
    }

    protected Map<String, Object> row(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    protected JsonNode latestPayload(Long postId, String eventType) throws Exception {
        String payload = jdbc.queryForObject("SELECT payload FROM post_notification_log WHERE post_id = ? AND event_type = ? "
                + "ORDER BY log_id DESC LIMIT 1", String.class, postId, eventType);
        return objectMapper.readTree(payload);
    }

    protected List<String> reportStatuses(Long postId) {
        return jdbc.queryForList("SELECT status FROM post_report WHERE post_id = ? ORDER BY reported_at, post_report_id",
                String.class, postId);
    }

    /** 기한 경과 — 열린 조치의 기한을 1분 전으로 옮기고 만료 배치를 직접 부른다 */
    protected void expireSuspension(Long postId) {
        setAppealDeadline(postId, LocalDateTime.now().minusMinutes(1));
        postLifecycleService.expireOverdueSuspensions(LocalDateTime.now(), 100);
    }

    protected static LocalDateTime at(JsonNode node) {
        return node == null || node.isNull() ? null : LocalDateTime.parse(node.asText());
    }

    protected static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.get(field).isNull() ? null : item.get(field).asText()));
        return values;
    }
}
