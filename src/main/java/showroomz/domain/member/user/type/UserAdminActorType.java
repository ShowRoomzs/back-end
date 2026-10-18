package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 처리 이력 주체 — 「소비자 본인」 표기. */
@Getter
@RequiredArgsConstructor
public enum UserAdminActorType {
    ADMIN("운영자"),
    CONSUMER("소비자 본인"),
    SYSTEM("시스템");

    private final String label;
}
