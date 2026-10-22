package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.cs.type.CsCategory;

import java.util.List;

@Getter
@NoArgsConstructor
public class InquiryRegisterRequest {

    @Schema(description = "문의 유형 (5종)", example = "DELIVERY", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"DELIVERY", "CANCEL_EXCHANGE_RETURN", "ORDER_PAYMENT", "SERVICE", "ACCOUNT"})
    @NotNull(message = "문의 유형을 선택해주세요.")
    private CsCategory type;

    @Schema(description = "문의 내용 (최대 1000자)", example = "배송이 언제 오나요?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "내용을 입력해주세요.")
    @Size(max = 1000, message = "문의 내용은 1000자 이내로 입력해주세요.")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트 (최대 5장)")
    @Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다.")
    private List<String> imageUrls;

    @Schema(description = "참조 주문 ID (선택 — 주문 없이도 문의할 수 있습니다)", example = "123456")
    private Long orderId;

    @Schema(description = "반려 이의 대상 클레임 ID (선택) — 반려 안내(C10-5)의 「이의 제기」로 쓸 때만 보낸다. "
            + "본인 클레임이고 반려 보류 중이어야 하며, 문의 유형은 CANCEL_EXCHANGE_RETURN 이어야 한다. "
            + "orderId 를 비우면 클레임의 주문으로 채운다", example = "3008")
    private Long claimId;

    @Schema(description = "계정 이용 정지 소명(선택) — 정지 안내 시트의 「1:1 문의로 이의 신청」(C20 1d)으로 쓸 때만 true. "
            + "이용 정지 중일 때만 쓸 수 있고, 유형은 ACCOUNT · orderId · claimId 는 비워야 한다. 답변 전 소명 문의가 이미 있으면 409",
            example = "true")
    private Boolean sanctionAppeal;

    @Schema(description = "운임 환급 계좌 안내(선택) — 반품 상세 C10-5 「귀책 변경 · 운임 환급」의 [1:1 문의]로 쓸 때만 그 환급 건(`refundSplit.bankTransferRefundTaskId`)을 보낸다. "
            + "본인 주문의 송금 대기 건이어야 하고, 유형은 CANCEL_EXCHANGE_RETURN 이어야 한다. orderId 를 비우면 그 주문으로 채운다. "
            + "유형명은 「환불 계좌 안내」로 보인다", example = "905")
    private Long refundTaskId;

    public boolean isSanctionAppeal() {
        return Boolean.TRUE.equals(sanctionAppeal);
    }
}
