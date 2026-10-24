package showroomz.domain.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.repository.DeviceTokenRow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소비자 알림 → 앱 푸시(1017 설계서 1-6). 거래 알림은 커밋 직후 바로, 광고성은 지연 표에서 꺼낸 뒤 보낸다.
 *
 * <p>광고성 푸시는 제목 앞 「(광고)」 · 본문 끝 수신거부 방법을 붙인다(정보통신망법 §50 · C11). {@code data}는 앱 딥링크 —
 * {@code type=NOTIFICATION} · 알림 유형 · 이동 대상. 실패는 알림함 행을 지우지 않는다(목록에서 다시 볼 수 있다).
 */
@Slf4j
@Component
public class NotificationPushDispatcher {

    public static final String DATA_TYPE = "NOTIFICATION";
    static final String AD_PREFIX = "(광고) ";
    static final String AD_OPT_OUT = "수신거부: 마이 > 설정 > 알림";
    private static final int CHUNK_SIZE = 500;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final PushSender pushSender;
    private final TransactionTemplate newTransaction;

    public NotificationPushDispatcher(DeviceTokenRepository deviceTokenRepository,
                                      DeviceTokenService deviceTokenService, PushSender pushSender,
                                      PlatformTransactionManager transactionManager) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.deviceTokenService = deviceTokenService;
        this.pushSender = pushSender;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 거래 알림 — 커밋 뒤 바로. */
    public void sendNow(Notification notification) {
        send(notification.getRecipientId(), new PushMessage(title(notification), body(notification), null,
                data(notification)));
    }

    /** 지연 표 한 건 — @return 성공 여부(토큰이 없으면 보낼 곳이 없을 뿐 실패가 아니다). */
    public boolean send(Long userId, String title, String body, String imageUrl, String dataJson) {
        return send(userId, new PushMessage(title, body, imageUrl, parse(dataJson)));
    }

    public String title(Notification n) {
        return n.isAdvertising() ? AD_PREFIX + n.getTitle() : n.getTitle();
    }

    public String body(Notification n) {
        if (!n.isAdvertising()) {
            return n.getBody();
        }
        return n.getBody() == null ? AD_OPT_OUT : n.getBody() + "\n" + AD_OPT_OUT;
    }

    public String dataJson(Notification n) {
        try {
            return MAPPER.writeValueAsString(data(n));
        } catch (Exception e) {
            return "{}";
        }
    }

    static Map<String, String> data(Notification n) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", DATA_TYPE);
        data.put("notificationId", String.valueOf(n.getId()));
        data.put("notificationType", n.getType().name());
        if (n.getTargetType() != null) {
            data.put("targetType", n.getTargetType().name());
        }
        if (n.getTargetId() != null) {
            data.put("targetId", String.valueOf(n.getTargetId()));
        }
        if (n.getTargetSub() != null) {
            data.put("targetSub", n.getTargetSub());
        }
        return data;
    }

    private static Map<String, String> parse(String json) {
        try {
            return json == null ? Map.of() : MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean send(Long userId, PushMessage message) {
        try {
            PushResult result = newTransaction.execute(status -> sendAll(userId, message));
            return result != null;
        } catch (RuntimeException e) {
            log.warn("알림 푸시 실패 - userId: {}", userId, e);
            return false;
        }
    }

    private PushResult sendAll(Long userId, PushMessage message) {
        Pageable chunk = PageRequest.of(0, CHUNK_SIZE);
        PushResult total = PushResult.none();
        long afterId = 0L;
        while (true) {
            List<DeviceTokenRow> rows = deviceTokenRepository.findUserTokens(userId, afterId, chunk);
            if (rows.isEmpty()) {
                return total;
            }
            afterId = rows.get(rows.size() - 1).id();
            PushResult result = pushSender.send(rows.stream().map(DeviceTokenRow::token).toList(), message);
            deviceTokenService.purgeInvalid(result.invalidTokens());
            total = total.plus(new PushResult(result.successCount(), result.failureCount(), List.of()));
            if (rows.size() < CHUNK_SIZE) {
                return total;
            }
        }
    }
}
