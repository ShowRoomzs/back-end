package showroomz.domain.terms.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.terms.type.TermsType;

import java.time.LocalDate;

/**
 * 약관 문서 — 운영자가 관리하는 정적 법률 문서(4종·버전 관리).
 * 개정 = 새 버전 등록 → 시행일부터 노출 교체. 과거 버전은 보관(동의 기록이 "동의한 버전"을 참조).
 * 유형당 시행 버전은 1개(서비스 계층에서 보장).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "terms_document",
        uniqueConstraints = {
                @UniqueConstraint(name = "terms_document_uk", columnNames = {"type", "version"})
        }
)
public class TermsDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_document_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TermsType type;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** 현재 시행 버전 여부 — 유형당 1개 */
    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Builder
    public TermsDocument(TermsType type, String version, String content, LocalDate effectiveDate) {
        this.type = type;
        this.version = version;
        this.content = content;
        this.effectiveDate = effectiveDate;
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
