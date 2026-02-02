package showroomz.global.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Schema(description = "페이지 응답")
public class PageResponse<T> {

    @ArraySchema(schema = @Schema(description = "데이터 항목"))
    private final List<T> data;

    @Schema(description = "페이지 정보")
    private final PageInfo pageInfo;

    public PageResponse(Page<T> page) {
        this.data = page.getContent();
        this.pageInfo = new PageInfo(page);
    }
    
    // 데이터 리스트와 Page 객체가 따로 존재할 경우(예: DTO 변환 후)를 위한 생성자
    public PageResponse(List<T> data, Page<?> page) {
        this.data = data;
        this.pageInfo = new PageInfo(page);
    }
    
    // static factory 메서드
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page);
    }
}

