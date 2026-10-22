package showroomz.domain.changerequest.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 변경 요청 공통 레코드 — 브랜드 M1(사업자 정보) · M2(정산 계좌)(§1-1) + 인플루언서 사업자 구분 변경 T7 · T8(§16-2a). 유형 · 요청자별
 * 테이블을 나누지 않는다 — 어드민 목록 · 정렬 · 이전/다음 · 요청 코드 순번이 하나다(16 설계서 1-1). 테이블 이름의 「brand」는 역사적
 * 이유다(1-2) — Java 이름은 {@code ChangeRequest}다(정합 계획서 R3-4). 요청자는 {@link #requesterType}이 정하고, BRAND ⇔ {@code market} · CREATOR ⇔ {@code creator}(DB CHECK).
 * 재요청은 신규 행이다(§16-6) — 이 행 자체를 갱신하는 대신 매번 새로 생성한다.
 */
@Entity
@Table(name = "brand_change_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "request_code", nullable = false, length = 20, unique = true)
    private String requestCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 16)
    private ChangeRequesterType requesterType;

    /** 브랜드 요청만 — 인플루언서 요청은 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id")
    private Market market;

    /** 인플루언서 요청만 — 브랜드 요청은 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChangeRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChangeRequestStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    /** 브랜드 담당자 이름 스냅샷 — 인플루언서 요청은 null(본인). */
    @Column(name = "requester_name", length = 64)
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

    /**
     * 비사업자 전환(TO_INDIVIDUAL)의 주민등록번호 암호문 — 항목 값에는 마스킹만 싣는다. 승인 · 반려 · 취소가 끝에서 스스로 비운다 — 처리가
     * 끝난 요청 행에는 고유식별정보가 남지 않는다(16 설계서 1-5). 승인 반영은 {@link #approve} 전에 읽는다.
     */
    @Column(name = "resident_number_enc", length = 255)
    private String residentNumberEnc;

    /** 인플루언서 계좌 변경(1021 기-22 · V212) — 요청 은행 코드 · 계좌번호 암호문 · 예금주(실명 스냅샷). 처리 뒤 암호문은 비운다. */
    @Column(name = "account_bank_code", length = 10)
    private String accountBankCode;

    @Column(name = "account_number_enc", length = 255)
    private String accountNumberEnc;

    @Column(name = "account_holder_name", length = 50)
    private String accountHolderName;

    /** 승인 · 반려 · 취소의 동시 처리 차단(16 설계서 3-8). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChangeRequestItem> items = new ArrayList<>();

    /** 브랜드 요청(M1 · M2). */
    public static ChangeRequest create(String requestCode, Market market, ChangeRequestType type,
                                             String reason, String requesterName,
                                             String evidenceFileUrl, String evidenceFileName, Long evidenceFileSize) {
        if (type.requesterType() != ChangeRequesterType.BRAND) {
            throw new IllegalArgumentException("브랜드 요청 유형이 아니다: " + type);
        }
        ChangeRequest request = new ChangeRequest();
        request.requestCode = requestCode;
        request.requesterType = ChangeRequesterType.BRAND;
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

    /** 인플루언서 사업자 구분 변경(T7 · T8) — 변경 사유 입력란이 없다(reason null) · 요청자 이름은 본인이라 저장하지 않는다. */
    public static ChangeRequest createForCreator(String requestCode, Creator creator, ChangeRequestType type,
                                                      String evidenceFileUrl, String evidenceFileName,
                                                      Long evidenceFileSize) {
        if (type.requesterType() != ChangeRequesterType.CREATOR) {
            throw new IllegalArgumentException("인플루언서 요청 유형이 아니다: " + type);
        }
        ChangeRequest request = new ChangeRequest();
        request.requestCode = requestCode;
        request.requesterType = ChangeRequesterType.CREATOR;
        request.creator = creator;
        request.type = type;
        request.status = ChangeRequestStatus.PENDING;
        request.evidenceFileUrl = evidenceFileUrl;
        request.evidenceFileName = evidenceFileName;
        request.evidenceFileSize = evidenceFileSize;
        request.requestedAt = LocalDateTime.now();
        return request;
    }

    public void attachResidentNumber(String encrypted) {
        this.residentNumberEnc = encrypted;
    }

    /** 계좌 변경 요청 값(1021 기-22) — 계좌번호는 암호문으로. */
    public void attachAccount(String bankCode, String accountNumberEncrypted, String holderName) {
        this.accountBankCode = bankCode;
        this.accountNumberEnc = accountNumberEncrypted;
        this.accountHolderName = holderName;
    }

    /** 바뀌는 항목 라벨(입력 순서) — 어드민 상세의 「변경 항목」 · 승인 결과 메일. */
    public List<String> changedFieldLabels() {
        return items.stream()
                .sorted(Comparator.comparingInt(ChangeRequestItem::getSortOrder))
                .map(item -> item.getFieldKey().getLabel())
                .toList();
    }

    public boolean isCreatorRequest() {
        return requesterType == ChangeRequesterType.CREATOR;
    }

    public void addItem(ChangeRequestField fieldKey, String currentValue, String requestedValue, int sortOrder) {
        ChangeRequestItem item = new ChangeRequestItem(fieldKey, currentValue, requestedValue, sortOrder);
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
        this.residentNumberEnc = null;
        this.accountNumberEnc = null;
    }

    public void reject(Long processorId, String reasonType, String reasonDetail) {
        this.status = ChangeRequestStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = processorId;
        this.rejectReason = reasonType;
        this.rejectReasonDetail = reasonDetail;
        this.residentNumberEnc = null;
        this.accountNumberEnc = null;
    }

    public void cancel() {
        this.status = ChangeRequestStatus.CANCELED;
        this.processedAt = LocalDateTime.now();
        this.residentNumberEnc = null;
        this.accountNumberEnc = null;
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
