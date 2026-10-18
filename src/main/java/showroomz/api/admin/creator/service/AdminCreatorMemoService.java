package showroomz.api.admin.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.creator.dto.AdminCreatorDto.Memo;
import showroomz.api.admin.creator.dto.AdminCreatorDto.MemoPage;
import showroomz.domain.member.common.service.AdminMemoPolicy;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorAdminMemo;
import showroomz.domain.member.creator.repository.CreatorAdminMemoRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.service.CreatorAdminHistoryRecorder;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 인플루언서 관리자 메모(§53-1 · 52-2 와 같은 규칙 · 53 설계서 3-7) — 누적 · 항목당 300자(서버 검증) · 작성자 본인 24시간 내 수정 · 삭제 ·
 * 인플루언서 비노출. {@code editable}은 서버가 판정해 내린다. 탈퇴 회원은 조회 전용.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminCreatorMemoService {

    private final CreatorRepository creators;
    private final CreatorAdminMemoRepository memos;
    private final CreatorAdminHistoryRecorder history;
    private final AdminOperatorResolver operators;

    @Transactional(readOnly = true)
    public MemoPage list(Long creatorId, Long operatorId, int page, int size) {
        load(creatorId);
        LocalDateTime now = LocalDateTime.now();
        Page<CreatorAdminMemo> result = memos.findByCreatorIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(creatorId,
                PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100)));
        return new MemoPage(result.getTotalElements(), result.getContent().stream().map(m -> toDto(m, operatorId, now)).toList());
    }

    public Memo create(Long creatorId, Long operatorId, String content) {
        String operatorName = operators.operatorName(operatorId);
        Creator creator = load(creatorId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        LocalDateTime now = LocalDateTime.now();
        CreatorAdminMemo memo = memos.save(CreatorAdminMemo.write(creatorId, operatorId, operatorName, AdminMemoPolicy.normalize(content), now));
        history.byOperator(creatorId, CreatorAdminEventType.MEMO_CREATED, operatorId, operatorName, null,
                CreatorAdminHistoryRecorder.REF_MEMO, memo.getId(), now);
        return toDto(memo, operatorId, now);
    }

    public Memo update(Long creatorId, Long memoId, Long operatorId, String content) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        CreatorAdminMemo memo = editable(creatorId, memoId, operatorId, now);
        memo.edit(AdminMemoPolicy.normalize(content), now);
        history.byOperator(creatorId, CreatorAdminEventType.MEMO_UPDATED, operatorId, operatorName, null,
                CreatorAdminHistoryRecorder.REF_MEMO, memoId, now);
        return toDto(memo, operatorId, now);
    }

    public void delete(Long creatorId, Long memoId, Long operatorId) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        CreatorAdminMemo memo = editable(creatorId, memoId, operatorId, now);
        memo.delete(now);
        history.byOperator(creatorId, CreatorAdminEventType.MEMO_DELETED, operatorId, operatorName, null,
                CreatorAdminHistoryRecorder.REF_MEMO, memoId, now);
    }

    private CreatorAdminMemo editable(Long creatorId, Long memoId, Long operatorId, LocalDateTime now) {
        Creator creator = load(creatorId);
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        CreatorAdminMemo memo = memos.findByIdAndCreatorIdAndDeletedAtIsNull(memoId, creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_MEMO_NOT_FOUND));
        AdminMemoPolicy.requireEditable(memo, operatorId, now, ErrorCode.CREATOR_MEMO_NOT_AUTHOR,
                ErrorCode.CREATOR_MEMO_EDIT_WINDOW_CLOSED);
        return memo;
    }

    private Creator load(Long creatorId) {
        return creators.findById(creatorId).orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private static Memo toDto(CreatorAdminMemo memo, Long operatorId, LocalDateTime now) {
        return new Memo(memo.getId(), memo.getAuthorName(), memo.getContent(), memo.getCreatedAt(), memo.getModifiedAt(),
                memo.isEditableBy(operatorId, now));
    }
}
