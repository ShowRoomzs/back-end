package showroomz.domain.contract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 계약번호 CTR-YYYYMMDD-NNN 의 일자별 일련번호(설계서 1-7).
 *
 * <p>애플리케이션이 이 엔티티를 직접 수정하지 않는다 — 번호 발급은 리포지토리의
 * upsert 한 방으로만 이뤄진다. 엔티티는 {@code ddl-auto: validate}가 테이블을 인지하게 하고
 * 발급된 값을 읽어오기 위해 둔다.
 */
@Entity
@Table(name = "contract_number_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNumberSequence {

    @Id
    @Column(name = "seq_date", nullable = false)
    private LocalDate seqDate;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;
}
