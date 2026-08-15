package showroomz.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

/**
 * 약관·정책 문서 (기획 §21-2) — 문서 : 버전 = 1 : N.
 *
 * <p>문서에는 <b>수정 메서드를 두지 않는다.</b> 문서명·유형·대상은 등록 후 고정이고, 원문 개정은
 * 오직 새 버전 등록으로만 이뤄진다 — 동의 기록이 "동의한 버전"을 참조하므로 원문이 바뀌면
 * 누가 무엇에 동의했는지가 무너진다 (기획 §21 성격).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "TERMS_DOCUMENT",
        uniqueConstraints = {
                // 마케팅 동의는 대상별로 같은 이름의 문서를 따로 둔다 — 이름만으로는 중복을 가릴 수 없다 (기획 §21-2)
                @UniqueConstraint(name = "uk_terms_document_name_target", columnNames = {"NAME", "TARGET"})
        },
        indexes = {
                // 목록은 유형 탭 + 문서명 검색이다 (기획 §21-3)
                @Index(name = "idx_terms_document_type", columnList = "TYPE")
        }
)
public class TermsDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TERMS_DOCUMENT_ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 등록 후 고정 (기획 §21-2) */
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 32, updatable = false)
    private TermsType type;

    /** 등록 후 고정 — 대상이 바뀌면 동의 대상 집단이 달라진다 (기획 §21-2) */
    @Enumerated(EnumType.STRING)
    @Column(name = "TARGET", nullable = false, length = 32, updatable = false)
    private TermsTarget target;

    /**
     * 후속 <b>문서</b>로 대체돼 더는 시행되지 않는 문서 — 목록의 "구버전" (기획 §21-1).
     *
     * <p>어드민에는 문서를 내리는 액션이 없다(내리기 불가 · 새 버전으로 대체만). 문서 자체의 대체는
     * 운영정책 편입 여부와 함께 미결이라(기획 §21-6 미결 3번) 지금은 데이터로만 표시하고,
     * 결정되면 그때 전이 API를 붙인다.
     */
    @Column(name = "IS_SUPERSEDED", nullable = false)
    private boolean superseded;

    /** 이 문서를 대체한 문서 ID — 대체되지 않았으면 비어 있다 */
    @Column(name = "SUPERSEDED_BY_DOCUMENT_ID")
    private Long supersededByDocumentId;

    @Column(name = "REGISTERED_BY")
    private Long registeredBy;

    @Builder
    public TermsDocument(String name, TermsType type, TermsTarget target, Long registeredBy) {
        this.name = name;
        this.type = type;
        this.target = target;
        this.registeredBy = registeredBy;
        this.superseded = false;
    }
}
