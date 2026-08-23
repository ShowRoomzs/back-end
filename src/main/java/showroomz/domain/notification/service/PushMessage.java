package showroomz.domain.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 발송할 알림 한 건의 내용 — 채널(FCM/APNs/…) 사정이 섞이지 않은 순수 값이다.
 *
 * <p>{@code data}는 앱이 알림을 눌렀을 때 어디로 갈지 판단하는 값이다. FCM의 data 페이로드는
 * <b>문자열만</b> 담을 수 있어서 {@code Map<String, String>}으로 못 박는다 — 숫자를 넣었다가
 * 발송 시점에 터지느니 여기서 문자열로 만들게 한다.
 */
public record PushMessage(String title, String body, String imageUrl, Map<String, String> data) {

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static PushMessage of(String title, String body, String imageUrl, Object... dataKeyValues) {
        Map<String, String> data = new LinkedHashMap<>();
        for (int i = 0; i + 1 < dataKeyValues.length; i += 2) {
            Object value = dataKeyValues[i + 1];
            if (value != null) {
                data.put(String.valueOf(dataKeyValues[i]), String.valueOf(value));
            }
        }
        return new PushMessage(title, body, imageUrl, data);
    }
}
