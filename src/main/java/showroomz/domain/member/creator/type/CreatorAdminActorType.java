package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.AdminActorType;

/** 처리 이력 주체 — 「인플루언서 본인」 표기. */
@Getter
@RequiredArgsConstructor
public enum CreatorAdminActorType implements AdminActorType {
    ADMIN("운영자"),
    CREATOR("인플루언서 본인"),
    SYSTEM("시스템");

    private final String label;
}
