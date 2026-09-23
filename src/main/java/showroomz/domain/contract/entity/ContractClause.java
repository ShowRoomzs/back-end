package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 표준 조항 1건.
 *
 * <p>요약(작성 화면 카드)과 전문(모달 C5)을 「같은 행에서」 파생시킨다(설계서 4-6).
 * 두 목록을 따로 관리하면 현재 시안의 요약 11개 ↔ 전문 9조 같은 어긋남이 또 생긴다.
 *
 * <p>전문이 아직 없는 조항(단독 판매·샘플 제공)은 {@code fullTitle}·{@code fullBody}가 null이다.
 * 기획·법률 확정 전까지 없는 문안을 지어내지 않는다(설계서 미결 #5).
 */
@Entity
@Table(name = "contract_clause")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractClause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_clause_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_clause_version_id", nullable = false)
    private ContractClauseVersion clauseVersion;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "summary_title", nullable = false, length = 100)
    private String summaryTitle;

    @Column(name = "summary_description", nullable = false, length = 500)
    private String summaryDescription;

    @Column(name = "full_title", length = 100)
    private String fullTitle;

    @Column(name = "full_body", columnDefinition = "TEXT")
    private String fullBody;

    /** 전문이 확정된 조항인지 — 모달(C5)은 이 값이 true인 조항만 그린다. */
    public boolean hasFullText() {
        return fullTitle != null && fullBody != null;
    }
}
