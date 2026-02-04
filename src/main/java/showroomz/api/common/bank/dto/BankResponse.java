package showroomz.api.common.bank.dto;

import lombok.Builder;
import lombok.Getter;
import showroomz.domain.bank.entity.Bank;

@Getter
@Builder
public class BankResponse {

    private String code;  // 금융결제원 표준 코드 (예: 004)
    private String name;  // 은행명 (예: KB국민은행)

    // Entity -> DTO 변환 메서드
    public static BankResponse from(Bank bank) {
        return BankResponse.builder()
                .code(bank.getCode())
                .name(bank.getName())
                .build();
    }
}
