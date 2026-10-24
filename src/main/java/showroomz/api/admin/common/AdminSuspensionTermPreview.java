package showroomz.api.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.member.common.service.SanctionTermPolicy;

import java.time.LocalDateTime;

/**
 * 정지 모달(M1) 미리보기 재료 — 「이의 신청 기한 · 통지 후 3영업일 이상 · 2026.10.22까지」 · 「소명 검토 · 접수 후 N영업일」(1021 설계서 7-2).
 * 정지 뒤 응답({@code term})에만 있던 값을 정지 <b>전</b> 사유 목록 API 에 싣는다 — 브랜드 · 인플루언서 · 소비자가 같은 모양을 쓴다.
 *
 * <p>미리보기 날짜는 응답 시각 기준이다 — 모달을 연 채 날을 넘기면 달라질 수 있어, 저장 시 서버가 다시 계산한 정지 응답의
 * {@code term.appealDeadlineAt}이 진실이다(FE 는 그 값으로 맞춰 그린다). 영업일 수는 공휴일 API 로 FE 가 직접 셀 때 쓴다.
 */
@Schema(name = "AdminSuspensionTermPreview", description = "정지 모달 미리보기 — 이의 신청 기한 영업일 · 지금 정지하면 잡힐 기한 · 소명 검토 영업일")
public record AdminSuspensionTermPreview(
        @Schema(description = "이의 신청 기한 영업일 수 — 통지 + N영업일 23:59:59(member.sanction.appeal-business-days) · 「통지 후 N영업일 이상」",
                example = "3") int appealBusinessDays,
        @Schema(description = "지금 정지하면 잡힐 이의 신청 기한 — 응답 시각 기준 미리보기(통지가 비영업일이면 다음 영업일부터 센다 · 공휴일 반영). "
                + "저장 값은 정지 응답의 term.appealDeadlineAt", example = "2026-10-22T23:59:59") LocalDateTime appealDeadlineAt,
        @Schema(description = "소명 검토 N영업일 — 운영자 처리 기한 · 표시만. [정책값 미정]이면 null", nullable = true)
        Integer appealReviewBusinessDays) {

    public static AdminSuspensionTermPreview of(SanctionTermPolicy policy, LocalDateTime now) {
        return new AdminSuspensionTermPreview(policy.appealBusinessDays(), policy.appealDeadline(now),
                policy.appealReviewBusinessDays());
    }
}
