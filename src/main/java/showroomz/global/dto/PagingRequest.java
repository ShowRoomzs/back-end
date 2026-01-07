package showroomz.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@Setter
public class PagingRequest {

    @Schema(description = "페이지 번호 (1부터 시작)", example = "1")
    private int page = 1;

    @Schema(description = "페이지당 항목 수", example = "20")
    private int size = 20;

    // PageRequest 객체로 변환하는 편의 메서드
    public Pageable toPageable() {
        // page가 0보다 작으면 1로 보정, Spring Data JPA는 0부터 시작하므로 -1 처리
        int pageNumber = page > 0 ? page - 1 : 0;
        return PageRequest.of(pageNumber, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    
    // 정렬 조건을 동적으로 받고 싶을 때 사용하는 오버로딩 메서드
    public Pageable toPageable(Sort sort) {
        int pageNumber = page > 0 ? page - 1 : 0;
        return PageRequest.of(pageNumber, size, sort);
    }
}

