package showroomz.domain.faq.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

public interface FaqRepositoryCustom {

    Page<Faq> findAdminFaqList(FaqCategory category, String keyword, Pageable pageable);

    void shiftOrderDownAfterDelete(Integer deletedOrder);
}
