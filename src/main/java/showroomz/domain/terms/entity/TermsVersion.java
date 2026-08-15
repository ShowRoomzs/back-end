package showroomz.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.time.LocalDate;

/**
 * 약관 문서의 한 버전 (기획 §21-4).
 *
 * <p><b>원문 수정 메서드를 두지 않는다.</b> 등록된 버전의 본문·시행일·버전 번호는 어떤 경로로도
 * 바뀌지 않으며, 상태 전이(시행 예정 → 시행중 → 과거 버전)만 일어난다. 과거 버전은 동의 기록이
 * 참조하므로 삭제하지 않고 영구 보관한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "TERMS_VERSION",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_terms_version_document_number", columnNames = {"TERMS_DOCUMENT_ID", "VERSION_NUMBER"})
        },
        indexes = {
                // 문서 상세의 버전 이력 · 시행 전환 배치가 함께 쓰는 인덱스다
                @Index(name = "idx_terms_version_document_effective", columnList = "TERMS_DOCUMENT_ID, EFFECTIVE_DATE"),
                @Index(name = "idx_terms_version_status_effective", columnList = "STATUS, EFFECTIVE_DATE")
        }
)
public class TermsVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TERMS_VERSION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TERMS_DOCUMENT_ID", nullable = false, updatable = false)
    private TermsDocument document;

    /** 접두 v를 뺀 숫자·점 표기 (예: 3.1) — 표시할 때만 v를 붙인다 (기획 §21-5) */
    @Column(name = "VERSION_NUMBER", nullable = false, length = 20, updatable = false)
    private String versionNumber;

    /** 시행일 — 이 날 00:00부터 시행중으로 전환된다 (기획 §21-6) */
    @Column(name = "EFFECTIVE_DATE", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TermsVersionStatus status;

    /** 등록자(운영자) ID */
    @Column(name = "REGISTERED_BY", updatable = false)
    private Long registeredBy;

    @Builder
    public TermsVersion(TermsDocument document, String versionNumber, LocalDate effectiveDate,
                        String content, Long registeredBy) {
        this.document = document;
        this.versionNumber = versionNumber;
        this.effectiveDate = effectiveDate;
        this.content = content;
        this.registeredBy = registeredBy;
        // 등록 시점은 언제나 시행 예정이다 — 시행일 00:00에 배치가 교체한다 (기획 §21-6)
        this.status = TermsVersionStatus.SCHEDULED;
    }

    /** 시행일이 도래해 시행중으로 전환한다 (기획 §21-6) */
    public void takeEffect() {
        this.status = TermsVersionStatus.EFFECTIVE;
    }

    /** 후속 버전에 자리를 넘기고 과거 버전이 된다 — 삭제하지 않는다 (기획 §21-6) */
    public void expire() {
        this.status = TermsVersionStatus.PAST;
    }

    public boolean isEffective() {
        return this.status == TermsVersionStatus.EFFECTIVE;
    }

    public boolean isScheduled() {
        return this.status == TermsVersionStatus.SCHEDULED;
    }

    /** 화면 표기 — 접두 v는 필드 밖에 두므로 값과 붙이는 자리는 여기 하나뿐이다 (기획 §21-5) */
    public String getDisplayVersion() {
        return "v" + this.versionNumber;
    }
}
