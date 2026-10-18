package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.user.dto.AdminUserDto.ReleaseRequest;
import showroomz.api.admin.user.dto.AdminUserDto.StatusResult;
import showroomz.api.admin.user.dto.AdminUserDto.SuspendRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.service.UserSanctionService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 소비자 정지 · 해제(51-6 설계서 2절) — 운영자를 확인해 도메인 서비스에 넘기고, 갱신된 상태 카드와 처리 이력 최신 5건으로 답한다(상세 전체를
 * 다시 부르지 않게). 정지 · 해제의 규칙은 {@link UserSanctionService}가 정본이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserCommandService {

    private static final int HISTORY_LIMIT = 5;

    private final UserSanctionService sanctionService;
    private final UserRepository userRepository;
    private final AdminOperatorResolver operators;
    private final AdminUserStatusCardAssembler statusCards;
    private final AdminUserHistoryReader historyReader;

    public StatusResult suspend(Long userId, Long operatorId, SuspendRequest request) {
        String operatorName = operators.operatorName(operatorId);
        sanctionService.suspend(userId, request.reason(), request.reasonDetail(), operatorId, operatorName,
                LocalDateTime.now());
        return result(userId);
    }

    public StatusResult release(Long userId, Long operatorId, ReleaseRequest request) {
        String operatorName = operators.operatorName(operatorId);
        sanctionService.release(userId, request == null ? null : request.reason(), operatorId, operatorName,
                LocalDateTime.now());
        return result(userId);
    }

    private StatusResult result(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new StatusResult(statusCards.statusCard(user), historyReader.summary(user, HISTORY_LIMIT));
    }
}
