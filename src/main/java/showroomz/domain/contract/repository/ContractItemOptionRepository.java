package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractItemOption;

import java.util.Collection;
import java.util.List;

public interface ContractItemOptionRepository extends JpaRepository<ContractItemOption, Long> {

    /**
     * 공구 계약의 옵션 행 중 주어진 옵션들 — 소비자 가격의 원천(가격 계획서 2절).
     *
     * <p>지워진 옵션(variant NULL)은 내부 조인으로 빠진다 — 살 수 없는 옵션이다. 같은 상품이 한 계약에 두 번
     * 실렸을 수 있어(검증이 막지 않는다) 항목 순서로 정렬해 호출자가 앞 행을 쓰게 한다.
     */
    @Query("SELECT o FROM ContractItemOption o JOIN FETCH o.contractItem i JOIN FETCH o.variant v "
            + "WHERE i.contract.id = (SELECT g.contract.id FROM GroupBuy g WHERE g.id = :groupBuyId) "
            + "AND v.variantId IN :variantIds "
            + "ORDER BY i.sortOrder ASC, o.sortOrder ASC")
    List<ContractItemOption> findPricedByGroupBuyIdAndVariantIds(@Param("groupBuyId") Long groupBuyId,
                                                                 @Param("variantIds") Collection<Long> variantIds);

    /** 공구 계약에서 한 상품의 옵션 행 전량 — C7 상세·옵션 시트가 한 번에 받는다. */
    @Query("SELECT o FROM ContractItemOption o JOIN FETCH o.contractItem i JOIN FETCH o.variant v "
            + "WHERE i.contract.id = (SELECT g.contract.id FROM GroupBuy g WHERE g.id = :groupBuyId) "
            + "AND i.product.productId = :productId "
            + "ORDER BY i.sortOrder ASC, o.sortOrder ASC")
    List<ContractItemOption> findPricedByGroupBuyIdAndProductId(@Param("groupBuyId") Long groupBuyId,
                                                                @Param("productId") Long productId);
}
