package showroomz.global.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class PaginationInfo {
    private final int currentPage;
    private final int totalPages;
    private final long totalResults;
    private final int limit;
    private final boolean hasNext;  // 다음 페이지 존재 여부 (선택사항)

    public PaginationInfo(Page<?> page) {
        this.currentPage = page.getNumber() + 1; // 0-based index를 1-based로 변환
        this.totalPages = page.getTotalPages();
        this.totalResults = page.getTotalElements();
        this.limit = page.getSize();
        this.hasNext = page.hasNext();
    }
}

