package showroomz.api.admin.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.brand.dto.AdminBrandDto;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandAdminMemo;
import showroomz.domain.member.brand.repository.BrandAdminMemoRepository;
import showroomz.domain.member.brand.service.BrandAdminHistoryRecorder;
import showroomz.domain.member.brand.type.BrandAdminEventType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 브랜드 관리자 메모(§52-2 · 52 설계서 5-1) — 누적 · 300자 · 작성자 본인 24시간 내 수정 · 삭제 · 탈퇴 계정은 조회 전용. 권한은 서버가 판정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminBrandMemoService {

    private final AdminBrandQueryService queries;
    private final AdminOperatorResolver operatorResolver;
    private final BrandAdminMemoRepository memoRepository;
    private final BrandAdminHistoryRecorder historyRecorder;

    public AdminBrandDto.Memo create(Long marketId, String content, Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        requireWritable(marketId);
        LocalDateTime now = LocalDateTime.now();
        BrandAdminMemo memo = memoRepository.save(BrandAdminMemo.write(marketId, operatorId, operatorName,
                validate(content), now));
        historyRecorder.byAdmin(marketId, BrandAdminEventType.MEMO_CREATED, operatorId, operatorName, null,
                BrandAdminHistoryRecorder.REF_MEMO, memo.getId(), now);
        return toMemo(memo, true);
    }

    public AdminBrandDto.Memo update(Long marketId, Long memoId, String content, Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        requireWritable(marketId);
        LocalDateTime now = LocalDateTime.now();
        BrandAdminMemo memo = editableMemo(marketId, memoId, operatorId, now);
        memo.edit(validate(content), now);
        historyRecorder.byAdmin(marketId, BrandAdminEventType.MEMO_UPDATED, operatorId, operatorName, null,
                BrandAdminHistoryRecorder.REF_MEMO, memo.getId(), now);
        return toMemo(memo, true);
    }

    public void delete(Long marketId, Long memoId, Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        requireWritable(marketId);
        LocalDateTime now = LocalDateTime.now();
        BrandAdminMemo memo = editableMemo(marketId, memoId, operatorId, now);
        memo.delete(now);
        historyRecorder.byAdmin(marketId, BrandAdminEventType.MEMO_DELETED, operatorId, operatorName, null,
                BrandAdminHistoryRecorder.REF_MEMO, memo.getId(), now);
    }

    private void requireWritable(Long marketId) {
        Market market = queries.loadBrand(marketId);
        if (market.getStatus() == MarketStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWN);
        }
    }

    private BrandAdminMemo editableMemo(Long marketId, Long memoId, Long operatorId, LocalDateTime now) {
        BrandAdminMemo memo = memoRepository.findByIdAndMarketIdAndDeletedAtIsNull(memoId, marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_MEMO_NOT_FOUND));
        if (!memo.isAuthor(operatorId)) {
            throw new BusinessException(ErrorCode.BRAND_MEMO_NOT_AUTHOR);
        }
        if (!memo.isWithinEditWindow(now)) {
            throw new BusinessException(ErrorCode.BRAND_MEMO_EDIT_WINDOW_CLOSED);
        }
        return memo;
    }

    /** 서버 검증 — 앞뒤 공백 제거 후 1 ~ 300자. */
    private static String validate(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() || trimmed.length() > BrandAdminMemo.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메모는 1자 이상 300자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private static AdminBrandDto.Memo toMemo(BrandAdminMemo memo, boolean editable) {
        return new AdminBrandDto.Memo(memo.getId(), memo.getAuthorName(), memo.getContent(), memo.getCreatedAt(),
                memo.getModifiedAt(), editable);
    }
}
