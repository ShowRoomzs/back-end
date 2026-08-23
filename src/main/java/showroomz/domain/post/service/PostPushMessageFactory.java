package showroomz.domain.post.service;

import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.type.PostNotificationEvent;

/**
 * 통지 종류별 알림 문구.
 *
 * <p>사유·기한 같은 <b>구체 값은 본문에 넣지 않는다.</b> 알림은 두 줄이라 잘리고, 잘린 사유는
 * 오해를 부른다. 무슨 일이 있었는지만 알리고 상세는 {@code data}에 실은 게시물로 들어가 보게 한다.
 * ({@code post_notification_log.payload}에 굳혀 둔 문구가 그 화면의 원본이다.)
 *
 * <p>{@code null}을 돌려주면 <b>보내지 않는다.</b> 본인이 방금 한 조작(직접 삭제)까지 알림으로
 * 되돌려주면 잡음이 된다 — 이력은 남기되 푸시는 하지 않는다.
 */
@Component
public class PostPushMessageFactory {

    /** 알림 본문에 실을 본문 미리보기 길이 — 두 줄에 들어가는 분량 */
    private static final int BODY_PREVIEW_LENGTH = 40;

    private static final String DATA_KEY_TYPE = "type";
    private static final String DATA_KEY_POST_ID = "postId";
    private static final String DATA_KEY_CREATOR_ID = "creatorId";

    /**
     * @param post 파기됐거나 아직 못 읽은 경우 null일 수 있다 — 문구가 게시물에 의존하지 않게 짠다
     */
    public PushMessage create(PostNotificationLog notificationLog, Creator creator, Post post) {
        PostNotificationEvent event = notificationLog.getEventType();
        return switch (event) {
            case PUBLISHED_TO_FOLLOWERS -> followerMessage(notificationLog, creator, post);
            case SUSPENDED -> creatorMessage(notificationLog, event,
                    "게시물 노출이 중지되었어요", "사유와 이의 신청 기한을 확인해 주세요.");
            case APPEAL_RECEIVED -> creatorMessage(notificationLog, event,
                    "이의 신청이 접수되었어요", "심사 결과가 나오면 다시 알려드릴게요.");
            case APPEAL_APPROVED -> creatorMessage(notificationLog, event,
                    "게시물이 다시 노출돼요", "이의 신청이 승인되었어요.");
            case APPEAL_REJECTED -> creatorMessage(notificationLog, event,
                    "이의 신청이 반려되었어요", "원본을 내려받을 수 있는 기간을 확인해 주세요.");
            case DELETED_BY_EXPIRE -> creatorMessage(notificationLog, event,
                    "게시물이 삭제되었어요", "이의 신청 기한이 지나 자동으로 삭제되었어요.");
            // 본인이 방금 한 조작이다 — 이력에는 남지만 알림으로 되돌려주지 않는다
            case DELETED_BY_SELF -> null;
        };
    }

    /**
     * 팔로워에게 가는 알림 — 제목이 <b>쇼룸 이름</b>이다.
     *
     * <p>"새 게시물이 올라왔어요"를 제목으로 두면 여러 쇼룸의 알림이 목록에서 전부 같아 보인다.
     * 누가 올렸는지가 열어볼지를 정하는 정보다.
     */
    private PushMessage followerMessage(PostNotificationLog notificationLog, Creator creator, Post post) {
        String body = post == null ? null : preview(post.getContent());
        return PushMessage.of(
                creator.getShowroomName(),
                body == null || body.isBlank() ? "새 게시물을 올렸어요." : body,
                thumbnailOf(post),
                DATA_KEY_TYPE, PostNotificationEvent.PUBLISHED_TO_FOLLOWERS.name(),
                DATA_KEY_POST_ID, notificationLog.getPostId(),
                DATA_KEY_CREATOR_ID, notificationLog.getCreatorId());
    }

    private PushMessage creatorMessage(PostNotificationLog notificationLog, PostNotificationEvent event,
                                       String title, String body) {
        return PushMessage.of(title, body, null,
                DATA_KEY_TYPE, event.name(),
                DATA_KEY_POST_ID, notificationLog.getPostId(),
                DATA_KEY_CREATOR_ID, notificationLog.getCreatorId());
    }

    /** 대표 사진(sort_order 0)을 알림 이미지로 쓴다 — 사진만 있는 게시물이 정상이라 본문보다 이쪽이 확실하다 */
    private String thumbnailOf(Post post) {
        if (post == null || post.getImages().isEmpty()) {
            return null;
        }
        PostImage first = post.getImages().get(0);
        return first == null ? null : first.getImageUrl();
    }

    private static String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        return flattened.length() <= BODY_PREVIEW_LENGTH
                ? flattened
                : flattened.substring(0, BODY_PREVIEW_LENGTH) + "…";
    }
}
