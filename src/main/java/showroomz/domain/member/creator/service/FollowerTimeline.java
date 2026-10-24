package showroomz.domain.member.creator.service;

import showroomz.domain.member.creator.entity.CreatorFollowEvent;
import showroomz.domain.member.creator.type.FollowEventType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 쇼룸의 팔로우 이력을 메모리에 펼친 판정기(49 스튜디오 성과 설계서 0-8 · 1-6).
 *
 * <p>판정식 하나 — <b>t 이전(포함) 마지막 이벤트가 FOLLOW</b> 면 그 시각의 팔로워다. 비로그인(사용자 없음)은 비팔로워다.
 * 방문 · 주문마다 쿼리를 날리지 않으려고 공구 하나의 스냅샷에서 한 번만 읽어 쓴다.
 */
public final class FollowerTimeline {

    private final Map<Long, List<CreatorFollowEvent>> byUser = new HashMap<>();
    private final List<CreatorFollowEvent> all;

    public FollowerTimeline(List<CreatorFollowEvent> events) {
        this.all = List.copyOf(events);
        for (CreatorFollowEvent event : events) {
            byUser.computeIfAbsent(event.getUserId(), k -> new ArrayList<>()).add(event);
        }
    }

    public boolean isFollowerAt(Long userId, LocalDateTime at) {
        if (userId == null || at == null) {
            return false;
        }
        FollowEventType last = null;
        for (CreatorFollowEvent event : byUser.getOrDefault(userId, List.of())) {
            if (event.getOccurredAt().isAfter(at)) {
                break;
            }
            last = event.getEventType();
        }
        return last == FollowEventType.FOLLOW;
    }

    /** 시각 t 의 팔로워 총량. */
    public int countAt(LocalDateTime at) {
        int count = 0;
        for (Long userId : byUser.keySet()) {
            if (isFollowerAt(userId, at)) {
                count++;
            }
        }
        return count;
    }

    /** 구간 [from, to) 증감 = FOLLOW − UNFOLLOW. 언팔로우 수 자체는 내보내지 않는다. */
    public int delta(LocalDateTime from, LocalDateTime to) {
        int delta = 0;
        for (CreatorFollowEvent event : all) {
            LocalDateTime t = event.getOccurredAt();
            if (t.isBefore(from) || !t.isBefore(to)) {
                continue;
            }
            delta += event.getEventType() == FollowEventType.FOLLOW ? 1 : -1;
        }
        return delta;
    }
}
