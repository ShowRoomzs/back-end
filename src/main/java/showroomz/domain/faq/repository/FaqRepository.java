package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long>, FaqRepositoryCustom {

    List<Faq> findAllByIdIn(List<Long> ids);

    long countByCategory(FaqCategory category);

    boolean existsByCategoryAndDisplayOrderInAndIdNotIn(FaqCategory category, List<Integer> displayOrders, List<Long> ids);
}
