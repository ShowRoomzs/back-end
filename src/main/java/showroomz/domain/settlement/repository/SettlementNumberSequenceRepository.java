package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementNumberSequence;

public interface SettlementNumberSequenceRepository extends JpaRepository<SettlementNumberSequence, String> {

    /**
     * 월별 일련번호를 1 올린다 — 행이 없으면 1로 시작한다. upsert 가 행에 배타 잠금을 잡은 채 커밋까지 유지하므로 같은 트랜잭션의
     * {@link #findLastNo}가 다른 요청과 겹치지 않는다({@code ContractNumberSequenceRepository}와 같은 방식).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO settlement_number_sequence (seq_month, last_no) VALUES (:seqMonth, 1) "
            + "ON DUPLICATE KEY UPDATE last_no = last_no + 1", nativeQuery = true)
    void increment(@Param("seqMonth") String seqMonth);

    @Query(value = "SELECT last_no FROM settlement_number_sequence WHERE seq_month = :seqMonth", nativeQuery = true)
    Integer findLastNo(@Param("seqMonth") String seqMonth);
}
