package showroomz.global.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final PaginationInfo pageInfo;

    public PageResponse(Page<T> page) {
        this.content = page.getContent();
        this.pageInfo = new PaginationInfo(page);
    }
    
    // 데이터 리스트와 Page 객체가 따로 존재할 경우(예: DTO 변환 후)를 위한 생성자
    public PageResponse(List<T> content, Page<?> page) {
        this.content = content;
        this.pageInfo = new PaginationInfo(page);
    }
}

