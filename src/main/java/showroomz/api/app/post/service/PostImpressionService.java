package showroomz.api.app.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.post.DTO.PostImpressionRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.service.PostAttributionService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 게시물 노출 적재 (§24-7).
 *
 * <p>인사이트 3단이 전부 이 로그 위에 선다. 누적 카운터만 있으면 기간 필터·연령 분포·행동 귀속을
 * 만들 수 없기 때문에, <b>카운터를 올리는 것과 같은 자리에서 로그도 남긴다.</b> 둘 중 하나만 하면
 * 목록의 숫자와 인사이트의 숫자가 갈린다.
 *
 * <p>중복 노출은 쇼룸 방문(§22-4)과 <b>같은 30분 세션 규칙</b>으로 적재 시점에 거른다. 집계 때 접으면
 * 조회마다 원본 로그를 훑어야 하는데, 노출 로그는 이 서비스에서 가장 빨리 불어나는 데이터다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostImpressionService {

    /**
     * 한 요청이 실을 수 있는 최대 게시물 수.
     *
     * <p>배치로 묶는 목적이 요청 수를 줄이는 것이지 한 요청을 무한정 키우는 게 아니다.
     * 상한이 없으면 노출 적재가 곧 대량 쓰기 창구가 된다.
     */
    private static final int MAX_BATCH_SIZE = 50;

    private final PostRepository postRepository;
    private final PostImpressionRepository postImpressionRepository;
    private final UserRepository userRepository;

    /**
     * @param username 로그인 조회면 로그인 아이디, 비로그인 조회면 null
     */
    public void recordImpressions(String username, PostImpressionRequest request) {
        Users viewer = username == null ? null : userRepository.findByUsername(username).orElse(null);
        String viewerKey = PostAttributionService.viewerKeyOf(
                viewer == null ? null : viewer.getId(), request.getVisitorId());
        if (viewerKey == null) {
            // 식별자가 없으면 같은 사람의 재노출을 접을 수 없어 노출 수가 부풀고 귀속도 성립하지 않는다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<Long> postIds = distinctIds(request.getPostIds());
        if (postIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionStart = now.minusMinutes(PostImpression.SESSION_MINUTES);

        // 배치 전체를 두 번의 조회로 판정한다 — 게시물 조회와 세션 판정을 카드마다 하면
        // 상한(50건)까지 채운 요청 하나가 조회 100번이 된다.
        Set<Long> alreadyCounted = Set.copyOf(
                postImpressionRepository.findCountedPostIds(postIds, viewerKey, sessionStart));

        for (Post post : postRepository.findAllById(postIds)) {
            // 없거나 이미 내려간 게시물의 노출은 조용히 버린다 — 화면에 떠 있던 카드가 그 사이 내려갔을 뿐이고,
            // 노출 적재 실패로 피드 스크롤이 멈출 이유는 없다. (없는 게시물은 조회 결과에서 그냥 빠진다)
            if (!post.isVisibleToConsumer() || alreadyCounted.contains(post.getId())) {
                continue;
            }
            postImpressionRepository.save(new PostImpression(
                    post, post.getCreator().getId(), viewer, viewerKey, now));
            post.increaseImpressionCount();
        }
    }

    /** 같은 카드가 스크롤 중 여러 번 담겨 오는 일이 흔하므로 요청 안에서 먼저 접는다 */
    private static List<Long> distinctIds(List<Long> postIds) {
        Set<Long> distinct = new LinkedHashSet<>(postIds);
        distinct.remove(null);
        return distinct.stream().limit(MAX_BATCH_SIZE).toList();
    }
}
