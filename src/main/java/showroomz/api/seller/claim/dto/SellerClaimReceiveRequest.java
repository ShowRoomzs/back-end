package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 입고 확인(35 설계서 3-3) — 다건. 묶음 안 한 건만 먼저 확인해도 된다. */
public record SellerClaimReceiveRequest(
        @Schema(description = "입고 확인할 클레임 id", example = "[3021, 3022]")
        @NotEmpty(message = "입고 확인할 건을 선택해 주세요.") @Size(max = 500) List<Long> claimIds
) {
}
