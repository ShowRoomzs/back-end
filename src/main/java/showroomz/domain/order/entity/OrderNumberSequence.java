package showroomz.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 주문번호 yyyyMMdd-NNNNNN 의 일자별 일련번호 — {@code GroupBuyNumberSequence}와 같은 구조(결제 계획서 3-3).
 * 애플리케이션이 이 엔티티를 직접 수정하지 않는다 — 번호 발급은 리포지토리의 upsert 한 방으로만 이뤄진다.
 */
@Entity
@Table(name = "order_number_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderNumberSequence {

    @Id
    @Column(name = "seq_date", nullable = false)
    private LocalDate seqDate;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;
}
