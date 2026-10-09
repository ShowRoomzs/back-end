package showroomz.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산번호 STL-YYMM-NNN 의 월별 일련번호(44 어드민 설계서 1-9). 번호 발급은 리포지토리 upsert 한 방으로만 —
 * 엔티티는 {@code ddl-auto: validate}가 테이블을 인지하게 하려고 둔다({@code ContractNumberSequence}와 같다).
 */
@Entity
@Table(name = "settlement_number_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementNumberSequence {

    @Id
    @Column(name = "seq_month", nullable = false, length = 4)
    private String seqMonth;

    @Column(name = "last_no", nullable = false)
    private int lastNo;
}
