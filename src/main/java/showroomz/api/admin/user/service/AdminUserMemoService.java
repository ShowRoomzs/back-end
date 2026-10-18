package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.user.dto.AdminUserDto.Memo;
import showroomz.api.admin.user.dto.AdminUserDto.MemoPage;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.UserAdminMemo;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserAdminMemoRepository;
import showroomz.domain.member.user.service.UserAdminHistoryRecorder;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 소비자 관리자 메모(§25-6 · 51-6 설계서 7-1) — 누적 · 항목당 300자(서버 검증) · 작성자 본인 24시간 내 수정 · 삭제 · 소비자 비노출.
 * {@code editable}은 서버가 판정해 내린다. 탈퇴 회원은 조회 전용. 옛 단일 메모 이관 행은 작성자가 없어 누구도 고치지 못한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserMemoService {

    private final UserRepository users;
    private final UserAdminMemoRepository memos;
    private final UserAdminHistoryRecorder history;
    private final AdminOperatorResolver operators;

    @Transactional(readOnly = true)
    public MemoPage list(Long userId, Long operatorId, int page, int size) {
        Users user = load(userId);
        return page(user, operatorId, Math.max(page, 1), Math.min(Math.max(size, 1), 100));
    }

    /** 상세 응답의 최신 N건. */
    @Transactional(readOnly = true)
    public MemoPage latest(Users user, Long operatorId, int limit) {
        return page(user, operatorId, 1, limit);
    }

    public Memo create(Long userId, Long operatorId, String content) {
        String operatorName = operators.operatorName(operatorId);
        Users user = load(userId);
        requireNotWithdrawn(user);
        LocalDateTime now = LocalDateTime.now();
        UserAdminMemo memo = memos.save(UserAdminMemo.write(userId, operatorId, operatorName, validate(content), now));
        history.byOperator(userId, UserAdminEventType.MEMO_CREATED, operatorId, operatorName, null,
                UserAdminHistoryRecorder.REF_MEMO, memo.getId(), now);
        return toDto(memo, user, operatorId, now);
    }

    public Memo update(Long userId, Long memoId, Long operatorId, String content) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Users user = load(userId);
        UserAdminMemo memo = editable(user, memoId, operatorId, now);
        memo.edit(validate(content), now);
        history.byOperator(userId, UserAdminEventType.MEMO_UPDATED, operatorId, operatorName, null,
                UserAdminHistoryRecorder.REF_MEMO, memoId, now);
        return toDto(memo, user, operatorId, now);
    }

    /** 삭제해도 처리 이력의 기입 기록은 남는다(§25-6). */
    public void delete(Long userId, Long memoId, Long operatorId) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        UserAdminMemo memo = editable(load(userId), memoId, operatorId, now);
        memo.delete(now);
        history.byOperator(userId, UserAdminEventType.MEMO_DELETED, operatorId, operatorName, null,
                UserAdminHistoryRecorder.REF_MEMO, memoId, now);
    }

    private MemoPage page(Users user, Long operatorId, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Page<UserAdminMemo> result = memos.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(user.getId(),
                PageRequest.of(page - 1, size));
        return new MemoPage(result.getTotalElements(),
                result.getContent().stream().map(m -> toDto(m, user, operatorId, now)).toList());
    }

    private UserAdminMemo editable(Users user, Long memoId, Long operatorId, LocalDateTime now) {
        requireNotWithdrawn(user);
        UserAdminMemo memo = memos.findByIdAndUserIdAndDeletedAtIsNull(memoId, user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_MEMO_NOT_FOUND));
        if (!memo.isAuthor(operatorId)) {
            throw new BusinessException(ErrorCode.USER_MEMO_NOT_AUTHOR);
        }
        if (!memo.isWithinEditWindow(now)) {
            throw new BusinessException(ErrorCode.USER_MEMO_EDIT_WINDOW_CLOSED);
        }
        return memo;
    }

    private static void requireNotWithdrawn(Users user) {
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_SANCTION_WITHDRAWN);
        }
    }

    private static String validate(String content) {
        String value = content == null ? "" : content.strip();
        if (value.isEmpty() || value.length() > UserAdminMemo.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메모는 1자 이상 300자 이내로 입력해 주세요.");
        }
        return value;
    }

    private Users load(Long userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private static Memo toDto(UserAdminMemo memo, Users user, Long operatorId, LocalDateTime now) {
        boolean editable = user.getStatus() != UserStatus.WITHDRAWN && memo.isEditableBy(operatorId, now);
        return new Memo(memo.getId(), memo.getAuthorName(), memo.getContent(), memo.getCreatedAt(),
                memo.getModifiedAt(), editable);
    }
}
