package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractItem;

import java.util.Collection;
import java.util.List;

public interface ContractItemRepository extends JpaRepository<ContractItem, Long> {

    /**
     * 목록의 「상품 수」 열. 행마다 items를 지연 로딩하면 20건짜리 목록이 21번 쿼리된다 —
     * 페이지에 실린 계약 id를 한 번에 묶어 센다.
     */
    @Query("SELECT i.contract.id, COUNT(i) FROM ContractItem i WHERE i.contract.id IN :contractIds GROUP BY i.contract.id")
    List<Object[]> countByContractIds(@Param("contractIds") Collection<Long> contractIds);

    /**
     * 공구 게시물 상품 행(공구 게시물 설계 6-2 ②) — 계약 상품 전부 = 게시물 상품. 상품명·정가·공구가는 계약 스냅샷이고,
     * 썸네일·품절만 상품에서 읽는다. 순서는 {@code sort_order}다.
     */
    @Query("SELECT i FROM ContractItem i LEFT JOIN FETCH i.product "
            + "WHERE i.contract.id IN :contractIds ORDER BY i.sortOrder ASC, i.id ASC")
    List<ContractItem> findWithProductByContractIds(@Param("contractIds") Collection<Long> contractIds);
}
