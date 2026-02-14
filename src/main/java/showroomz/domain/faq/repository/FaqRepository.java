package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import showroomz.domain.faq.entity.Faq;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 노출 가능한 FAQ 전체 목록 조회
    List<Faq> findAllByIsVisibleTrue();

    // 노출 가능한 FAQ의 카테고리 목록 (중복 제거, 가나다순)
    @Query("SELECT DISTINCT f.category FROM Faq f WHERE f.isVisible = true ORDER BY f.category")
    List<String> findDistinctCategoriesByIsVisibleTrue();
}

