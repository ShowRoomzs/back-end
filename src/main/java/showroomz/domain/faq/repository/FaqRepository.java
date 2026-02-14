package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.faq.entity.Faq;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 노출 가능한 FAQ 전체 목록 조회
    List<Faq> findAllByIsVisibleTrue();
}

