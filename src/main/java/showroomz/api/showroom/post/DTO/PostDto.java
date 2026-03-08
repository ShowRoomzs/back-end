package showroomz.api.showroom.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class PostDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 최대 200자입니다.")
        private String title;

        private String content;

        private String imageUrl;

        /** 등록할 상품 ID 목록 (이미지 등록과 둘 중 하나만 가능) */
        private List<Long> productIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostResponse {
        private Long postId;
        private String title;
        private String content;
        private String imageUrl;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        @Size(max = 200, message = "제목은 최대 200자입니다.")
        private String title;

        private String content;

        private String imageUrl;

        private Boolean isDisplay;

        /** 수정할 상품 ID 목록 (제공 시 기존 매핑 제거 후 재등록) */
        private List<Long> productIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostResponse {
        private Long postId;
        private String title;
        private String content;
        private String imageUrl;
        private Boolean isDisplay;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResponse {
        private Long postId;
        private Long marketId;
        private String marketName;
        private String title;
        private String content;
        private String imageUrl;
        private Long viewCount;
        private Long wishlistCount;
        private Boolean isDisplay;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListItem {
        private Long postId;
        private String title;
        private String imageUrl;
        private Long viewCount;
        private Long wishlistCount;
        private Boolean isDisplay;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
