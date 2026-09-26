package showroomz.api.app.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.service.GroupBuyPostCard;
import showroomz.domain.groupbuy.type.GroupBuyProductState;
import showroomz.domain.groupbuy.type.GroupBuySaleState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소비자에게 나가는 게시물 응답.
 *
 * <p>구버전 대비 사라진 것 — {@code title}(일반 게시물에는 제목이 없다),
 * {@code registeredProducts}(상품은 공구 게시물의 {@code groupBuy.products}로 옮겨졌다).
 * 새로 생긴 것 — {@code aspectRatio}와 {@code imageCount}, 공구 게시물의 {@code groupBuy} 블록.
 *
 * <p>공구 게시물은 사진이 없으므로 {@code imageUrls: []} · {@code aspectRatio: null}로 나가고, 제목·판매 상태·상품은
 * {@code groupBuy} 블록에 있다. 일반 게시물은 {@code groupBuy: null}이고 기존 필드는 그대로다(공구 게시물 설계 5-2).
 *
 * <p>{@code aspectRatio}를 서버가 내려주는 이유 — §24-2에 따라 게시물마다 높이가 다르다
 * (1.91:1 ~ 4:5 사이 임의 값). <b>고정 높이 카드로 구현하면 안 되고</b> 클라이언트가 첫 사진을
 * 받아 재기 전에 자리를 잡을 수 있어야 피드가 튀지 않는다.
 *
 * <p>용어도 기획에 맞췄다 — {@code wishlistCount}/{@code isWishlisted}는 상품 위시리스트와 같은 말이라
 * 게시물 지표에서 무엇의 수인지 흐려졌다. {@code likeCount}/{@code isLiked}로 통일한다.
 */
public class PostDto {

    @Schema(description = "게시글 상세 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResponse {
        @Schema(description = "콘텐츠 타입 — GENERAL(일반 게시물) / GROUP_BUY(공구 게시물)", example = "GENERAL")
        private String contentType;
        @Schema(description = "게시글 ID", example = "123")
        private Long postId;
        @Schema(description = "쇼룸 ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "쇼룸 대표 이미지 URL")
        private String showroomImageUrl;
        @Schema(description = "본문 — 없을 수 있다(사진만 있는 게시물)")
        private String content;
        @Schema(description = "게시글 이미지 URL 목록 — 배열 순서가 노출 순서이고 첫 장이 대표 사진")
        private List<String> imageUrls;
        @Schema(description = "사진 장수", example = "5")
        private Integer imageCount;
        @Schema(description = "게시물 비율(가로/세로) — 1.9100 ~ 0.8000. 카드 높이를 이 값으로 잡는다",
                example = "0.8000")
        private BigDecimal aspectRatio;
        @Schema(description = "노출 수", example = "532")
        private Long impressionCount;
        @Schema(description = "현재 사용자 좋아요 여부", example = "true")
        private Boolean isLiked;
        @Schema(description = "좋아요 수", example = "12")
        private Long likeCount;
        @Schema(description = "새 좋아요가 막힌 게시물인지 — true면 해제만 된다(마감된 공구). 하트를 눌러도 새로 걸리지 않는다",
                example = "false")
        private Boolean likeLocked;

        @Schema(description = "공구 블록 — 공구 게시물만 있고 일반 게시물은 null. 상세는 마감이어도 상품 전부를 내린다")
        private GroupBuyBlock groupBuy;

        @Schema(description = "게시 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        @Schema(description = "수정 일시", example = "2026-03-04T13:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    /**
     * 피드 아이템 래퍼.
     *
     * <p>{@code contentType}이 판별자다. 공구 게시물이 들어오면 값이 {@code GROUP_BUY}로 늘어나고,
     * 쇼룸 피드(공구 상단 고정 + 일반 최신순)가 <b>응답 구조를 바꾸지 않고</b> 확장된다.
     */
    @Schema(description = "피드 아이템 래퍼 — contentType으로 게시물 종류를 구분한다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedItemResponse {
        @Schema(description = "콘텐츠 타입 — GENERAL(일반 게시물) / GROUP_BUY(공구 게시물). GROUP_BUY면 post.groupBuy가 채워진다",
                example = "GENERAL")
        @Builder.Default
        private String contentType = "GENERAL";
        @Schema(description = "게시글 목록 항목")
        private PostListItem post;
    }

    @Schema(description = "게시글 목록 항목")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListItem {
        @Schema(description = "게시글 ID", example = "123")
        private Long postId;
        @Schema(description = "쇼룸 ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "쇼룸 대표 이미지 URL")
        private String showroomImageUrl;

        /**
         * C1 — 카드 헤더의 팔로우 버튼은 <b>미팔로우 쇼룸에만</b> 붙는다. 이미 팔로우한 대상에게
         * 버튼 자리를 내주지 않고(팔로우 취소는 C2·C4에서 한다), 추천 피드에서만 실질적으로 뜬다.
         */
        @Schema(description = "현재 사용자가 이 쇼룸을 팔로우 중인지 — false일 때만 카드에 팔로우 버튼을 그린다",
                example = "false")
        private Boolean isFollowing;

        /**
         * C1 — 카드 헤더 아바타의 <b>로즈 링</b>이다. "지금 살 수 있는 공구가 있다"는 신호이고,
         * C2 팔로잉·C14 검색의 아바타 규칙과 같은 값이다(§02).
         *
         * <p>게시물이 아니라 <b>쇼룸</b>의 상태다 — 일반 게시물 카드에도 그 쇼룸이 공구를 열고 있으면
         * 링이 붙는다. C4 쇼룸 안에서는 모든 카드가 같은 쇼룸이라 링을 그리지 않는다(클라이언트 판단).
         */
        @Schema(description = "이 쇼룸이 진행 중인 공구를 갖고 있는지 — 아바타 로즈 링 표시용", example = "true")
        private Boolean hasOngoingGroupBuy;

        @Schema(description = "본문 — 없을 수 있다")
        private String content;
        @Schema(description = "게시글 이미지 URL 목록 (순서대로)")
        private List<String> imageUrls;
        @Schema(description = "사진 장수", example = "5")
        private Integer imageCount;
        @Schema(description = "게시물 비율(가로/세로)", example = "0.8000")
        private BigDecimal aspectRatio;
        @Schema(description = "노출 수", example = "532")
        private Long impressionCount;
        @Schema(description = "현재 사용자 좋아요 여부", example = "false")
        private Boolean isLiked;
        @Schema(description = "좋아요 수", example = "12")
        private Long likeCount;

        /**
         * C3 — 마감된 공구는 좋아요 목록에 <b>남되</b> 하트를 회색으로 낮추고 해제만 허용한다.
         * 서버가 {@code POST /wishlist}를 거절하는 것과 같은 규칙을 클라이언트가 미리 그릴 수 있게
         * 내려준다. 값이 정책({@code PostPolicy.canLike})에서 나오므로 둘이 어긋날 수 없다.
         */
        @Schema(description = "새 좋아요가 막힌 게시물인지 — true면 해제만 된다(마감된 공구)", example = "false")
        private Boolean likeLocked;

        @Schema(description = "게시 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        @Schema(description = "공구 블록 — 공구 게시물만 있고 일반 게시물은 null. 목록에서 마감(CLOSED)이면 products는 []")
        private GroupBuyBlock groupBuy;
    }

    /**
     * 공구 블록 — C1·C3·C4·C5가 같은 모양을 쓴다(공구 게시물 설계 5-2). 화면별 차이는 상품을 몇 개 그리는가뿐이라,
     * 목록에서도 상품을 <b>전부</b> 내린다 — C1·C4의 「상품 N개 더보기」가 추가 호출 없이 인라인으로 펼친다.
     *
     * <p>배지 문구는 서버가 정하지 않는다 — C3 「공구 종료」 / C5 「공구 마감」처럼 화면마다 다르므로 {@code saleState}만 내린다.
     */
    @Schema(description = "공구 블록 — 공구 게시물에만 있다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupBuyBlock {
        @Schema(description = "공구 ID", example = "77")
        private Long groupBuyId;
        @Schema(description = "제목 — 공구 게시물에만 있다", example = "여름 끝 무너진 장벽, 3주면 돌아옵니다")
        private String title;
        @Schema(description = """
                판매 상태 — ON_SALE · PARTIALLY_SOLD_OUT · SOLD_OUT · CLOSED.
                ON_SALE·PARTIALLY_SOLD_OUT은 「공동구매 D-n」, SOLD_OUT은 「품절」, CLOSED는 「공구 마감」(C3는 「공구 종료」) 배지""",
                example = "ON_SALE")
        private GroupBuySaleState saleState;
        @Schema(description = "D-day — KST 날짜 차이, 마감 당일 0. CLOSED면 null", example = "3", nullable = true)
        private Integer dDay;
        @Schema(description = "종료 예정 일시 — 시분 단위 표시가 필요할 때", example = "2026-09-29T23:59:59")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endAt;
        @Schema(description = "대가관계 표시 — 공구 게시물에 항상 붙는다")
        private AdDisclosure adDisclosure;
        @Schema(description = "상품 수 — 항상 실제 개수. 「상품 N개 더보기」 문구의 근거", example = "3")
        private Integer productCount;
        @Schema(description = "상품 행 — 계약 상품 순서. 목록에서 CLOSED면 [](글만 표시), 상세는 마감이어도 전부")
        private List<GroupBuyProductItem> products;

        /** 게시물의 {@code dDay} 필드명 그대로 직렬화한다 — Lombok 게터 {@code getDDay}를 Jackson은 {@code dday}로 읽는다. */
        @JsonProperty("dDay")
        public Integer getDDay() {
            return dDay;
        }

        public static GroupBuyBlock of(GroupBuyPostCard card, boolean includeProducts) {
            return GroupBuyBlock.builder()
                    .groupBuyId(card.groupBuyId())
                    .title(card.title())
                    .saleState(card.saleState())
                    .dDay(card.dDay())
                    .endAt(card.endAt())
                    .adDisclosure(new AdDisclosure(AdDisclosure.LABEL, card.adDisclosure()))
                    .productCount(card.products().size())
                    .products(includeProducts
                            ? card.products().stream().map(GroupBuyProductItem::of).toList()
                            : List.of())
                    .build();
        }
    }

    @Schema(description = "대가관계 표시")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdDisclosure {
        static final String LABEL = "유료 광고 포함";

        @Schema(description = "배지 문구", example = "유료 광고 포함")
        private String label;
        @Schema(description = "전문 — 노출 위치는 앱·법무가 정한다(미결 ⑥)",
                example = "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다")
        private String text;
    }

    @Schema(description = "공구 상품 행")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupBuyProductItem {
        @Schema(description = "상품 ID", example = "901")
        private Long productId;
        @Schema(description = "상품명 — 계약 시점 이름", example = "시카 리페어 앰플 30ml 리필 2개 세트 기획")
        private String name;
        @Schema(description = "썸네일 URL")
        private String thumbnailUrl;
        @Schema(description = "정가(취소선)", example = "38000")
        private Integer regularPrice;
        @Schema(description = "공구가", example = "24900")
        private Integer groupBuyPrice;
        @Schema(description = "할인율(%) — 서버가 C7과 같은 반올림으로 계산한다", example = "34")
        private Integer discountRate;
        @Schema(description = "행 상태 — ON_SALE · SOLD_OUT · CLOSED. ON_SALE이 아니면 흑백 + 라벨", example = "ON_SALE")
        private GroupBuyProductState state;
        @Schema(description = "C7 상품 상세로 갈 수 있는지 — false면 탭을 막는다", example = "true")
        private Boolean detailAvailable;

        static GroupBuyProductItem of(GroupBuyPostCard.Product product) {
            return GroupBuyProductItem.builder()
                    .productId(product.productId())
                    .name(product.name())
                    .thumbnailUrl(product.thumbnailUrl())
                    .regularPrice(product.regularPrice())
                    .groupBuyPrice(product.groupBuyPrice())
                    .discountRate(product.discountRate())
                    .state(product.state())
                    .detailAvailable(product.detailAvailable())
                    .build();
        }
    }
}
