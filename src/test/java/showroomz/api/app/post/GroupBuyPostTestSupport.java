package showroomz.api.app.post;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.groupbuy.GroupBuyTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.service.GroupBuyPostExposure;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 공구 게시물 통합 테스트 픽스처(공구 게시물 설계서).
 *
 * <p>기본 상태 — 쇼룸 「글로우_지민」은 소비자에게 노출되고, 크림은 재고 5 · 세럼은 재고 0(일부 품절)이다.
 * 공구 기간은 {@code seedIn}을 따른다(진행 중이면 오늘 + 4일에 끝난다).
 *
 * <p><b>스케줄러가 테스트 중에도 돈다</b>(실제 현재 시각 · 1분 주기). 종료 시각은 최근으로 두고 「마감 게시물 내리기」 판정에는
 * 미래 시각을 넘긴다 — 종료 3일이 지난 게시중 행을 만들어 두면 실제 스케줄러가 먼저 내려 버릴 수 있다.
 */
abstract class GroupBuyPostTestSupport extends GroupBuyTestSupport {

    protected static final String SHOWROOMS = "/v1/user/showrooms/";

    @Autowired protected ProductVariantRepository productVariantRepository;
    @Autowired protected CreatorFollowRepository creatorFollowRepository;
    @Autowired protected PostLikeRepository postLikeRepository;

    protected Users viewer;
    protected String consumer;

    @BeforeEach
    void setUpGroupBuyPostFixtures() {
        makeVisible(creator, "jimin");
        stock(cream, 5);
        stock(serum, 0);
        viewer = createUser("viewer-mia", "미아");
        consumer = tokenOf(viewer);
    }

    // ------------------------------------------------------------------ 쇼룸 · 사용자

    /** 쇼룸 주소가 있어야 소비자에게 노출되는 쇼룸이다(PublicShowrooms). */
    protected void makeVisible(Creator showroom, String address) {
        jdbc.update("UPDATE creator SET showroom_address = ? WHERE creator_id = ?", address, showroom.getId());
    }

    protected Creator visibleCreator(String showroomName, String accountId) {
        Creator created = createCreator(showroomName, accountId);
        makeVisible(created, accountId);
        return created;
    }

    protected Users createUser(String username, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));
    }

    protected String tokenOf(Users user) {
        return bearerToken(user.getUsername(), RoleType.USER, user.getId());
    }

    protected void follow(Users user, Creator showroom) {
        creatorFollowRepository.save(new CreatorFollow(user, showroom));
    }

    // ------------------------------------------------------------------ 상품

    protected void stock(Product product, int quantity) {
        productVariantRepository.save(new ProductVariant(product, "기본", product.getRegularPrice(),
                product.getRegularPrice(), quantity, true));
    }

    protected void setStock(Product product, int quantity) {
        jdbc.update("UPDATE product_variant SET stock = ? WHERE product_id = ?", quantity, product.getProductId());
    }

    // ------------------------------------------------------------------ 게시물

    /** 승인된 게시물 + 노출 투영 — 진행 중 공구면 PUBLISHED가 된다(오픈 스케줄러가 하는 일). */
    protected GroupBuyPost exposedPost(GroupBuy groupBuy) {
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        sync(groupBuy.getId(), LocalDateTime.now());
        return post;
    }

    protected Long ongoingPost() {
        return exposedPost(seedIn(GroupBuyStatus.IN_PROGRESS)).getPostId();
    }

    /** 다른 브랜드의 진행 중 공구 — 같은 상품을 여러 공구에 걸지 않으려고 브랜드를 새로 만든다. */
    protected GroupBuy ongoingGroupBuyOf(String brandName, Creator showroom) {
        var otherBrand = fixture.createBrand(brandName + "@showroomz.test", brandName);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy groupBuy = seed(otherBrand, showroom, brandName + " 공구", now.minusDays(3), now.plusDays(4));
        moveTo(groupBuy.getId(), GroupBuyStatus.IN_PROGRESS);
        return reload(groupBuy.getId());
    }

    protected void sync(Long groupBuyId, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(tx -> GroupBuyPostExposure.sync(
                groupBuyRepository.findById(groupBuyId).orElseThrow(),
                groupBuyPostRepository.findByGroupBuyId(groupBuyId).orElseThrow(), now));
    }

    /**
     * 종결을 SQL로 옮기고 투영한다 — 종결 경로가 하는 일({@code GroupBuyTerminator})의 게시물 부분만 재현한다.
     * 기간 종료면 {@code end_at}도 {@code endedAt}으로 옮긴다.
     */
    protected void terminate(Long groupBuyId, GroupBuyStatus status, GroupBuyCloseType closeType,
                             LocalDateTime endedAt) {
        jdbc.update("UPDATE group_buy SET status = ?, close_type = ?, ended_at = ?, "
                        + "end_at = CASE WHEN ? THEN ? ELSE end_at END WHERE group_buy_id = ?",
                status.name(), closeType.name(), Timestamp.valueOf(endedAt),
                closeType == GroupBuyCloseType.COMPLETED, Timestamp.valueOf(endedAt), groupBuyId);
        sync(groupBuyId, LocalDateTime.now());
    }

    protected void endNow(Long groupBuyId, LocalDateTime endedAt) {
        terminate(groupBuyId, GroupBuyStatus.ENDED, GroupBuyCloseType.COMPLETED, endedAt);
    }

    protected Long generalPost(Creator showroom, String content, LocalDateTime publishedAt) {
        return postRepository.save(Post.published(showroom, content, new BigDecimal("0.8000"), publishedAt)).getId();
    }

    // ------------------------------------------------------------------ 좋아요

    protected void like(String token, Long postId) throws Exception {
        mockMvc.perform(post(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
    }

    protected void likedAt(Users user, Long postId, LocalDateTime at) {
        jdbc.update("UPDATE post_like SET created_at = ? WHERE user_id = ? AND post_id = ?",
                Timestamp.valueOf(at), user.getId(), postId);
    }

    // ------------------------------------------------------------------ 조회

    protected ResultActions detail(Long postId) throws Exception {
        return mockMvc.perform(get(SHOWROOMS + "posts/" + postId).header(HttpHeaders.AUTHORIZATION, consumer));
    }

    protected ResultActions pinned(Creator showroom) throws Exception {
        return mockMvc.perform(get(SHOWROOMS + showroom.getId() + "/group-buy-posts"));
    }

    protected ResultActions lowerFeed(Creator showroom) throws Exception {
        return mockMvc.perform(get(SHOWROOMS + showroom.getId() + "/posts"));
    }

    protected ResultActions profile(Creator showroom) throws Exception {
        return mockMvc.perform(get(SHOWROOMS + showroom.getId()));
    }
}
