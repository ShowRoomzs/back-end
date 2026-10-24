package showroomz.api.admin.common;

import org.springframework.stereotype.Component;

/**
 * 운영자 등급 판정의 단일 지점(53 설계서 0-12 · 2-1). 기획은 개인정보 마스킹 해제를 <b>최고관리자 한정</b>으로 두었지만(§53-2 · §55 C-8)
 * 코드에는 등급 모델이 없다({@code RoleType.ADMIN} 하나). 지금은 ADMIN 이면 통과한다 — 등급이 생기면 이 클래스만 고친다.
 * 호출부에서 역할을 직접 비교하지 않는다(두 벌이 되면 한쪽만 고쳐진다).
 */
@Component
public class AdminPrivilege {

    /** 본인확인 정보 마스킹 해제 — TODO 최고관리자 등급 확정 시 등급 검사로 교체(§55 C-8). */
    public boolean canUnmaskPrivacy(Long operatorId) {
        return operatorId != null;
    }
}
