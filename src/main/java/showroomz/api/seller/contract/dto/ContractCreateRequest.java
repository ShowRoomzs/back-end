package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 빈 초안 생성(B1). 계약 조건은 여기서 받지 않는다 — 작성 화면이 열린 뒤 임시저장(PUT)으로 채운다.
 *
 * <p>스레드 경유 진입이면 {@code creatorId}를 함께 받아 <b>고정</b>하고, 이후 PUT에서
 * 상대 변경을 거부한다(§25-5-1 "자동 지정 · 변경 불가").
 */
@Schema(description = "계약 초안 생성 요청")
public record ContractCreateRequest(

        @Schema(description = "계약 상대 — 스레드 경유 진입일 때만 보낸다. 보내면 이후 변경할 수 없다",
                nullable = true)
        Long creatorId,

        @Schema(description = "연결 ID — 참고용으로만 받는다. 서버는 이 값을 쓰지 않고 "
                + "creatorId로 지금 실제 CONNECTED인 연결을 다시 찾는다. 보내온 id를 그대로 믿으면 "
                + "남의 연결을 계약의 근거로 붙일 수 있다", nullable = true)
        Long connectionId
) {
}
