package showroomz.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Schema(description = "페이지 정보")
public class PageInfo {
    @Schema(description = "현재 페이지", example = "1")
    private final int currentPage;

    @Schema(description = "페이지당 개수", example = "20")
    private final int pageSize;

    @Schema(description = "전체 결과 수", example = "485")
    private final long totalElements;

    @Schema(description = "전체 페이지 수", example = "10")
    private final int totalPages;

    @Schema(description = "마지막 페이지 여부", example = "false")
    private final boolean isLast;

    public PageInfo(Page<?> page) {
        this.currentPage = page.getNumber() + 1; // 0-based index를 1-based로 변환
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.isLast = page.isLast();
    }
}
