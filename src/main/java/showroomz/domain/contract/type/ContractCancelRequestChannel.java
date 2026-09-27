package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 [계약 취소]의 요청이 들어온 경로(28-1 수정계획 1-3). 브랜드 계약 취소 기능이 없어져 요청은 정해진 형식 없이
 * 스레드 메시지·전화·메일로 들어온다 — 서버가 판정할 수 없으므로 운영자가 취소 모달에서 고른다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractCancelRequestChannel {
    THREAD("소통 스레드"),
    PHONE("전화"),
    EMAIL("이메일"),
    ETC("기타");

    private final String label;
}
