package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소비자 정지 해제 사유 3종(§25-4) — <b>필수</b> · 상세 사유란 없음(정지 사유와 다른 축이고 무엇을 확인했는지는 코드로 충분하다).
 *
 * <p>브랜드 · 인플루언서가 함께 쓰는 {@code SanctionReleaseReason}에 값을 더하지 않고 따로 둔다 — 그쪽 해제 모달 옵션(reason-options)이
 * 바뀌고, 문구도 §25-4(「소명 확인 완료」 · 「오처리 정정」)와 다르다. 정지 기간이 없으므로 {@link #TERM_ENDED}는 운영자가 고르는 라벨일
 * 뿐 — 자동 해제는 없다(51-6 설계서 15절 #1).
 */
@Getter
@RequiredArgsConstructor
public enum UserReleaseReason {
    RESOLVED("소명 확인 완료"),
    MISTAKE("오처리 정정"),
    TERM_ENDED("제재 기간 종료");

    private final String label;
}
