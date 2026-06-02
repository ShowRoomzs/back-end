package showroomz.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Schema(description = "페이징 메타데이터")
public class PaginationInfo {
    @Schema(description = "현재 페이지 (1부터)", example = "1")
    private final int currentPage;
    @Schema(description = "전체 페이지 수", example = "5")
    private final int totalPages;
    @Schema(description = "전체 항목 수", example = "80")
    private final long totalResults;
    @Schema(description = "limit (페이지당 항목 수)", example = "20")
    private final int limit;
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private final boolean hasNext;

    public PaginationInfo(Page<?> page) {
        this.currentPage = page.getNumber() + 1;
        this.totalPages = page.getTotalPages();
        this.totalResults = page.getTotalElements();
        this.limit = page.getSize();
        this.hasNext = page.hasNext();
    }

    /**
     * 고정 페이징 값으로 생성 (예: Top 10 응답 등)
     */
    public PaginationInfo(int currentPage, int totalPages, long totalResults, int limit, boolean hasNext) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalResults = totalResults;
        this.limit = limit;
        this.hasNext = hasNext;
    }
}
