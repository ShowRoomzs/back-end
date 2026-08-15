package showroomz.api.app.showroom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.showroom.DTO.ShowroomVisitRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * §22-4 쇼룸 방문 적재 — 쇼룸 현황의 도달·유입 경로·팔로워 행동 지표가 여기서 쌓인 로그로 계산된다.
 *
 * <p>"순방문은 30분 세션 기준 1회"라는 규칙을 <b>적재 시점</b>에 적용한다. 집계 때 세션을 접으려면
 * 조회마다 원본 로그 전체를 훑어야 하는데, 방문 로그는 지표 중 가장 빨리 불어나는 데이터다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowroomVisitService {

    private final ShowroomVisitRepository showroomVisitRepository;
    private final CreatorRepository creatorRepository;
    private final UserRepository userRepository;

    /**
     * 방문 1건을 기록한다. 30분 세션 안의 재방문이면 아무것도 쌓지 않는다.
     *
     * @param username 로그인 방문이면 로그인 아이디, 비로그인 방문이면 null
     */
    @Transactional
    public void recordVisit(String username, Long showroomId, ShowroomVisitRequest request) {
        Creator creator = creatorRepository.findById(showroomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWROOM_NOT_FOUND));

        Users visitor = username == null ? null : userRepository.findByUsername(username).orElse(null);
        String visitorKey = resolveVisitorKey(visitor, request.getVisitorId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionStart = now.minusMinutes(ShowroomVisit.SESSION_MINUTES);
        if (showroomVisitRepository.existsByCreator_IdAndVisitorKeyAndVisitedAtAfter(
                creator.getId(), visitorKey, sessionStart)) {
            return;
        }

        showroomVisitRepository.save(new ShowroomVisit(
                creator,
                visitor,
                visitorKey,
                ShowroomVisitSource.fromLinkValue(request.getSource()),
                now));
    }

    /**
     * 사람 단위 식별자를 정한다.
     *
     * <p>로그인 방문은 사용자 기준으로 센다 — 같은 사람이 폰과 PC로 들어와도 방문자 수는 1명이어야 한다.
     * 비로그인 방문은 누구인지 알 수 없으므로 클라이언트가 보낸 디바이스 식별자에 기댄다. 그 값이 없으면
     * 방문마다 새 사람으로 잡혀 방문자 수가 부풀기 때문에, 조용히 세는 대신 요청을 거절한다.
     */
    private static String resolveVisitorKey(Users visitor, String visitorId) {
        if (visitor != null) {
            return "u:" + visitor.getId();
        }
        if (visitorId == null || visitorId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String trimmed = visitorId.trim();
        // 컬럼 길이(64)를 넘는 식별자는 잘라 넣는다 — 접두사 2자를 빼고 62자까지.
        return "d:" + (trimmed.length() > 62 ? trimmed.substring(0, 62) : trimmed);
    }
}
