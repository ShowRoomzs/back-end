package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractNumberSequence;

import java.time.LocalDate;

public interface ContractNumberSequenceRepository extends JpaRepository<ContractNumberSequence, LocalDate> {

    /**
     * 일자별 일련번호를 1 올린다. 행이 없으면 1로 시작한다.
     *
     * <p>ProductService.generateProductNumber()가 쓰는 findAll() → max 방식을 복제하지 않는다 —
     * 전량을 메모리로 끌어오는 데다 동시 요청에서 같은 번호가 나온다.
     */
    // clearAutomatically는 쓰지 않는다 — 영속성 컨텍스트를 비우면 호출자가 붙들고 있는
    // 계약 엔티티가 준영속으로 떨어져 이어지는 전이 기록이 변경 감지에서 빠진다.
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO contract_number_sequence (seq_date, last_seq) VALUES (:seqDate, 1) "
            + "ON DUPLICATE KEY UPDATE last_seq = last_seq + 1", nativeQuery = true)
    void increment(@Param("seqDate") LocalDate seqDate);

    /**
     * 올린 값을 읽는다. 위 upsert가 해당 행에 배타 잠금을 잡은 채 커밋 전까지 유지하므로
     * 같은 트랜잭션 안에서 읽는 이 값은 다른 요청과 겹치지 않는다.
     */
    @Query(value = "SELECT last_seq FROM contract_number_sequence WHERE seq_date = :seqDate", nativeQuery = true)
    Integer findLastSeq(@Param("seqDate") LocalDate seqDate);
}
