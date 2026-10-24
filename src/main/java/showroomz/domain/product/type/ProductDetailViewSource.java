package showroomz.domain.product.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상품 상세에 어디서 들어왔나(49 스튜디오 성과 설계서 1-4) — 앱이 진입 화면을 그대로 보낸다. 모르는 값 · 빈 값은 {@link #OTHER}.
 *
 * <p>성과 집계는 이 값이 아니라 {@code attributed_post_id}(라스트 터치)로 「게시물 → 상품 상세 이동」을 센다. 소스는 진입 경로 분석과
 * 앱 연동 검증(QA)용이다 — 둘을 섞으면 같은 이동이 두 번 세어진다.
 */
@Getter
@RequiredArgsConstructor
public enum ProductDetailViewSource {
    SHOWROOM("쇼룸"),
    POST("게시물"),
    DIRECT_LINK("직접 링크"),
    OTHER("기타");

    private final String label;

    public static ProductDetailViewSource from(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (ProductDetailViewSource source : values()) {
            if (source.name().equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        return OTHER;
    }
}
