package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;
import java.util.Optional;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByIsVisibleTrueOrderByDisplayOrderAscIdAsc();

    List<Faq> findAllByIsVisibleTrueAndCategoryOrderByDisplayOrderAscIdAsc(FaqCategory category);

    List<Faq> findAllByIsVisibleTrueAndQuestionContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(String keyword);

    List<Faq> findAllByIdIn(List<Long> ids);

    Optional<Faq> findTopByOrderByDisplayOrderDescIdDesc();

    List<Faq> findAllByIsVisibleTrueAndCategoryAndQuestionContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(FaqCategory category, String keyword);
}

