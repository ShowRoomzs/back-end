package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.entity.AnswerTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnswerTemplateRepository extends JpaRepository<AnswerTemplate, Long> {

    Optional<AnswerTemplate> findByIdAndSellerId(@Param("id") Long id, @Param("sellerId") Long sellerId);

    List<AnswerTemplate> findAllByIdInAndSellerId(Collection<Long> ids, Long sellerId);

    @Query(value = "SELECT at FROM AnswerTemplate at " +
                   "WHERE at.seller.id = :sellerId " +
                   "AND (:isActive IS NULL OR at.isActive = :isActive) " +
                   "AND (:category IS NULL OR at.category = :category) " +
                   "AND (:keyword IS NULL OR :keyword = '' OR at.title LIKE %:keyword%)",
           countQuery = "SELECT COUNT(at) FROM AnswerTemplate at " +
                        "WHERE at.seller.id = :sellerId " +
                        "AND (:isActive IS NULL OR at.isActive = :isActive) " +
                        "AND (:category IS NULL OR at.category = :category) " +
                        "AND (:keyword IS NULL OR :keyword = '' OR at.title LIKE %:keyword%)")
    Page<AnswerTemplate> findTemplates(
            @Param("sellerId") Long sellerId,
            @Param("isActive") Boolean isActive,
            @Param("category") MarketInquiryFilterType category,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
