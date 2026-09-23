package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 검토 요청(C3 확인 모달). */
@Schema(description = "검토 요청")
public record ContractReviewRequestRequest(

        @Schema(description = "모달에서 확인한 경고 코드 목록(W1~W6). "
                + "서버가 다시 판정한 경고 집합과 다르면 409로 되돌려 모달을 다시 띄우게 한다 — "
                + "모달을 본 뒤 다른 탭에서 값을 고쳤을 수 있다(설계서 2-3)",
                example = "[\"W2\",\"W3\"]")
        List<String> acknowledgedWarnings
) {

    public List<String> acknowledgedWarningsOrEmpty() {
        return acknowledgedWarnings == null ? List.of() : acknowledgedWarnings;
    }
}
