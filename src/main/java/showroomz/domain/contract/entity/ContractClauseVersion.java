package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.contract.type.ContractClauseVersionStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 표준 조항의 버전 스냅샷(§25-7).
 *
 * <p>조항은 브랜드가 편집할 수 없고 계약서에 자동 삽입된다. 문안이 개정되면 이미 서명된 계약은
 * 옛 문안으로 남아야 하므로 계약이 조항 「버전」을 참조한다. 구조는 terms_document/terms_version과
 * 같은 패턴이며, 개정은 이 행을 수정하는 것이 아니라 새 버전을 쌓는 것으로만 이뤄진다.
 */
@Entity
@Table(name = "contract_clause_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractClauseVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_clause_version_id")
    private Long id;

    /** 접두 v를 뺀 숫자·점 표기(예: 1.0) — v는 화면 표기다. */
    @Column(name = "version_number", nullable = false, length = 20)
    private String versionNumber;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractClauseVersionStatus status;

    @OneToMany(mappedBy = "clauseVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ContractClause> clauses = new ArrayList<>();
}
