package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.contract.type.ContractDeclineReason;

/**
 * 거절(시안 S5) — 인플루언서가 계약 객체에 쓸 수 있는 세 가지 중 하나다.
 *
 * <p>조건 수정 필드가 하나도 없다. §27-4가 「화면 어디에도 조건 입력 필드를 두지 않는다」로
 * 못박았고, 없는 화면에 대응하는 API가 열려 있으면 화면이 보장한 제약을 서버가 배신한다.
 */
@Schema(description = "계약 거절 요청")
public record CreatorContractDeclineRequest(

        @Schema(description = "거절 사유 5종 — **필수**. 브랜드의 취소 사유와 별도 enum이다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ContractDeclineReason reasonCode,

        @Schema(description = "브랜드에게 그대로 전달되는 메모 — **선택**이다(시안 S5에 `*`가 없다). "
                + "ETC(기타)여도 필수로 걸지 않는다. 파트너 취소가 ETC에 메모를 필수로 건 것과 "
                + "대칭이 깨진 지점이라 §28-8 D #10에서 함께 정리한다(설계서 미결 #2)",
                nullable = true)
        @Size(max = 1000)
        String memo
) {
}
