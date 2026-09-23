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
}
