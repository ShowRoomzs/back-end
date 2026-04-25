package showroomz.domain.faq.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;
import java.util.Optional;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByOrderByDisplayOrderAscIdAsc();

    List<Faq> findAllByCategoryOrderByDisplayOrderAscIdAsc(FaqCategory category);

    List<Faq> findAllByQuestionContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(String keyword);

    List<Faq> findAllByIdIn(List<Long> ids);

    Optional<Faq> findTopByOrderByDisplayOrderDescIdDesc();

    List<Faq> findAllByCategoryAndQuestionContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(FaqCategory category, String keyword);

    Page<Faq> findByCategory(FaqCategory category, Pageable pageable);

    @Query("SELECT f FROM Faq f WHERE LOWER(f.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.answer) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Faq> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT f FROM Faq f WHERE f.category = :category AND (LOWER(f.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.answer) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Faq> findByCategoryAndKeyword(@Param("category") FaqCategory category, @Param("keyword") String keyword, Pageable pageable);
}

