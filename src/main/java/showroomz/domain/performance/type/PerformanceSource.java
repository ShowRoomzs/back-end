package showroomz.domain.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.showroom.type.ShowroomVisitSource;

/**
 * 성과 유입 경로 4종(§47-2 · 49 스튜디오 설계서 0-7 · 47 시리즈 공통 결정 #4) — 쇼룸 안에서 관측되는 <b>도착 지점</b>이다.
 *
 * <p>쇼룸 현황(§22-4)이 쓰는 {@link ShowroomVisitSource}를 사상한다 — enum 을 갈아엎지 않는다. 「인스타그램」이라는 이름은 성과 화면에
 * 나가지 않는다(인스타그램 지표 금지). 소스 없는 방문은 앱 안에서 탐색해 들어온 것으로 센다 — 바깥 링크는 전부 {@code from}을 단다.
 * 한 사람은 <b>첫 방문의 소스</b> 하나로만 센다 — 그래야 4종 합 = 쇼룸 방문(명)이다.
 */
@Getter
@RequiredArgsConstructor
public enum PerformanceSource {

    INTERNAL("쇼룸 내부 탐색"),
    PROFILE_LINK("프로필 쇼룸 링크"),
    POST_LINK("공구 게시물 링크"),
    PRODUCT_LINK("상품 상세 직접 링크");

    private final String label;

    public static PerformanceSource of(ShowroomVisitSource source) {
        if (source == null) {
            return INTERNAL;
        }
        return switch (source) {
            case INSTAGRAM_LINK -> PROFILE_LINK;
            case GROUP_BUY_POST -> POST_LINK;
            case PRODUCT_DIRECT_LINK -> PRODUCT_LINK;
            case APP_SEARCH, DIRECT -> INTERNAL;
        };
    }
}
