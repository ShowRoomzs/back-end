package showroomz.api.creator.common;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/** 로그인한 인플루언서 — 1017 신규 스튜디오 API(홈 · 알림 · 계정 상태)의 진입 규칙. 소비자 계정 id → 크리에이터. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorResolver {

    private final CreatorRepository creatorRepository;

    public Creator currentCreator() {
        return resolve(currentUserId());
    }

    public Creator resolve(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    public static Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUserId();
    }
}
