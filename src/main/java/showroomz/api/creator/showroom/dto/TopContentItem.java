package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;

import java.time.LocalDateTime;

/** §22-4 인기 콘텐츠 TOP 5 한 줄 — 판매·정산 수치는 넣지 않는다(#6·#7 소관). */
@Getter
@Schema(description = "인기 콘텐츠 항목")
public class TopContentItem {

    /** 제목이 없는 게시물을 알아보는 단서 — 대표 사진과 이 조각뿐이다 */
    private static final int EXCERPT_LENGTH = 40;

    @Schema(description = "순위(1부터)", example = "1")
    private final Integer rank;

    @Schema(description = "게시물 ID", example = "301")
    private final Long postId;

    /**
     * 대표 사진 — 일반 게시물에는 제목이 없으므로(§24-3) 썸네일이 첫 번째 식별 수단이다.
     * 예전에는 {@code post.getTitle()}을 썼는데, 제목 컬럼이 사라진 지금 그대로 두면 순위표가 전부 빈칸이 된다.
     */
    @Schema(description = "대표 사진 URL")
    private final String thumbnailUrl;

    @Schema(description = "본문 앞부분(40자)", example = "여름 끝 무너진 장벽, 3주 루틴")
    private final String excerpt;

    /** 게시일 — {@code createdAt}이 아니라 실제로 세상에 나온 시각이다 */
    @Schema(description = "게시일", example = "2026-08-10T09:12:00")
    private final LocalDateTime publishedAt;

    @Schema(description = "노출 수 — 게시물에 누적된 값", example = "2840")
    private final Long viewCount;

    @Schema(description = "좋아요 수 — 게시물에 누적된 값", example = "24")
    private final Long likeCount;

    public TopContentItem(Integer rank, Post post) {
        this.rank = rank;
        this.postId = post.getId();
        PostImage representative = post.getRepresentativeImage();
        this.thumbnailUrl = representative == null ? null : representative.getImageUrl();
        this.excerpt = excerptOf(post.getContent());
        this.publishedAt = post.getPublishedAt();
        this.viewCount = post.getImpressionCount();
        this.likeCount = post.getLikeCount();
    }

    private static String excerptOf(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        return flattened.length() <= EXCERPT_LENGTH ? flattened : flattened.substring(0, EXCERPT_LENGTH) + "…";
    }
}
