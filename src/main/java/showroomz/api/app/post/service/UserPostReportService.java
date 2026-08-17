package showroomz.api.app.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.post.DTO.PostReportReasonItem;
import showroomz.api.app.post.DTO.PostReportRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostReportReason;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 게시물 신고 접수 (C4 게시물 헤더 ⋯ · C4 하단 고지 "게시물 신고").
 *
 * <p>운영자 조치(§24-5)의 <b>입구</b>다. 조치·이의 신청은 이미 있었지만 소비자가 문제를 올릴 창구가
 * 없어 진입이 운영자 수동 조작뿐이었다.
 *
 * <p>접수만 하고 아무것도 판단하지 않는다. 신고가 곧 자동 노출 중지로 이어지지 않는 이유 — 반려 시
 * 영구 삭제가 걸린 절차이고(§24-5), 자동화하면 경쟁 쇼룸을 신고로 내리는 길이 열린다. 판단은 사람이
 * 하고, 서버는 대기열에 쌓아 순서만 지킨다.
 *
 * <p>로그인을 요구한다. C4의 "비로그인 열람 자유"는 <b>읽기</b>에 걸린 규칙이고, 팔로우·♥와 마찬가지로
 * 남기는 동작은 로그인 후에 이어서 실행된다. 익명 신고를 받으면 사람당 1회를 셀 수 없어 대기열이
 * 같은 사람의 반복으로 채워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPostReportService {

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;

    /** 신고 시트의 선택지 — 어드민 조치 화면과 같은 코드 축을 쓴다 */
    public List<PostReportReasonItem> getReportReasons() {
        return Arrays.stream(PostReportReason.values())
                .map(reason -> new PostReportReasonItem(reason, reason.getLabel(), reason.requiresDetail()))
                .toList();
    }

    @Transactional
    public void reportPost(String username, Long postId, PostReportRequest request) {
        Users reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 보이지 않는 게시물은 소비자에게 "없는 것"이다 — 작성중·노출 중지·삭제를 구분해 알려주면
        // 이미 조치가 들어갔다는 사실이 신고자를 통해 새어 나간다(상세 조회와 같은 규칙).
        if (!post.isVisibleToConsumer()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (request.getReasonCode().requiresDetail()
                && (request.getReasonDetail() == null || request.getReasonDetail().isBlank())) {
            throw new BusinessException(ErrorCode.POST_REPORT_DETAIL_REQUIRED);
        }

        // 자기 게시물은 신고 대상이 아니다 — 내리고 싶으면 스튜디오에서 삭제하면 된다(§24-3).
        if (isOwnPost(post, reporter)) {
            throw new BusinessException(ErrorCode.POST_REPORT_SELF_NOT_ALLOWED);
        }

        if (postReportRepository.existsByPost_IdAndReporter_Id(postId, reporter.getId())) {
            throw new BusinessException(ErrorCode.POST_REPORT_ALREADY_SUBMITTED);
        }

        try {
            postReportRepository.save(new PostReport(
                    post, reporter, request.getReasonCode(), request.getReasonDetail(), LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            // 위 검사와 저장 사이를 파고든 동시 요청 — 유니크가 잡아 준 것을 같은 응답으로 되돌린다
            throw new BusinessException(ErrorCode.POST_REPORT_ALREADY_SUBMITTED);
        }
    }

    /** 쇼룸 주인의 앱 계정과 대조한다 — 크리에이터도 소비자 앱에서는 같은 사용자 계정으로 움직인다 */
    private static boolean isOwnPost(Post post, Users reporter) {
        Users owner = post.getCreator().getUser();
        return owner != null && owner.getId().equals(reporter.getId());
    }
}
