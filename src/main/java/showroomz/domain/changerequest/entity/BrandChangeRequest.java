package showroomz.domain.changerequest.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.entity.Market;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * M1(사업자 정보)·M2(정산 계좌) 변경 요청 공통 레코드(§1-1). 유형별 테이블을 나누지 않는다 —
 * 어드민 목록·상세·승인·반려가 두 유형에서 완전히 같은 코드로 돌아간다.
 * 재요청은 신규 행이다(§16-6) — 이 행 자체를 갱신하는 대신 매번 새로 생성한다.
 */
@Entity
@Table(name = "brand_change_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandChangeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "request_code", nullable = false, length = 20, unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChangeRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChangeRequestStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "requester_name", nullable = false, length = 64)
    private String requesterName;

    @Column(name = "evidence_file_url", nullable = false, length = 1024)
    private String evidenceFileUrl;

    @Column(name = "evidence_file_name", nullable = false)
    private String evidenceFileName;

    @Column(name = "evidence_file_size", nullable = false)
    private Long evidenceFileSize;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "reject_reason", length = 64)
    private String rejectReason;

    @Column(name = "reject_reason_detail", length = 500)
    private String rejectReasonDetail;

    @Column(name = "result_acknowledged_at")
    private LocalDateTime resultAcknowledgedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BrandChangeRequestItem> items = new ArrayList<>();

    public static BrandChangeRequest create(String requestCode, Market market, ChangeRequestType type,
                                             String reason, String requesterName,
                                             String evidenceFileUrl, String evidenceFileName, Long evidenceFileSize) {
        BrandChangeRequest request = new BrandChangeRequest();
        request.requestCode = requestCode;
        request.market = market;
        request.type = type;
        request.status = ChangeRequestStatus.PENDING;
        request.reason = reason;
        request.requesterName = requesterName;
        request.evidenceFileUrl = evidenceFileUrl;
        request.evidenceFileName = evidenceFileName;
        request.evidenceFileSize = evidenceFileSize;
        request.requestedAt = LocalDateTime.now();
        return request;
    }

    public void addItem(ChangeRequestField fieldKey, String currentValue, String requestedValue, int sortOrder) {
        BrandChangeRequestItem item = new BrandChangeRequestItem(fieldKey, currentValue, requestedValue, sortOrder);
        item.assignRequest(this);
        this.items.add(item);
    }

    public boolean isPending() {
        return this.status == ChangeRequestStatus.PENDING;
    }

    public void approve(Long processorId) {
        this.status = ChangeRequestStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = processorId;
    }

    public void reject(Long processorId, String reasonType, String reasonDetail) {
        this.status = ChangeRequestStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = processorId;
        this.rejectReason = reasonType;
        this.rejectReasonDetail = reasonDetail;
    }

    public void cancel() {
        this.status = ChangeRequestStatus.CANCELED;
        this.processedAt = LocalDateTime.now();
    }

    public void acknowledge() {
        this.resultAcknowledgedAt = LocalDateTime.now();
    }

    public boolean isBannerVisible() {
        if (status == ChangeRequestStatus.PENDING) {
            return true;
        }
        return (status == ChangeRequestStatus.APPROVED || status == ChangeRequestStatus.REJECTED)
                && resultAcknowledgedAt == null;
    }
}
