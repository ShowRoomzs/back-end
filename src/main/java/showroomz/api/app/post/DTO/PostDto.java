package showroomz.api.app.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class PostDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResponse {
        private Long postId;
        private Long showroomId;
        private String showroomName;
        private String showroomImageUrl;
        private String title;
        private String content;
        private String imageUrl;
        private Long viewCount;
        private Boolean isWishlisted;
        private Long wishlistCount;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    /** PostListItem을 감싸는 피드 아이템 래퍼. contentType으로 추후 추첨 등 확장 가능 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedItemResponse {
        @Builder.Default
        private String contentType = "POST";
        private PostListItem post;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListItem {
        private Long postId;
        private Long showroomId;
        private String showroomName;
        private String showroomImageUrl;
        private String title;
        private String imageUrl;
        private Long viewCount;
        private Boolean isWishlisted;
        private Long wishlistCount;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
