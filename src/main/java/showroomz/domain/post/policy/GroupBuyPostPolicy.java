package showroomz.domain.post.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 공구 게시물 규칙(31 설계 2-2). {@link GeneralPostPolicy}와 달리 판단에 필요한 상태가 게시물 밖
 * ({@code group_buy_post} · {@code group_buy})에 있어 리포지토리를 주입받는다.
 *
 * <p>이 빈이 없으면 공구 게시물이 생기는 순간 {@link PostPolicies#of}가 예외를 던지고 소비자 좋아요가 500이 된다 —
 * 게시물 타입과 무관하게 {@code canLike}를 부르기 때문이다(31 설계 0-6).
 *
 * <p>길이는 {@code String.length()}(UTF-16)로 센다 — 일반 게시물과 같은 방식이다. 한쪽만 코드포인트로 바꾸면
 * 같은 문장이 한 화면에서는 들어가고 다른 화면에서는 안 들어간다(31 설계 7절).
 */
@Component
@RequiredArgsConstructor
public class GroupBuyPostPolicy implements PostPolicy {

    /** 제목 상한 — 앱 검증(§31-2 · 근거 대기 ②). 컬럼은 100자로 여유를 둔다. */
    public static final int MAX_TITLE_LENGTH = 40;

    public static final int MAX_CONTENT_LENGTH = Post.MAX_CONTENT_LENGTH;

    private final GroupBuyPostRepository groupBuyPostRepository;

    @Override
    public PostType supports() {
        return PostType.GROUP_BUY;
    }

    /** 제출 조건 — 제목·본문 필수 · 상한 · 사진 0장. 제출(2-4)과 승인 후 수정(2-5)이 부른다. */
    @Override
    public void validateForPublish(Post post) {
        if (!post.getImages().isEmpty()) {
            // 공구 게시물은 사진이 없다(§31-2). 공구 API에 업로드 경로가 없으니 여기 오면 다른 경로가 샌 것이다.
            throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
        }
        validateRequired(load(post).getTitle(), post.getContent());
    }

    /** 승인 후 수정 판정 — 심사 상태 × 공구 상태(31 설계 2-5 표). */
    @Override
    public void validateEditable(Post post) {
        requireEditable(load(post));
    }

    /**
     * 노출중이고 종료 시각 전일 때만 — 마감·숨김·예약 게시물은 좋아요를 받지 않는다. 품절은 막지 않는다.
     *
     * <p>종료 후 3일 동안 남는 마감 게시물은 여기서 잠긴다(해제만). {@code end_at}도 보는 이유 — 종료 스케줄러의
     * 최대 1분 지연 동안 상태는 아직 IN_PROGRESS다(공구 게시물 설계 4-3 · 4-4).
     *
     * <p>목록은 이 메서드를 부르지 않는다 — 카드 로더가 이미 읽은 판매 상태로 {@code likeLocked}를 계산한다(6-3).
     */
    @Override
    public boolean canLike(Post post) {
        LocalDateTime now = LocalDateTime.now();
        return groupBuyPostRepository.findById(post.getId())
                .map(groupBuyPost -> GroupBuyPostStatus.of(groupBuyPost, groupBuyPost.getGroupBuy())
                        == GroupBuyPostStatus.EXPOSED
                        && groupBuyPost.getGroupBuy().getEndAt().isAfter(now))
                .orElse(false);
    }

    /** 대가관계 표시는 항상 붙는다 — 저장하지 않고 렌더링 시점에 타입으로 붙인다(31 설계 2-8). */
    @Override
    public boolean requiresAdDisclosure() {
        return true;
    }

    // ── 스튜디오 쓰기 API가 함께 쓰는 판정 ─────────────────────────────────────

    /** 임시저장 — 상한만 보고, 둘 다 비었으면 막는다(빈 임시저장 방지). 필수 검증은 제출 시점의 일이다. */
    public static void validateDraft(String title, String content) {
        validateLength(title, content);
        if (isBlank(title) && isBlank(content)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_EMPTY_DRAFT);
        }
    }

    /** 제출 · 승인 후 수정 — 노출 중인 게시물의 제목·본문이 사라지면 안 된다. */
    public static void validateRequired(String title, String content) {
        validateLength(title, content);
        if (isBlank(title) || isBlank(content)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_REQUIRED_FIELD);
        }
    }

    /**
     * 승인 후 수정 가능 — APPROVED ∧ 공구 READY · IN_PROGRESS(숨김 포함). 승인대기는 별도 코드로 알린다.
     *
     * <p><b>중단 예정(SUSPENSION_SCHEDULED)은 잠근다</b> — 직권 중단은 통지 시점의 게시물을 근거로 집행 여부를
     * 판정하는 절차라 판정 도중 근거가 바뀌면 안 된다. 시안 B13에 [게시물 수정]이 없다(31 설계 2-5 · 10-1 #13).
     */
    public static void requireEditable(GroupBuyPost groupBuyPost) {
        if (groupBuyPost == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_NOT_EDITABLE);
        }
        if (groupBuyPost.isPendingReview()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_UNDER_REVIEW);
        }
        if (!isEditable(groupBuyPost, groupBuyPost.getGroupBuy())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_NOT_EDITABLE);
        }
    }

    public static boolean isEditable(GroupBuyPost groupBuyPost, GroupBuy groupBuy) {
        GroupBuyStatus status = groupBuy.getStatus();
        return groupBuyPost != null && groupBuyPost.isApproved()
                && (status == GroupBuyStatus.READY || status == GroupBuyStatus.IN_PROGRESS);
    }

    private GroupBuyPost load(Post post) {
        return groupBuyPostRepository.findById(post.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private static void validateLength(String title, String content) {
        if ((title != null && title.length() > MAX_TITLE_LENGTH)
                || (content != null && content.length() > MAX_CONTENT_LENGTH)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_TOO_LONG);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
