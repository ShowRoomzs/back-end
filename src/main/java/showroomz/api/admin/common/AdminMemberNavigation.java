package showroomz.api.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 상세 상단의 「이전 · 다음」(ui-admin-13 · 18) — 회원번호(id) 순 인접 회원. 목록 필터 · 정렬과 무관하다(브랜드 상세 {@code Navigation}과
 * 같은 규칙). 인플루언서 · 소비자 상세가 함께 쓴다(55 계획서 M4) — 브랜드는 필드명 호환을 위해 기존 레코드를 그대로 둔다.
 */
@Schema(name = "AdminMemberNavigation")
public record AdminMemberNavigation(
        @Schema(description = "앞 회원 id — 첫 회원이면 null", example = "3017", nullable = true) Long previousId,
        @Schema(description = "뒤 회원 id — 마지막 회원이면 null", example = "3019", nullable = true) Long nextId) {
}
