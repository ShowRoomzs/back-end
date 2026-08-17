package showroomz.api.app.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소비자에게 나가는 게시물 응답.
 *
 * <p>구버전 대비 사라진 것 — {@code title}(일반 게시물에는 제목이 없다),
 * {@code registeredProducts}(상품이 붙는 것은 공구 게시물이고 아직 만들지 않았다).
 * 새로 생긴 것 — {@code aspectRatio}와 {@code imageCount}.
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
        @Schema(description = "콘텐츠 타입 — GENERAL(일반 게시물) / GROUP_BUY(공구 게시물, 예정)",
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
    }
}
