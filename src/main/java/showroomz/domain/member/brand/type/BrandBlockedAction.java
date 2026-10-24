package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정지 중 막히는 행위(§51-1 「새로 벌이는 일만 차단」 · ui-partner-15 rev.2 확정) — 가드가 409 메시지에 실어 무엇이 막혔는지 알린다.
 *
 * <p>공구 오픈은 여기 없다 — 시스템 사건이라 409 가 아니라 <b>보류</b>다(스케줄러가 건너뛴다 · 1017 설계서 8-3).
 * 「공구 개설 신청」도 없다 — 공구는 체결에서 자동 생성된다.
 */
@Getter
@RequiredArgsConstructor
public enum BrandBlockedAction {
    PRODUCT_CREATE("상품을 등록할"),
    /** 미진열 → 진열 복귀(S2c) — 사실상 새 상품을 내놓는 일이다. 진열 → 미진열 · 품절 · 재고 · 일반 수정은 허용. */
    PRODUCT_REDISPLAY("상품을 다시 진열할"),
    CONTRACT_CREATE("새 계약을 작성할"),
    /** 초안 수정 · 임시저장(S4a) — 초안은 열람 · 삭제만. */
    CONTRACT_UPDATE("계약을 작성 · 수정할"),
    CONTRACT_REVIEW_REQUEST("계약 검토를 요청할"),
    /** 서명 안내 재발송 요청(S4b) — 체결로 가는 길이다. */
    CONTRACT_RESEND_REQUEST("서명 안내 재발송을 요청할"),
    CONNECTION_REQUEST("인플루언서에게 연결을 요청할"),
    /** 기간 연장 요청(S3a) — 판매 기간을 늘리는 일이다. 조기 마감 · 중단 요청은 허용. */
    GROUP_BUY_EXTENSION_REQUEST("기간 연장을 요청할"),
    /** 탈퇴 신청 심사 중에만 막힌다(51 설계서 7-2 · 시안 4-I) — 정지 가드는 쓰지 않는다. */
    PASSWORD_CHANGE("비밀번호를 변경할"),
    /** 탈퇴 신청 심사 중에만 막힌다(51 설계서 7-2 · 시안 4-I) — 결과 메일을 받을 주소가 바뀌지 않게 한다. */
    LOGIN_EMAIL_CHANGE("로그인 이메일을 변경할");

    private final String phrase;

    public String message() {
        return "계정 이용 정지 중에는 " + phrase + " 수 없습니다. 운영팀 소통 스레드로 문의해 주세요.";
    }

    /** 탈퇴 신청 심사 중 막힘 문구 — 인플루언서 쪽 「탈퇴 신청 심사 중에는 …」과 같은 틀(55 계획서 D8). */
    public String withdrawalPendingMessage() {
        return "탈퇴 신청 심사 중에는 " + phrase + " 수 없습니다. 신청을 취소하면 바로 이용할 수 있습니다.";
    }
}
