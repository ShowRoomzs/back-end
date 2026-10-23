package showroomz.api.common.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.bank.entity.Bank;

@Getter
@Builder
public class BankResponse {

    @Schema(description = "금융결제원 표준 은행 코드 — 계좌 등록 요청(환불 계좌 · 정산 계좌)의 bankCode로 보낸다", example = "004")
    private String code;  // 금융결제원 표준 코드 (예: 004)
    @Schema(description = "은행 · 증권사명", example = "KB국민은행")
    private String name;  // 은행명 (예: KB국민은행)

    // Entity -> DTO 변환 메서드
    public static BankResponse from(Bank bank) {
        return BankResponse.builder()
                .code(bank.getCode())
                .name(bank.getName())
                .build();
    }
}
