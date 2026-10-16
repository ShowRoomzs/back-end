package showroomz.api.admin.brand.type;

import org.springframework.data.domain.Sort;

/** 브랜드 회원 목록 정렬 — 최근 가입순(기본) · 회원번호순. */
public enum AdminBrandSort {
    RECENT_JOINED,
    BRAND_NO;

    public Sort toSort() {
        return this == BRAND_NO
                ? Sort.by(Sort.Direction.ASC, "id")
                : Sort.by(Sort.Direction.DESC, "seller.createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
