package showroomz.api.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.common.type.ReasonType;
import showroomz.domain.member.common.type.SuspensionReasonType;

import java.util.Arrays;
import java.util.List;

/**
 * 어드민 모달 셀렉트의 사유 한 줄 — 코드 · 라벨 · 「기타면 상세 필수」 · (정지 사유만) 지급 정지 여부. 브랜드 · 인플루언서 상세가 같은 모양 ·
 * 같은 조립식을 쓴다(정합 계획서 R1-9). enum 이 단일 소스라 FE 가 목록을 하드코딩하지 않는다.
 */
@Schema(name = "AdminReasonOption", description = "모달 셀렉트 사유")
public record AdminReasonOption(
        @Schema(example = "SETTLEMENT_INFO_MISMATCH") String code,
        @Schema(example = "정산 정보 불일치") String label,
        @Schema(description = "기타(직접 입력)면 상세 필수") boolean detailRequired,
        @Schema(description = "정지 사유만 — 그 회원 몫 지급 정지 여부", nullable = true) Boolean holdsPayout) {

    /** 브랜드 · 인플루언서 정지 해제에서 운영자가 고르는 사유 — 「탈퇴 승인으로 종결」은 시스템 전용이라 빠진다. */
    private static final List<SanctionReleaseReason> OPERATOR_RELEASE_REASONS =
            List.of(SanctionReleaseReason.RESOLVED, SanctionReleaseReason.MISTAKE);

    public static List<AdminReasonOption> suspension(SuspensionReasonType[] reasons) {
        return Arrays.stream(reasons)
                .map(r -> new AdminReasonOption(r.name(), r.getLabel(), r.isDetailRequired(), r.isHoldsPayout()))
                .toList();
    }

    public static List<AdminReasonOption> of(ReasonType[] reasons) {
        return Arrays.stream(reasons)
                .map(r -> new AdminReasonOption(r.name(), r.getLabel(), r.isDetailRequired(), null))
                .toList();
    }

    public static List<AdminReasonOption> memberRelease() {
        return OPERATOR_RELEASE_REASONS.stream()
                .map(r -> new AdminReasonOption(r.name(), r.getLabel(), false, null))
                .toList();
    }

    public static AdminReasonOption label(String code, String label) {
        return new AdminReasonOption(code, label, false, null);
    }
}
