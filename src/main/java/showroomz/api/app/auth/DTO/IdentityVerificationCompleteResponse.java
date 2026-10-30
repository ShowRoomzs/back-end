package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;
import showroomz.domain.member.user.type.IdentityVerificationStatus;

import java.time.LocalDateTime;

/** 본인인증 완료 응답(57 설계서 3-2) — 마스킹 값만 내린다. 원문은 가입 · 재인증 뒤 회원정보 조회에서도 마스킹이다. */
@Getter
@AllArgsConstructor
public class IdentityVerificationCompleteResponse {

    @Schema(description = "항상 VERIFIED (실패는 4xx)", example = "VERIFIED")
    private final IdentityVerificationStatus status;

    @Schema(description = "용도", example = "SIGNUP")
    private final IdentityVerificationPurpose purpose;

    @Schema(description = "마스킹한 실명 — 「홍길* 님, 인증되었어요」 표시용", example = "홍길*")
    private final String maskedName;

    @Schema(description = "마스킹한 휴대폰번호", example = "010-****-0000")
    private final String maskedPhone;

    @Schema(description = "포트원 인증 완료 시각", example = "2026-10-24T10:03:12")
    private final LocalDateTime verifiedAt;
}
