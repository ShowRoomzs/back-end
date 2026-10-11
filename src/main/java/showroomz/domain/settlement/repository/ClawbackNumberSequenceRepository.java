package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import showroomz.domain.settlement.entity.ClawbackNumberSequence;

public interface ClawbackNumberSequenceRepository extends JpaRepository<ClawbackNumberSequence, Integer> {

    /** 전역 일련번호를 1 올린다 — 행이 없으면 1로 시작한다(행 1개 · id = 1). */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO clawback_number_sequence (id, last_no) VALUES (1, 1) "
            + "ON DUPLICATE KEY UPDATE last_no = last_no + 1", nativeQuery = true)
    void increment();

    @Query(value = "SELECT last_no FROM clawback_number_sequence WHERE id = 1", nativeQuery = true)
    Integer findLastNo();
}
