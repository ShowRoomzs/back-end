package showroomz.api.app.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.type.PostStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공구 게시물 소비자 노출 — C4 고정 섹션 ⇔ 아바타 링 · 아래 피드 · 게시물 수 · C5 공구 블록(공구 게시물 설계 4-5 · 5 · 6절).
 */
@DisplayName("[통합] 소비자 공구 게시물 — C4 고정 섹션 · 아래 피드 · C5 공구 블록")
class UserGroupBuyPostIntegrationTest extends GroupBuyPostTestSupport {

    @Test
    @DisplayName("진행 중 — 고정 섹션에만 뜨고 링이 켜진다 · 같은 브랜드에 연결된 다른 쇼룸의 링은 꺼져 있다 · 카드에 공구 블록")
    void ongoingGoesToPinnedSection() throws Exception {
        Creator other = visibleCreator("다른_쇼룸", "other");
        connect(brand, other);
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();

        pinned(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$[0].post.postId").value(postId))
                .andExpect(jsonPath("$[0].post.imageUrls").isEmpty())
                .andExpect(jsonPath("$[0].post.aspectRatio").doesNotExist())
                .andExpect(jsonPath("$[0].post.hasOngoingGroupBuy").value(true))
                .andExpect(jsonPath("$[0].post.groupBuy.groupBuyId").value(groupBuy.getId()))
                .andExpect(jsonPath("$[0].post.groupBuy.title").value("글로우 크림 앵콜 공구 오픈"))
                .andExpect(jsonPath("$[0].post.groupBuy.saleState").value("PARTIALLY_SOLD_OUT"))
                .andExpect(jsonPath("$[0].post.groupBuy.dDay").value(4))
                .andExpect(jsonPath("$[0].post.groupBuy.adDisclosure.label").value("유료 광고 포함"))
                .andExpect(jsonPath("$[0].post.groupBuy.adDisclosure.text").value(startsWith("유료 광고 포함 · 글로우랩")))
                .andExpect(jsonPath("$[0].post.groupBuy.productCount").value(2))
                .andExpect(jsonPath("$[0].post.groupBuy.products.length()").value(2))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].name").value("글로우 크림 50ml"))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].groupBuyPrice").value(27_200))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].discountRate").value(20))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].state").value("ON_SALE"))
                .andExpect(jsonPath("$[0].post.groupBuy.products[1].state").value("SOLD_OUT"))
                .andExpect(jsonPath("$[0].post.likeLocked").value(false));

        lowerFeed(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        profile(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasOngoingGroupBuy").value(true))
                .andExpect(jsonPath("$.postCount").value(1));
        profile(other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasOngoingGroupBuy").value(false));
        // C1 추천 피드에는 진행 중 공구가 섞인다
        mockMvc.perform(get("/v1/user/feed/recommended").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].post.postId").value(hasItem(postId.intValue())));
    }

    @Test
    @DisplayName("종료 — 고정 섹션·링에서 빠지고 아래 피드에 글만(products = []) 남는다 · 상세는 상품 전부 CLOSED · 게시물 수 유지")
    void closedMovesToLowerFeed() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(endedAt), groupBuy.getId());
        assertThat(lifecycleService.end(groupBuy.getId(), LocalDateTime.now())).isTrue();
        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);

        pinned(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        profile(creator)
                .andExpect(jsonPath("$.hasOngoingGroupBuy").value(false))
                .andExpect(jsonPath("$.postCount").value(1));
        lowerFeed(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].post.postId").value(postId))
                .andExpect(jsonPath("$.content[0].post.groupBuy.saleState").value("CLOSED"))
                .andExpect(jsonPath("$.content[0].post.groupBuy.dDay").doesNotExist())
                .andExpect(jsonPath("$.content[0].post.groupBuy.productCount").value(2))
                .andExpect(jsonPath("$.content[0].post.groupBuy.products").isEmpty())
                .andExpect(jsonPath("$.content[0].post.likeLocked").value(true));
        detail(postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$.groupBuy.products.length()").value(2))
                .andExpect(jsonPath("$.groupBuy.products[0].state").value("CLOSED"))
                .andExpect(jsonPath("$.groupBuy.products[1].state").value("CLOSED"))
                .andExpect(jsonPath("$.groupBuy.products[0].detailAvailable").value(false));
        // C1 피드에는 마감 게시물을 싣지 않는다(미결 ⑧ 기본값)
        mockMvc.perform(get("/v1/user/feed/recommended").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));

        assertThat(lifecycleService.retirePost(groupBuy.getId(), endedAt.plusHours(72))).isEqualTo(1);
        lowerFeed(creator)
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        profile(creator)
                .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    @DisplayName("숨김 게시물은 진행 중이어도 고정 섹션·링에 잡히지 않는다")
    void hiddenIsNotOngoing() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        sync(groupBuy.getId(), LocalDateTime.now());

        pinned(creator).andExpect(jsonPath("$").isEmpty());
        profile(creator)
                .andExpect(jsonPath("$.hasOngoingGroupBuy").value(false))
                .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    @DisplayName("승인 대기·준비완료(예약) 게시물은 고정 섹션·링·피드 어디에도 없다")
    void notYetOpenIsInvisible() throws Exception {
        GroupBuy pending = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(pending.getId(), GroupBuyPostReviewStatus.PENDING, false);
        sync(pending.getId(), LocalDateTime.now());
        GroupBuy ready = ongoingGroupBuyOf("예약브랜드", creator);
        moveTo(ready.getId(), GroupBuyStatus.READY);
        seedPost(ready.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        sync(ready.getId(), LocalDateTime.now());

        pinned(creator).andExpect(jsonPath("$").isEmpty());
        lowerFeed(creator).andExpect(jsonPath("$.content").isEmpty());
        profile(creator)
                .andExpect(jsonPath("$.hasOngoingGroupBuy").value(false))
                .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    @DisplayName("고정 섹션은 공구 시작일(opened_at) 최신순이다")
    void pinnedOrderedByOpenedAt() throws Exception {
        GroupBuy older = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long olderPost = exposedPost(older).getPostId();
        GroupBuy newer = ongoingGroupBuyOf("신규브랜드", creator);
        Long newerPost = exposedPost(newer).getPostId();
        jdbc.update("UPDATE group_buy SET opened_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(5)), older.getId());
        jdbc.update("UPDATE group_buy SET opened_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)), newer.getId());

        pinned(creator)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].post.postId").value(newerPost))
                .andExpect(jsonPath("$[1].post.postId").value(olderPost));
    }

    @Test
    @DisplayName("없는 쇼룸 · 노출할 수 없는 쇼룸(등록 미완료)의 고정 섹션은 쇼룸 프로필과 같은 404")
    void unknownShowroom() throws Exception {
        mockMvc.perform(get(SHOWROOMS + 999_999 + "/group-buy-posts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWROOM_NOT_FOUND"));

        Creator unregistered = createCreator("주소없는_쇼룸", "noaddr");
        pinned(unregistered)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인 — 고정 섹션 · 아래 피드 · 상세 · 추천은 토큰 없이 열리고 isLiked·isFollowing은 false")
    void anonymousCanBrowse() throws Exception {
        Long postId = ongoingPost();

        pinned(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].post.isLiked").value(false))
                .andExpect(jsonPath("$[0].post.isFollowing").value(false));
        lowerFeed(creator).andExpect(status().isOk());
        mockMvc.perform(get(SHOWROOMS + "posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLiked").value(false))
                .andExpect(jsonPath("$.groupBuy.saleState").value("PARTIALLY_SOLD_OUT"));
        mockMvc.perform(get("/v1/user/feed/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].post.postId").value(hasItem(postId.intValue())));
    }

    @Test
    @DisplayName("로그인 — 고정 섹션 카드에 내 좋아요·팔로우 상태가 실린다")
    void pinnedCarriesViewerState() throws Exception {
        Long postId = ongoingPost();
        follow(viewer, creator);
        like(consumer, postId);

        mockMvc.perform(get(SHOWROOMS + creator.getId() + "/group-buy-posts")
                        .header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(jsonPath("$[0].post.isLiked").value(true))
                .andExpect(jsonPath("$[0].post.isFollowing").value(true))
                .andExpect(jsonPath("$[0].post.likeCount").value(1));
    }
}
