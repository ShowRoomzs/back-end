package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyNumberSequence;

import java.time.LocalDate;

public interface GroupBuyNumberSequenceRepository extends JpaRepository<GroupBuyNumberSequence, LocalDate> {

    /**
     * 일자별 일련번호를 1 올린다. 행이 없으면 1로 시작한다 — 계약번호와 같은 upsert 방식(설계서 1-10).
     * clearAutomatically는 쓰지 않는다 — 호출자가 붙들고 있는 계약 엔티티가 준영속으로 떨어진다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO group_buy_number_sequence (seq_date, last_seq) VALUES (:seqDate, 1) "
            + "ON DUPLICATE KEY UPDATE last_seq = last_seq + 1", nativeQuery = true)
    void increment(@Param("seqDate") LocalDate seqDate);

    /** 위 upsert가 잡은 행 잠금이 커밋까지 유지되므로 같은 트랜잭션에서 읽는 값은 다른 요청과 겹치지 않는다. */
    @Query(value = "SELECT last_seq FROM group_buy_number_sequence WHERE seq_date = :seqDate", nativeQuery = true)
    Integer findLastSeq(@Param("seqDate") LocalDate seqDate);
}
