package showroomz.api.admin.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.entity.RemoteAreaPostcode;
import showroomz.domain.order.type.DeliveryRegion;

import java.time.LocalDateTime;
import java.util.List;

/** 추가 배송비 권역 우편번호 운영 등록(55 계획서 P4 · 1021 설계서 3-1 · Q10). */
public final class RemoteAreaPostcodeDto {

    private RemoteAreaPostcodeDto() {
    }

    @Schema(name = "AdminRemoteAreaPostcode", description = "등록된 우편번호 범위 1행")
    public record Item(
            @Schema(description = "범위 ID", example = "12") Long id,
            @Schema(description = "시작 우편번호(5자리 · 포함)", example = "40200") String postcodeFrom,
            @Schema(description = "끝 우편번호(5자리 · 포함)", example = "40240") String postcodeTo,
            @Schema(description = "권역 — REMOTE(도서산간) · JEJU(제주)", example = "REMOTE") DeliveryRegion region,
            @Schema(description = "권역 라벨", example = "도서산간") String regionLabel,
            @Schema(description = "이름", example = "경북 울릉군", nullable = true) String name,
            @Schema(description = "등록 시각", example = "2026-10-24T10:00:00") LocalDateTime createdAt
    ) {
        public static Item of(RemoteAreaPostcode p) {
            return new Item(p.getId(), p.getPostcodeFrom(), p.getPostcodeTo(), p.getRegion(), p.getRegion().getLabel(),
                    p.getName(), p.getCreatedAt());
        }
    }

    @Schema(name = "AdminRemoteAreaPostcodeCreateRequest", description = "범위 1건 등록 — 한 번호만이면 시작 = 끝")
    public record CreateRequest(
            @Schema(description = "시작 우편번호(5자리 숫자)", example = "40200", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "\\d{5}", message = "우편번호는 5자리 숫자여야 합니다.") String postcodeFrom,
            @Schema(description = "끝 우편번호(5자리 숫자 · 시작 이상)", example = "40240", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "\\d{5}", message = "우편번호는 5자리 숫자여야 합니다.") String postcodeTo,
            @Schema(description = "권역 — REMOTE · JEJU(STANDARD 는 등록 대상이 아니다)", example = "REMOTE",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull DeliveryRegion region,
            @Schema(description = "이름 — 100자", example = "경북 울릉군", nullable = true)
            @Size(max = 100) String name
    ) {
    }

    @Schema(name = "AdminRemoteAreaPostcodeBulkResult", description = "CSV 일괄 등록 결과 — 전부 등록되거나 하나도 등록되지 않는다")
    public record BulkResult(
            @Schema(description = "등록한 범위 수", example = "2") int createdCount,
            @Schema(description = "등록한 범위 — 파일 순서") List<Item> items
    ) {
    }
}
