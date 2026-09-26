package showroomz.domain.groupbuy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 공구번호 GB-YYYYMMDD-NNN 의 일자별 일련번호(설계서 1-10).
 *
 * <p>애플리케이션이 이 엔티티를 직접 수정하지 않는다 — 번호 발급은 리포지토리의 upsert 한 방으로만 이뤄진다.
 */
@Entity
@Table(name = "group_buy_number_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuyNumberSequence {

    @Id
    @Column(name = "seq_date", nullable = false)
    private LocalDate seqDate;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;
}
