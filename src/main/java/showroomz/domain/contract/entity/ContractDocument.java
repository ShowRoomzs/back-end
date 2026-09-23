package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.contract.type.ContractDocumentType;

import java.time.LocalDateTime;

/**
 * 체결 문서 2종(§25-3 #3) — 어드민이 모두싸인에서 받아 업로드한다.
 *
 * <p>교체·삭제 메서드를 두지 않는다(§28-6 "서명 원본이 바뀌면 계약의 증거가 사라진다").
 * (contract_id, document_type) 유니크 제약이 실수로 인한 중복 업로드까지 함께 막는다.
 */
@Entity
@Table(name = "contract_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_document_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private ContractDocumentType documentType;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "file_url", length = 2048)
    private String fileUrl;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
