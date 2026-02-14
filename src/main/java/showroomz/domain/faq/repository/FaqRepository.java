package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByIsVisibleTrue();

    List<Faq> findAllByIsVisibleTrueAndCategory(FaqCategory category);

    List<Faq> findAllByIsVisibleTrueAndQuestionContainingIgnoreCase(String keyword);

    List<Faq> findAllByIsVisibleTrueAndCategoryAndQuestionContainingIgnoreCase(FaqCategory category, String keyword);
}

