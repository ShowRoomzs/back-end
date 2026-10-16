package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 처리 이력 주체 — 「브랜드 본인」 표기. */
@Getter
@RequiredArgsConstructor
public enum BrandAdminActorType {
    ADMIN("운영자"),
    BRAND("브랜드 본인"),
    SYSTEM("시스템");

    private final String label;
}
