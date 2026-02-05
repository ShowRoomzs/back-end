package showroomz.domain.member.user.vo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.bank.entity.Bank;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAccount {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REFUND_BANK_CODE", nullable = true)
    private Bank bank;

    @Column(name = "REFUND_ACCOUNT_NUMBER", length = 20)
    private String accountNumber;

    @Column(name = "REFUND_ACCOUNT_HOLDER", length = 50)
    private String accountHolder;

    @Builder
    public RefundAccount(Bank bank, String accountNumber, String accountHolder) {
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}
