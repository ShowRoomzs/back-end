package showroomz.domain.notification.infra;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;

import java.util.ArrayList;
import java.util.List;

/**
 * FCM HTTP v1 발송 어댑터.
 *
 * <p><b>500개씩 끊는다.</b> FCM 멀티캐스트의 하드 리밋이고, 넘기면 요청 전체가 거절된다.
 * 팔로워 수천 명인 쇼룸이 정상 케이스라 호출부가 아니라 여기서 나눈다.
 *
 * <p><b>죽은 토큰을 골라 올려보낸다.</b> {@code UNREGISTERED}(앱 삭제·재설치)와
 * {@code INVALID_ARGUMENT}(형식이 깨진 토큰)는 재시도해도 영원히 실패한다. 지우지 않으면 발송할
 * 때마다 같은 실패를 반복하고, 팔로워가 늘수록 죽은 토큰 비중이 커져 발송 시간이 늘어난다.
 * 그 외 오류(서버 일시 장애 등)는 <b>지우지 않는다</b> — 일시 장애로 멀쩡한 기기를 잘라내면
 * 그 사람은 재로그인 전까지 알림을 못 받는다.
 *
 * <p>발송 실패는 예외로 올리지 않는다. 알림이 안 간 것 때문에 게시물 등록이 실패하면 안 되고,
 * 무엇이 안 나갔는지는 {@code post_notification_log.delivered}에 남는다.
 */
@Slf4j
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    /** FCM 멀티캐스트 상한 */
    private static final int BATCH_SIZE = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final boolean dryRun;

    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        PushResult total = PushResult.none();
        for (int from = 0; from < tokens.size(); from += BATCH_SIZE) {
            List<String> batch = tokens.subList(from, Math.min(from + BATCH_SIZE, tokens.size()));
            total = total.plus(sendBatch(batch, message));
        }
        return total;
    }

    private PushResult sendBatch(List<String> tokens, PushMessage message) {
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(build(tokens, message), dryRun);
            return interpret(tokens, response);
        } catch (FirebaseMessagingException e) {
            // 묶음 전체가 거절된 경우 — 자격 증명 만료나 FCM 장애다. 토큰 문제가 아니므로 아무것도 지우지 않는다.
            log.error("FCM 발송 실패 - 대상 {}건, errorCode={}", tokens.size(), e.getMessagingErrorCode(), e);
            return new PushResult(0, tokens.size(), List.of());
        }
    }

    /** 성공/실패를 세고, 되살아날 수 없는 토큰만 골라낸다 */
    private PushResult interpret(List<String> tokens, BatchResponse response) {
        List<String> invalidTokens = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse each = responses.get(i);
            if (each.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException exception = each.getException();
            MessagingErrorCode errorCode = exception == null ? null : exception.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidTokens.add(tokens.get(i));
            } else {
                log.warn("FCM 개별 발송 실패(토큰 유지) - errorCode={}", errorCode);
            }
        }
        return new PushResult(response.getSuccessCount(), response.getFailureCount(), invalidTokens);
    }

    /**
     * 알림 본체는 {@code notification}에, 이동 대상은 {@code data}에 싣는다.
     *
     * <p>iOS는 {@code contentAvailable} 없이도 알림이 뜨지만 data가 앱에 전달되려면 필요하고,
     * Android는 앱이 죽어 있을 때 시스템이 직접 알림을 그리므로 채널 이름을 지정해야
     * Android 8 이상에서 소리가 난다.
     *
     * <p>{@code addAllTokens}는 9.10.0에서 {@code addAllFids}로 대체 예고됐지만 <b>바꾸지 않는다.</b>
     * 우리가 앱에서 받는 값은 {@code FirebaseMessaging.getToken()}이 주는 등록 토큰이지 FID가 아니다.
     * FID 자리에 등록 토큰을 넣으면 조용히 아무 데도 가지 않는다. 앱이 FID를 올려보내도록 바뀌는
     * 시점에 이 메서드와 {@code device_token.token}을 함께 옮긴다.
     */
    @SuppressWarnings("deprecation") // addAllTokens — 아래 주석 참고
    private MulticastMessage build(List<String> tokens, PushMessage message) {
        Notification.Builder notification = Notification.builder()
                .setTitle(message.title())
                .setBody(message.body());
        if (message.imageUrl() != null && !message.imageUrl().isBlank()) {
            notification.setImage(message.imageUrl());
        }

        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification.build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(ANDROID_CHANNEL_ID)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setContentAvailable(true)
                                .setSound("default")
                                .build())
                        .build())
                .build();
    }

    /** 앱이 미리 만들어 둔 알림 채널 id와 같아야 한다 — 다르면 Android 8+에서 조용히 뜬다 */
    private static final String ANDROID_CHANNEL_ID = "showroomz_default";
}
