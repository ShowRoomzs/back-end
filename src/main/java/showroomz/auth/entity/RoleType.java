package showroomz.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum RoleType {
    USER("ROLE_USER", "일반 사용자 권한"),
    SELLER("ROLE_SELLER", "판매자 권한"),
    SUPER_ADMIN("ROLE_SUPER_ADMIN", "슈퍼 관리자 권한"),
    GUEST("ROLE_GUEST", "게스트 권한"); // ROLE_ 접두사 추가

    private final String code;
    private final String displayName;

    public static RoleType of(String code) {
        return Arrays.stream(RoleType.values())
                .filter(r -> r.getCode().equals(code))
                .findAny()
                .orElse(GUEST);
    }
}
