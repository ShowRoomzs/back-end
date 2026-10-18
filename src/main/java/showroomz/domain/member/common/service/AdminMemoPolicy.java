package showroomz.domain.member.common.service;

import showroomz.domain.member.common.entity.AdminMemo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 관리자 메모 규칙(§52-2 · §53-1 · §25-6) — 누적 기입 · 항목당 300자 · 작성자 본인 24시간 내 수정 · 삭제. 브랜드 · 인플루언서 메모가 글자까지
 * 같은 규칙이라 한 곳에 둔다(정합 계획서 R1-3). 에러 코드 값은 회원 유형별로 다르므로(클라이언트 계약) 호출자가 넘긴다.
 */
public final class AdminMemoPolicy {

    public static final int MAX_LENGTH = 300;
    public static final Duration EDIT_WINDOW = Duration.ofHours(24);

    private AdminMemoPolicy() {
    }

    /** 서버 검증 — 앞뒤 공백 제거 후 1 ~ 300자. */
    public static String normalize(String content) {
        String value = content == null ? "" : content.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메모는 1자 이상 300자 이내로 입력해 주세요.");
        }
        return value;
    }

    /** 수정 · 삭제 직전 — 작성자가 아니면 {@code notAuthor}, 24시간이 지났으면 {@code windowClosed}(둘 다 403). */
    public static void requireEditable(AdminMemo memo, Long operatorId, LocalDateTime now, ErrorCode notAuthor,
                                       ErrorCode windowClosed) {
        if (!memo.isAuthor(operatorId)) {
            throw new BusinessException(notAuthor);
        }
        if (!memo.isWithinEditWindow(now)) {
            throw new BusinessException(windowClosed);
        }
    }
}
