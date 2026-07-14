package showroomz.domain.notification.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationReceiverType {
    USER("소비자"),
    SELLER("브랜드"),
    CREATOR("인플루언서"),
    ADMIN("운영자");

    private final String description;
}
