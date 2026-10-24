package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 초안 승인 기록(1021 설계서 1-8 · 결정 노트 기-30) — 브랜드가 인플루언서의 콘텐츠 초안을 승인한 사실. <b>기록 전용</b>이다 — 계약 · 공구
 * 상태를 움직이지 않는다. 체결완료 상세(파트너 B5 · B5a · B5b · 스튜디오 S6)의 「초안 승인 기록」 카드가 읽는다.
 */
@Entity
@Table(name = "contract_draft_approval")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractDraftApproval {

    public static final String SOURCE_THREAD = "THREAD";
    public static final String SOURCE_ETC = "ETC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 대상 — 「릴스 1차 초안」. */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Column(name = "approved_by_market_id", nullable = false)
    private Long approvedByMarketId;

    /** THREAD(연결·소통 스레드에서 확인한 초안) · ETC. */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "thread_message_id")
    private Long threadMessageId;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static ContractDraftApproval of(Long contractId, Long marketId, String title, LocalDateTime approvedAt,
                                           Long threadMessageId, String note, LocalDateTime now) {
        ContractDraftApproval a = new ContractDraftApproval();
        a.contractId = contractId;
        a.approvedByMarketId = marketId;
        a.title = title;
        a.approvedAt = approvedAt;
        a.threadMessageId = threadMessageId;
        a.source = threadMessageId == null ? SOURCE_ETC : SOURCE_THREAD;
        a.note = note;
        a.createdAt = now;
        return a;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public String sourceLabel() {
        return SOURCE_THREAD.equals(source) ? "연결·소통 스레드에서 확인한 초안" : "직접 기록";
    }
}
