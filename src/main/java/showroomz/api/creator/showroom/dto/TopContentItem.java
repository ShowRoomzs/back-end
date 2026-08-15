package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.post.entity.Post;

import java.time.LocalDateTime;

/** §22-4 인기 콘텐츠 TOP 5 한 줄 — 판매·정산 수치는 넣지 않는다(#6·#7 소관). */
@Getter
@Schema(description = "인기 콘텐츠 항목")
public class TopContentItem {

    @Schema(description = "순위(1부터)", example = "1")
    private final Integer rank;

    @Schema(description = "게시물 ID", example = "301")
    private final Long postId;

    @Schema(description = "제목", example = "여름 끝 무너진 장벽, 3주 루틴")
    private final String title;

    @Schema(description = "게시일", example = "2026-08-10T09:12:00")
    private final LocalDateTime publishedAt;

    @Schema(description = "노출 수 — 게시물에 누적된 값", example = "2840")
    private final Long viewCount;

    @Schema(description = "좋아요 수 — 게시물에 누적된 값", example = "24")
    private final Long likeCount;

    public TopContentItem(Integer rank, Post post) {
        this.rank = rank;
        this.postId = post.getId();
        this.title = post.getTitle();
        this.publishedAt = post.getCreatedAt();
        this.viewCount = post.getViewCount();
        this.likeCount = post.getWishlistCount();
    }
}
