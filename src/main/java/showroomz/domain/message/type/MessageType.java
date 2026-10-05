package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 말풍선과 시스템 카드(36 설계 0-3). 사람의 대화는 {@code TEXT}, 절차의 기록(요청 · 처리 결과)은 {@code SYSTEM}이다 —
 * 절차가 말풍선에 섞이면 이후 대화에 묻힌다.
 */
@Getter
@AllArgsConstructor
public enum MessageType {
    TEXT("말풍선"),
    SYSTEM("시스템 카드");

    private final String description;
}
