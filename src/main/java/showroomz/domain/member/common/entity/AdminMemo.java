package showroomz.domain.member.common.entity;

import showroomz.domain.member.common.service.AdminMemoPolicy;

import java.time.LocalDateTime;

/**
 * 관리자 메모 한 건 — 회원 유형별 메모 엔티티(브랜드 · 인플루언서)가 구현한다. 「작성자 본인 · 24시간」 판정은 {@link AdminMemoPolicy} 한
 * 곳이다(정합 계획서 R1-3).
 */
public interface AdminMemo {

    Long getAuthorId();

    LocalDateTime getCreatedAt();

    default boolean isAuthor(Long operatorId) {
        return getAuthorId() != null && getAuthorId().equals(operatorId);
    }

    default boolean isWithinEditWindow(LocalDateTime now) {
        return !getCreatedAt().plus(AdminMemoPolicy.EDIT_WINDOW).isBefore(now);
    }

    /** 수정 · 삭제 버튼 노출 — 작성자 본인 ∧ 24시간 이내. 서버가 판정해 내린다. */
    default boolean isEditableBy(Long operatorId, LocalDateTime now) {
        return isAuthor(operatorId) && isWithinEditWindow(now);
    }
}
