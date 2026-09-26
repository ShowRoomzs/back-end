package showroomz.api.app.post;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyPostCard;
import showroomz.domain.groupbuy.service.GroupBuyPostCardLoader;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공구 블록 조립 — 상품 행 · 품절 · C7 진입 · 판매 상태 보정 · 쿼리 수(공구 게시물 설계 3절 · 4-3 · 4-6 · 5-2 · 6-2).
 */
@DisplayName("[통합] 공구 블록 조립 — 상품 행 · 품절 · C7 진입 · 판매 상태 · 쿼리 수")
class GroupBuyPostCardIntegrationTest extends GroupBuyPostTestSupport {

    @Autowired
    private GroupBuyPostCardLoader cardLoader;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ── 상품 행 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("상품 행은 계약 순서 · 계약 시점 상품명·정가·공구가다 — 상품명이 바뀌어도 그대로, 썸네일만 현재 상품을 따른다")
    void productRowsFollowContractSnapshot() throws Exception {
        Long postId = ongoingPost();
        jdbc.update("UPDATE product SET name = ?, regular_price = ?, thumbnail_url = ? WHERE product_id = ?",
                "리뉴얼 크림 60ml", 50_000, "https://cdn.example.com/cream.jpg", cream.getProductId());

        detail(postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.products[0].productId").value(cream.getProductId()))
                .andExpect(jsonPath("$.groupBuy.products[0].name").value("글로우 크림 50ml"))
                .andExpect(jsonPath("$.groupBuy.products[0].regularPrice").value(34_000))
                .andExpect(jsonPath("$.groupBuy.products[0].groupBuyPrice").value(27_200))
                .andExpect(jsonPath("$.groupBuy.products[0].discountRate").value(20))
                .andExpect(jsonPath("$.groupBuy.products[0].thumbnailUrl").value("https://cdn.example.com/cream.jpg"))
                .andExpect(jsonPath("$.groupBuy.products[1].productId").value(serum.getProductId()))
                .andExpect(jsonPath("$.groupBuy.products[1].name").value("글로우 세럼 30ml"))
                .andExpect(jsonPath("$.groupBuy.products[1].groupBuyPrice").value(24_000))
                .andExpect(jsonPath("$.groupBuy.products[1].discountRate").value(20));
    }

    @Test
    @DisplayName("공구 게시물은 사진이 없다 — imageUrls [] · imageCount 0 · aspectRatio null · 대가관계 표시는 항상")
    void groupBuyPostShape() throws Exception {
        Long postId = ongoingPost();

        detail(postId)
                .andExpect(jsonPath("$.contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$.imageUrls").isEmpty())
                .andExpect(jsonPath("$.imageCount").value(0))
                .andExpect(jsonPath("$.aspectRatio").doesNotExist())
                .andExpect(jsonPath("$.content").value("여름 한정 앵콜 공구 — 크림·세럼 세트"))
                .andExpect(jsonPath("$.groupBuy.title").value("글로우 크림 앵콜 공구 오픈"))
                .andExpect(jsonPath("$.groupBuy.adDisclosure.text")
                        .value("유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다"));
    }

    @Test
    @DisplayName("일반 게시물은 contentType GENERAL · groupBuy null — 기존 응답 그대로")
    void generalPostHasNoGroupBuyBlock() throws Exception {
        Long postId = generalPost(creator, "아침 루틴", LocalDateTime.now().minusHours(1));

        detail(postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("GENERAL"))
                .andExpect(jsonPath("$.groupBuy").doesNotExist())
                .andExpect(jsonPath("$.aspectRatio").value(0.8));
        lowerFeed(creator)
                .andExpect(jsonPath("$.content[0].contentType").value("GENERAL"))
                .andExpect(jsonPath("$.content[0].post.groupBuy").doesNotExist());
    }

    // ── 품절 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("재고 있는 옵션이 하나라도 있으면 판매 중 — 모든 상품 판매 중이면 ON_SALE")
    void allInStockIsOnSale() throws Exception {
        setStock(serum, 3);
        Long postId = ongoingPost();

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.saleState").value("ON_SALE"))
                .andExpect(jsonPath("$.groupBuy.products[*].state").value(everyItem(is("ON_SALE"))));
    }

    @Test
    @DisplayName("모든 상품 품절 → SOLD_OUT 배지 · 행 전부 SOLD_OUT · D-day는 그대로 · 품절은 좋아요를 막지 않는다")
    void allSoldOut() throws Exception {
        setStock(cream, 0);
        Long postId = ongoingPost();

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.saleState").value("SOLD_OUT"))
                .andExpect(jsonPath("$.groupBuy.dDay").value(4))
                .andExpect(jsonPath("$.groupBuy.products[*].state").value(everyItem(is("SOLD_OUT"))))
                .andExpect(jsonPath("$.likeLocked").value(false));
        like(consumer, postId);
    }

    @Test
    @DisplayName("강제 품절은 재고가 남아도 품절이다")
    void forcedOutOfStock() throws Exception {
        setStock(serum, 3);
        jdbc.update("UPDATE product SET is_out_of_stock_forced = TRUE WHERE product_id = ?", cream.getProductId());
        Long postId = ongoingPost();

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.saleState").value("PARTIALLY_SOLD_OUT"))
                .andExpect(jsonPath("$.groupBuy.products[0].state").value("SOLD_OUT"))
                .andExpect(jsonPath("$.groupBuy.products[1].state").value("ON_SALE"));
    }

    @Test
    @DisplayName("옵션이 하나도 없는 상품도 품절이다 — C7 status.isOutOfStock과 같은 식")
    void productWithoutVariantsIsSoldOut() throws Exception {
        setStock(serum, 3);
        jdbc.update("DELETE FROM product_variant WHERE product_id = ?", cream.getProductId());
        Long postId = ongoingPost();

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.products[0].state").value("SOLD_OUT"))
                .andExpect(jsonPath("$.groupBuy.products[1].state").value("ON_SALE"));
    }

    // ── C7 진입 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("detailAvailable — 진열 중 ∧ 공구 연결일 때만 true · 미진열·연결 해제면 false(C7이 404를 내는 상품)")
    void detailAvailability() throws Exception {
        Long postId = ongoingPost();
        jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS', display_status = 'DISPLAY'");

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.products[0].detailAvailable").value(true))
                .andExpect(jsonPath("$.groupBuy.products[1].detailAvailable").value(true));

        jdbc.update("UPDATE product SET display_status = 'HIDDEN' WHERE product_id = ?", cream.getProductId());
        jdbc.update("UPDATE product SET group_buy_status = 'NOT_CONNECTED' WHERE product_id = ?", serum.getProductId());

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.products[0].detailAvailable").value(false))
                .andExpect(jsonPath("$.groupBuy.products[1].detailAvailable").value(false));
    }

    // ── 판매 상태 보정 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("종료 시각이 지났는데 스케줄러가 아직 못 닫은 구간 — CLOSED로 보정 · 좋아요 거절 · 진행 중(고정 섹션·링·C1)에서 빠진다")
    void endAtPassedBeforeScheduler() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), groupBuy.getId());

        detail(postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.saleState").value("CLOSED"))
                .andExpect(jsonPath("$.groupBuy.dDay").doesNotExist())
                .andExpect(jsonPath("$.groupBuy.products[*].state").value(everyItem(is("CLOSED"))))
                .andExpect(jsonPath("$.groupBuy.products[*].detailAvailable").value(everyItem(is(false))))
                .andExpect(jsonPath("$.likeLocked").value(true));
        mockMvc.perform(post(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isNotFound());
        pinned(creator).andExpect(jsonPath("$").isEmpty());
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(false));
        lowerFeed(creator)
                .andExpect(jsonPath("$.content[0].post.postId").value(postId))
                .andExpect(jsonPath("$.content[0].post.groupBuy.products").isEmpty());
        mockMvc.perform(get("/v1/user/feed/recommended").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
    }

    @Test
    @DisplayName("중단 예정(SUSPENSION_SCHEDULED)은 아직 팔린다 — 고정 섹션·링에 남고 좋아요도 받는다")
    void suspensionScheduledIsStillOngoing() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        moveTo(groupBuy.getId(), GroupBuyStatus.SUSPENSION_SCHEDULED);

        pinned(creator)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].post.groupBuy.saleState").value("PARTIALLY_SOLD_OUT"))
                .andExpect(jsonPath("$[0].post.likeLocked").value(false));
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(true));
        like(consumer, postId);
    }

    @Test
    @DisplayName("D-day — 마감 당일 0 · endAt은 초 단위 문자열로 함께 내린다")
    void dDayOnLastDay() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        LocalDateTime lastSecond = LocalDate.now().atTime(LocalTime.of(23, 59, 59));
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(lastSecond), groupBuy.getId());

        detail(postId)
                .andExpect(jsonPath("$.groupBuy.dDay").value(0))
                .andExpect(jsonPath("$.groupBuy.endAt").value(LocalDate.now() + "T23:59:59"));
    }

    // ── 쿼리 수 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로더는 공구 게시물 수와 무관하게 쿼리 3회다 — 게시물·공구·브랜드 / 계약 상품·상품 / 재고")
    void loaderQueryCountIsConstant() {
        Long first = ongoingPost();
        Long second = exposedPost(ongoingGroupBuyOf("브랜드둘", creator)).getPostId();
        Long third = exposedPost(ongoingGroupBuyOf("브랜드셋", creator)).getPostId();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            Map<Long, GroupBuyPostCard> one = transactionTemplate.execute(
                    tx -> cardLoader.load(List.of(first), LocalDateTime.now()));
            long singleCount = statistics.getPrepareStatementCount();

            statistics.clear();
            Map<Long, GroupBuyPostCard> three = transactionTemplate.execute(
                    tx -> cardLoader.load(List.of(first, second, third), LocalDateTime.now()));
            long pageCount = statistics.getPrepareStatementCount();

            assertThat(one).containsOnlyKeys(first);
            assertThat(three).containsOnlyKeys(first, second, third);
            assertThat(three.get(first).products()).hasSize(2);
            assertThat(three.get(second).products()).hasSize(1);
            assertThat(three.get(second).adDisclosure()).contains("브랜드둘");
            assertThat(singleCount).isEqualTo(3);
            assertThat(pageCount).isEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    @DisplayName("로더에 일반 게시물 id만 넘기면 결과가 비고, 빈 목록이면 쿼리를 내지 않는다")
    void loaderIgnoresGeneralPosts() {
        Long general = generalPost(creator, "일반", LocalDateTime.now());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            Map<Long, GroupBuyPostCard> cards = transactionTemplate.execute(
                    tx -> cardLoader.load(List.of(general), LocalDateTime.now()));
            assertThat(cards).isEmpty();
            statistics.clear();
            assertThat(cardLoader.load(List.of(), LocalDateTime.now())).isEmpty();
            assertThat(statistics.getPrepareStatementCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
