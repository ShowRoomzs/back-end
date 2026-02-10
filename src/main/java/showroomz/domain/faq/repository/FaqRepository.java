package showroomz.domain.faq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 타입별 FAQ 목록 조회 (노출 가능한 것만)
    List<Faq> findAllByTypeAndIsVisibleTrue(InquiryType type);

    // 전체 목록 조회 (노출 가능한 것만)
    List<Faq> findAllByIsVisibleTrue();
}

