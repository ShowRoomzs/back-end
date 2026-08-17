package showroomz.domain.inquiry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;

import java.util.List;

public interface ProductInquiryHistoryRepository extends JpaRepository<ProductInquiryHistory, Long> {

    /** 처리 이력 — 최신순 (§23-3) */
    List<ProductInquiryHistory> findByInquiry_IdOrderByCreatedAtDescIdDesc(Long inquiryId);
}
