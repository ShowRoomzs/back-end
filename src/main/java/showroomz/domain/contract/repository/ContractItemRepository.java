package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

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

    /**
     * 이 상품을 담은 계약 중 <b>아직 끝나지 않은</b> 것이 있는가 — 셀러 상품 수정의 옵션 구조 변경 차단(옵션 계획서 6-1).
     *
     * <p>진행 중 = 체결 전 활성 상태(작성중~체결 처리 대기) ∨ 체결됐지만 공구가 종결 3종이 아님(공구 미생성 포함).
     * 거절·만료·취소와 종결된 공구의 계약은 계약서에 스냅샷만 남으면 되므로 막지 않는다. 삭제 표시된 계약은 뺀다.
     */
    @Query("SELECT COUNT(i) > 0 FROM ContractItem i JOIN i.contract c "
            + "WHERE i.product.productId = :productId AND c.deletedAt IS NULL "
            + "AND (c.status IN :openStatuses "
            + "  OR (c.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED "
            + "      AND NOT EXISTS (SELECT g.id FROM GroupBuy g WHERE g.contract = c AND g.status IN :terminalStatuses)))")
    boolean existsOpenContractForProduct(@Param("productId") Long productId,
                                         @Param("openStatuses") Collection<ContractStatus> openStatuses,
                                         @Param("terminalStatuses") Collection<GroupBuyStatus> terminalStatuses);
}
