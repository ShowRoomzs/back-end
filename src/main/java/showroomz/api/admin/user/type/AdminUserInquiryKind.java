package showroomz.api.admin.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 소비자 상세 「최근 문의」의 구분 열(ui-admin-13 CS 응대 보강 ①) — 행 클릭 시 이동할 관리 화면도 이것으로 고른다. */
@Getter
@RequiredArgsConstructor
public enum AdminUserInquiryKind {
    ONE_TO_ONE("1:1"),
    PRODUCT("상품");

    private final String label;
}
