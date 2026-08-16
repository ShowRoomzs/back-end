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

        @Schema(description = "게시 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;
    }
}
