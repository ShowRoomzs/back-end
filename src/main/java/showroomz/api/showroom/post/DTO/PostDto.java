package showroomz.api.showroom.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class PostDto {

    @Schema(description = "게시글 작성 요청 (포스트에 상품 등록 가능)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        @Schema(description = "제목", example = "신상품 출시 소식", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 최대 200자입니다.")
        private String title;

        @Schema(description = "본문 내용", example = "이번 주 신상품을 소개합니다.")
        private String content;

        @Schema(description = "게시글 대표 이미지 URL")
        private String imageUrl;

        @Schema(description = "포스트에 등록할 상품 ID 목록 (본인 마켓 상품만 가능, 이미지 등록과 둘 중 하나만 가능)")
        private List<Long> productIds;
    }

    @Schema(description = "게시글 작성 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostResponse {
        @Schema(description = "게시글 ID", example = "1")
        private Long postId;
        @Schema(description = "제목")
        private String title;
        @Schema(description = "본문 내용")
        private String content;
        @Schema(description = "게시글 대표 이미지 URL")
        private String imageUrl;

        @Schema(description = "생성 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }

    @Schema(description = "게시글 수정 요청 (포스트에 등록된 상품 목록 변경 가능)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        @Schema(description = "제목", example = "신상품 출시 소식", maxLength = 200)
        @Size(max = 200, message = "제목은 최대 200자입니다.")
        private String title;

        @Schema(description = "본문 내용")
        private String content;

        @Schema(description = "게시글 대표 이미지 URL")
        private String imageUrl;

        @Schema(description = "전시 여부")
        private Boolean isDisplay;

        @Schema(description = "포스트에 등록할 상품 ID 목록 (제공 시 기존 매핑 제거 후 재등록, 본인 마켓 상품만 가능)")
        private List<Long> productIds;
    }

    @Schema(description = "게시글 수정 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostResponse {
        @Schema(description = "게시글 ID", example = "1")
        private Long postId;
        @Schema(description = "제목")
        private String title;
        @Schema(description = "본문 내용")
        private String content;
        @Schema(description = "게시글 대표 이미지 URL")
        private String imageUrl;
        @Schema(description = "전시 여부")
        private Boolean isDisplay;

        @Schema(description = "수정 일시", example = "2026-03-04T13:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    @Schema(description = "게시글 상세 응답 (크리에이터용)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResponse {
        @Schema(description = "게시글 ID", example = "1")
        private Long postId;
        @Schema(description = "마켓 ID", example = "1")
        private Long marketId;
        @Schema(description = "마켓명")
        private String marketName;
        @Schema(description = "제목")
        private String title;
        @Schema(description = "본문 내용")
        private String content;
        @Schema(description = "게시글 대표 이미지 URL")
        private String imageUrl;
        @Schema(description = "조회수")
        private Long viewCount;
        @Schema(description = "위시리스트 수")
        private Long wishlistCount;
        @Schema(description = "전시 여부")
        private Boolean isDisplay;

        @Schema(description = "생성 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @Schema(description = "수정 일시", example = "2026-03-04T13:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    @Schema(description = "게시글 목록 항목")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListItem {
        @Schema(description = "게시글 ID", example = "1")
        private Long postId;
        @Schema(description = "제목")
        private String title;
        @Schema(description = "게시글 대표 이미지 URL")
        private String imageUrl;
        @Schema(description = "조회수")
        private Long viewCount;
        @Schema(description = "위시리스트 수")
        private Long wishlistCount;
        @Schema(description = "전시 여부")
        private Boolean isDisplay;

        @Schema(description = "생성 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
