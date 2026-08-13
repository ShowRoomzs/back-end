package showroomz.domain.faq.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;
import java.util.Map;

public interface FaqRepositoryCustom {

    Page<Faq> findAdminFaqList(FaqCategory category, String keyword, Pageable pageable);

    List<Faq> findAppFaqList(FaqCategory category, String keyword);

    /** 카테고리 탭 건수 (기획 §19-2) — 검색어가 있으면 검색 결과 기준 */
    Map<FaqCategory, Long> countByCategoryGroup(String keyword);

    /** 신규 등록 시 해당 카테고리 전체를 한 칸씩 밀어 맨 위(1번) 자리를 비운다 (기획 §19-4) */
    void shiftOrderUpForInsert(FaqCategory category);

    /** 삭제 시 같은 카테고리에서 뒤쪽 순서를 한 칸씩 당긴다 (기획 §19-4·§19-6) */
    void shiftOrderDownAfterDelete(FaqCategory category, Integer deletedOrder);
}
