package showroomz.domain.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.repository.PostNotificationLogRepository;
import showroomz.domain.post.type.PostNotificationEvent;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 게시물 통지 — <b>이력을 먼저 남기고</b> 발송을 시도한다 (§24-5 · §24-6).
 *
 * <p>순서가 뒤집히면 안 된다. §24-5는 "알리지 않고 사라지는 경우는 없다"를 요구하고 §24-6은
 * 삭제 이력의 영구 보존을 요구하는데, 발송이 먼저면 발송 성공 후 적재 실패했을 때
 * 무슨 일이 있었는지가 사라진다.
 *
 * <p>{@code payload}에 통지 당시 문구를 굳히는 이유도 같다 — 게시물은 보관 기간이 끝나면 파기되므로,
 * 사유·근거 규정·기한을 나중에 게시물에서 다시 읽어 재구성할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostNotificationService {

    private final PostNotificationLogRepository postNotificationLogRepository;
    private final PostNotificationSender postNotificationSender;
    private final ObjectMapper objectMapper;

    public void notify(Post post, PostNotificationEvent event, Map<String, Object> payload) {
        notify(post.getId(), post.getCreator().getId(), event, payload);
    }

    public void notify(Long postId, Long creatorId, PostNotificationEvent event, Map<String, Object> payload) {
        PostNotificationLog notificationLog = postNotificationLogRepository.save(new PostNotificationLog(
                postId, creatorId, event, serialize(payload), LocalDateTime.now()));

        if (postNotificationSender.send(notificationLog)) {
            notificationLog.markDelivered();
        }
    }

    /** 통지 문구를 만들 때 쓰는 편의 — 순서를 지키려고 {@link LinkedHashMap}을 쓴다 */
    public static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }

    /**
     * 직렬화가 실패해도 통지 자체를 막지 않는다 — payload는 부가 정보고,
     * "언제 무슨 일이 있었는지"는 event_type과 sent_at만으로도 남는다.
     */
    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("게시물 통지 payload 직렬화 실패 - keys={}", payload.keySet(), e);
            return null;
        }
    }
}
