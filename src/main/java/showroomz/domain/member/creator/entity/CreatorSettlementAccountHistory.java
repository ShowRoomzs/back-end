package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.creator.type.CreatorAdminActorType;

import java.time.LocalDateTime;

/**
 * 정산 계좌 변경 1건 = 1행(53 설계서 1-4 · 구현 메모 12절) — 바뀐 시점의 <b>이전 · 이후</b>를 함께 적어 백필 없이 「변경 08.29 14:02 · 이전
 * 신한 ****3301」을 만든다. 전체 계좌번호는 암호문으로만 남긴다(분쟁 조사용 · 응답에 내리지 않는다). 「지급 직전 계좌 변경은 사칭 신호」를
 * 판단 지표로 가공하지 않는다 — 시각만 보여 준다.
 */
@Entity
@Table(name = "creator_settlement_account_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorSettlementAccountHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "previous_bank_name", length = 50)
    private String previousBankName;

    @Column(name = "previous_last4", columnDefinition = "char(4)")
    private String previousLast4;

    @Column(name = "previous_account_enc", length = 255)
    private String previousAccountEnc;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "last4", columnDefinition = "char(4)")
    private String last4;

    @Column(name = "account_enc", nullable = false, length = 255)
    private String accountEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by_type", nullable = false, length = 16)
    private CreatorAdminActorType changedByType;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static CreatorSettlementAccountHistory of(Long creatorId, String previousBankName, String previousLast4,
                                                     String previousAccountEnc, String bankName, String last4,
                                                     String accountEnc, CreatorAdminActorType changedByType,
                                                     Long changedBy, LocalDateTime now) {
        CreatorSettlementAccountHistory history = new CreatorSettlementAccountHistory();
        history.creatorId = creatorId;
        history.previousBankName = previousBankName;
        history.previousLast4 = previousLast4;
        history.previousAccountEnc = previousAccountEnc;
        history.bankName = bankName;
        history.last4 = last4;
        history.accountEnc = accountEnc;
        history.changedByType = changedByType;
        history.changedBy = changedBy;
        history.changedAt = now;
        return history;
    }
}
