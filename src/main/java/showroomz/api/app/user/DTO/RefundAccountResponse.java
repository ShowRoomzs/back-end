package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.member.user.vo.RefundAccount;

@Getter
@Builder
public class RefundAccountResponse {

    @Schema(description = "은행 표준 코드", example = "004")
    private String bankCode;

    @Schema(description = "은행명", example = "KB국민은행")
    private String bankName;

    @Schema(description = "계좌번호", example = "123456789012")
    private String accountNumber;

    @Schema(description = "예금주명", example = "김화창")
    private String accountHolder;

    public static RefundAccountResponse of(RefundAccount account, String bankName) {
        return RefundAccountResponse.builder()
                .bankCode(account.getBankCode())
                .bankName(bankName)
                .accountNumber(account.getAccountNumber())
                .accountHolder(account.getAccountHolder())
                .build();
    }
}
