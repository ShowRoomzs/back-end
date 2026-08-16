package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.repository.PostImpressionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * §24-7 라스트 터치 귀속 — "이 게시물을 보고 한 행동"을 정하는 규칙 한 벌.
 *
 * <p>규칙은 두 줄이다. <b>게시물을 본 뒤 24시간 이내</b>의 행동만 세고,
 * 여러 게시물을 봤다면 <b>마지막에 본 게시물</b>의 몫으로 돌린다.
 *
 * <p>귀속을 <b>행동이 일어나는 시점</b>에 계산해 태그로 굳히는 이유 — 나중에 집계하면서 매번
 * 로그를 되짚으면 방문 한 건마다 노출 로그를 훑어야 하고, 노출 로그는 가장 빨리 불어나는 데이터다.
 * 게다가 귀속은 그 시점의 사실이라 나중에 바뀌면 안 된다.
 *
 * <p>쇼룸을 함께 넘기는 이유 — 방문·팔로우는 쇼룸 단위 행동이라, 다른 쇼룸의 게시물에 귀속시키면
 * 그 인플루언서의 인사이트에 남의 성과가 섞인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostAttributionService {

    private final PostImpressionRepository postImpressionRepository;

    /**
     * @return 귀속될 게시물 ID. 귀속 창 안에 본 게시물이 없으면 {@code null}(귀속 불명)이다
     */
    public Long resolveAttributedPostId(String viewerKey, Long creatorId, LocalDateTime now) {
        if (viewerKey == null || creatorId == null) {
            return null;
        }
        LocalDateTime since = now.minusHours(PostImpression.ATTRIBUTION_WINDOW_HOURS);
        List<Long> recent = postImpressionRepository.findRecentlyViewedPostIds(viewerKey, creatorId, since);
        return recent.isEmpty() ? null : recent.get(0);
    }

    /**
     * 사람 단위 식별자 — {@code showroom_visit.visitorKey}와 <b>같은 규칙</b>이어야 한다.
     * 규칙이 어긋나는 순간 귀속은 전부 불명이 된다.
     */
    public static String viewerKeyOf(Long userId, String deviceId) {
        if (userId != null) {
            return "u:" + userId;
        }
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        String trimmed = deviceId.trim();
        // 컬럼 길이(64)를 넘는 식별자는 접두사 2자를 빼고 62자까지 잘라 넣는다.
        return "d:" + (trimmed.length() > 62 ? trimmed.substring(0, 62) : trimmed);
    }
}
