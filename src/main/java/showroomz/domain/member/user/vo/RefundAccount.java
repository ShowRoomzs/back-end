package showroomz.domain.member.user.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAccount {

    @Column(name = "REFUND_BANK_CODE", length = 3)
    private String bankCode; // Bank 테이블의 PK (논리적 연결)

    @Column(name = "REFUND_ACCOUNT_NUMBER", length = 20)
    private String accountNumber;

    @Column(name = "REFUND_ACCOUNT_HOLDER", length = 50)
    private String accountHolder;

    @Builder
    public RefundAccount(String bankCode, String accountNumber, String accountHolder) {
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}
