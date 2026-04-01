package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.entity.AnswerTemplate;

public interface AnswerTemplateRepository extends JpaRepository<AnswerTemplate, Long> {

    @Query(value = "SELECT at FROM AnswerTemplate at " +
                   "WHERE at.seller.id = :sellerId " +
                   "AND at.isActive = true " +
                   "AND (:category IS NULL OR at.category = :category) " +
                   "AND (:keyword IS NULL OR :keyword = '' OR at.title LIKE %:keyword%)",
           countQuery = "SELECT COUNT(at) FROM AnswerTemplate at " +
                        "WHERE at.seller.id = :sellerId " +
                        "AND at.isActive = true " +
                        "AND (:category IS NULL OR at.category = :category) " +
                        "AND (:keyword IS NULL OR :keyword = '' OR at.title LIKE %:keyword%)")
    Page<AnswerTemplate> findActiveTemplates(
            @Param("sellerId") Long sellerId,
            @Param("category") MarketInquiryFilterType category,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
