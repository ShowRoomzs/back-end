package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.CreatorSuspensionReason;

/**
 * 공구 중단 요청(C7). 메모는 <b>항상 필수</b>다 — 인플루언서 요청은 상대(브랜드)의 이행 문제를 주장하는 경우가 대부분이라
 * 운영자가 사실을 확인할 재료가 필요하다(31 설계 5-3). 메모는 운영자에게 쓰는 글이고 브랜드에게 내리지 않는다.
 */
@Schema(description = "공구 중단 요청")
public record CreatorSuspensionRequestRequest(

        @Schema(description = "중단 사유 — 필수. PRODUCT_DEFECT(상품에 문제가 있어 추천을 이어갈 수 없음) · DELIVERY_FAILURE(배송 지연 · 미발송이 계속됨) · "
                + "CONSUMER_COMPLAINTS(소비자 불만이 반복적으로 접수됨) · BRAND_UNREACHABLE(브랜드와 연락이 되지 않음) · "
                + "PERSONAL_REASON(개인 사정으로 진행이 어려움) · ETC(기타). 브랜드에게는 이 사유 라벨만 보인다", example = "DELIVERY_FAILURE")
        @NotNull
        CreatorSuspensionReason reasonCode,

        @Schema(description = "운영자에게 전달할 내용 — 항상 필수 · 1,000자. 브랜드에게는 보이지 않는다",
                example = "시작 4일째인데 첫날 주문 18건이 아직 발송되지 않았고 배송 문의가 계속 들어옵니다.")
        @NotBlank
        @Size(max = 1000)
        String memo
) {
}
