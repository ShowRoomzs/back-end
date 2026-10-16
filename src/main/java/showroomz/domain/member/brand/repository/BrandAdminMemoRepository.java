package showroomz.domain.member.brand.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.brand.entity.BrandAdminMemo;

import java.util.Optional;

public interface BrandAdminMemoRepository extends JpaRepository<BrandAdminMemo, Long> {

    Page<BrandAdminMemo> findByMarketIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long marketId, Pageable pageable);

    long countByMarketIdAndDeletedAtIsNull(Long marketId);

    Optional<BrandAdminMemo> findByIdAndMarketIdAndDeletedAtIsNull(Long id, Long marketId);
}
