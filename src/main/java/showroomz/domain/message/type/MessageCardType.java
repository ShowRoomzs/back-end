package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 시스템 카드 종류(36 설계 4절). 제목은 카드 머리줄이자 목록 미리보기 · 구 FE의 대체 텍스트다. */
@Getter
@AllArgsConstructor
public enum MessageCardType {
    /** 계약 관리의 [서명 안내 다시 받기] — 요청자의 운영팀 채널에 자동 등록된다. */
    CONTRACT_RESEND_REQUEST("요청 · 서명 안내 다시 받기"),
    /** 계약 관리의 운영자 [계약 취소] 결과 — 요청이 들어온 채널에 자동 등록된다. */
    CONTRACT_ADMIN_CANCELED("계약 직권 취소 처리됨");

    private final String title;
}
