package showroomz.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 차감번호 CLW-NNNN 의 전역 일련번호(44 어드민 설계서 1-9) — 행 1개(id = 1). 발급은 리포지토리 upsert 로만. */
@Entity
@Table(name = "clawback_number_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClawbackNumberSequence {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "last_no", nullable = false)
    private int lastNo;
}
