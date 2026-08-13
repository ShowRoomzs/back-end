package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.faq.entity.Faq;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long>, FaqRepositoryCustom {

    List<Faq> findAllByIdIn(List<Long> ids);

    long countByCategory(CsCategory category);

    boolean existsByCategoryAndDisplayOrderInAndIdNotIn(CsCategory category, List<Integer> displayOrders, List<Long> ids);
}
