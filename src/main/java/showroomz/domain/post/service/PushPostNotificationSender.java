package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.repository.DeviceTokenRow;
import showroomz.domain.notification.service.DeviceTokenService;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostNotificationEvent;

import java.util.List;

/**
 * 통지 이력 한 건을 실제 푸시로 바꾼다 — <b>받는 사람이 누구인지</b>가 이 클래스의 일이다.
 *
 * <p>이력에는 {@code creatorId}밖에 없지만 수신자는 통지 종류에 따라 정반대다.
 * 노출 중지·이의 신청 결과는 <b>크리에이터 본인</b>에게 가고, 신규 게시물은 그 쇼룸의
 * <b>팔로워 전원</b>에게 간다. 이력 테이블에 수신자를 적지 않는 이유이기도 하다 —
 * 팔로워 알림의 수신자는 수만 명이고, 그들을 이력에 펼치면 게시물 하나에 수만 행이 생긴다.
 *
 * <p>팔로워 발송은 <b>500명씩 끊어 읽고 끊어 보낸다.</b> 전부 메모리에 올리면 팔로워가 많은
 * 쇼룸에서 그대로 터진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushPostNotificationSender implements PostNotificationSender {

    /** FCM 멀티캐스트 상한과 맞춘다 — 읽어온 만큼이 곧 한 번의 발송이다 */
    private static final int CHUNK_SIZE = 500;

    private final PostRepository postRepository;
    private final CreatorRepository creatorRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final PushSender pushSender;
    private final PostPushMessageFactory messageFactory;

    @Override
    public boolean send(PostNotificationLog notificationLog) {
        Creator creator = creatorRepository.findById(notificationLog.getCreatorId()).orElse(null);
        if (creator == null) {
            log.warn("통지 대상 쇼룸이 없어 발송하지 않음 - logId={}, creatorId={}",
                    notificationLog.getId(), notificationLog.getCreatorId());
            return false;
        }

        // 파기된 게시물일 수 있다 — 문구는 게시물 없이도 만들어진다
        Post post = postRepository.findById(notificationLog.getPostId()).orElse(null);

        PushMessage message = messageFactory.create(notificationLog, creator, post);
        if (message == null) {
            // 푸시하지 않기로 한 종류(본인 삭제 등). 이력은 이미 남았다.
            return false;
        }

        PushResult result = notificationLog.getEventType() == PostNotificationEvent.PUBLISHED_TO_FOLLOWERS
                ? sendToFollowers(creator.getId(), message)
                : sendToCreator(creator, message);

        log.info("게시물 통지 발송 - logId={}, event={}, 성공={}, 실패={}",
                notificationLog.getId(), notificationLog.getEventType(),
                result.successCount(), result.failureCount());

        return result.anyDelivered();
    }

    /**
     * 팔로워 전원 — 알림을 끈 사람과 탈퇴·정지 회원은 쿼리에서 이미 빠져 있다.
     *
     * <p>키셋({@code afterId})으로 끊어 읽는다. 발송 도중 만료 토큰을 지우기 때문에 OFFSET
     * 페이징을 쓰면 삭제된 수만큼 뒤 페이지가 당겨져 <b>일부 팔로워가 통째로 건너뛰어진다.</b>
     */
    private PushResult sendToFollowers(Long creatorId, PushMessage message) {
        Pageable chunk = PageRequest.of(0, CHUNK_SIZE);
        PushResult total = PushResult.none();
        long afterId = 0L;

        while (true) {
            List<DeviceTokenRow> rows =
                    deviceTokenRepository.findFollowerTokens(creatorId, UserStatus.NORMAL, afterId, chunk);
            if (rows.isEmpty()) {
                return total;
            }

            afterId = rows.get(rows.size() - 1).id();
            total = total.plus(sendAndPurge(rows, message));

            if (rows.size() < CHUNK_SIZE) {
                return total;
            }
        }
    }

    /**
     * 크리에이터 본인 — 기기가 여러 대일 수 있어 같은 방식으로 끊어 읽는다.
     *
     * <p>{@code follow_post_push_agree}를 보지 않는다. 그 토글은 "팔로우한 쇼룸의 새 게시물"만
     * 끄는 값이고, 내 게시물이 내려갔다는 통지는 끌 수 있는 알림이 아니다(§24-5).
     */
    private PushResult sendToCreator(Creator creator, PushMessage message) {
        Long userId = creator.getUser() == null ? null : creator.getUser().getId();
        if (userId == null) {
            return PushResult.none();
        }

        Pageable chunk = PageRequest.of(0, CHUNK_SIZE);
        PushResult total = PushResult.none();
        long afterId = 0L;

        while (true) {
            List<DeviceTokenRow> rows = deviceTokenRepository.findUserTokens(userId, afterId, chunk);
            if (rows.isEmpty()) {
                return total;
            }

            afterId = rows.get(rows.size() - 1).id();
            total = total.plus(sendAndPurge(rows, message));

            if (rows.size() < CHUNK_SIZE) {
                return total;
            }
        }
    }

    /**
     * 한 묶음을 보내고 죽은 토큰을 바로 지운다.
     *
     * <p>전부 모았다가 마지막에 지우지 않는 이유 — 팔로워 수만 명이면 죽은 토큰 목록만으로도
     * 메모리를 크게 먹는다. 지우는 대상은 이미 지나온 구간(id ≤ afterId)이라 키셋 페이징을
     * 흔들지 않는다.
     */
    private PushResult sendAndPurge(List<DeviceTokenRow> rows, PushMessage message) {
        PushResult result = pushSender.send(rows.stream().map(DeviceTokenRow::token).toList(), message);
        deviceTokenService.purgeInvalid(result.invalidTokens());
        return new PushResult(result.successCount(), result.failureCount(), List.of());
    }
}
