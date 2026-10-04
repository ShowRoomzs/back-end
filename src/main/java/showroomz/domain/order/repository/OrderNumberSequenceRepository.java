package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderNumberSequence;

import java.time.LocalDate;

public interface OrderNumberSequenceRepository extends JpaRepository<OrderNumberSequence, LocalDate> {

    /** 일자별 일련번호를 1 올린다. 행이 없으면 1로 시작한다 — 공구·계약 번호와 같은 upsert 방식. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO order_number_sequence (seq_date, last_seq) VALUES (:seqDate, 1) "
            + "ON DUPLICATE KEY UPDATE last_seq = last_seq + 1", nativeQuery = true)
    void increment(@Param("seqDate") LocalDate seqDate);

    /** 위 upsert가 잡은 행 잠금이 커밋까지 유지되므로 같은 트랜잭션에서 읽는 값은 다른 요청과 겹치지 않는다. */
    @Query(value = "SELECT last_seq FROM order_number_sequence WHERE seq_date = :seqDate", nativeQuery = true)
    Integer findLastSeq(@Param("seqDate") LocalDate seqDate);
}
