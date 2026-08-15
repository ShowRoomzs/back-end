package showroomz.domain.showroom.type;

/**
 * §22-4 유입 경로 — 쇼룸 방문이 어디서 들어왔는지.
 *
 * <p>§22-5 전제: 판정 근거는 쇼룸 링크에 붙는 소스 값(`?from=ig`)과 앱 딥링크의 소스 보존 규칙이다.
 * 규칙이 확정되기 전에는 소스 없는 방문이 전부 {@link #DIRECT}로 뭉치므로, 이 카드의 분포는
 * "직접 유입이 크다"가 아니라 "아직 소스를 못 붙였다"로 읽어야 한다.
 */
public enum ShowroomVisitSource {

    INSTAGRAM_LINK("인스타그램 링크", "ig"),
    APP_SEARCH("앱 검색", "search"),
    GROUP_BUY_POST("공구 게시물", "post"),
    DIRECT("직접 유입", "direct");

    private final String label;
    private final String linkValue;

    ShowroomVisitSource(String label, String linkValue) {
        this.label = label;
        this.linkValue = linkValue;
    }

    public String getLabel() {
        return label;
    }

    public String getLinkValue() {
        return linkValue;
    }

    /** 쇼룸 링크의 `from` 값 → 유입 경로. 값이 없거나 모르는 값이면 직접 유입으로 센다. */
    public static ShowroomVisitSource fromLinkValue(String linkValue) {
        if (linkValue == null || linkValue.isBlank()) {
            return DIRECT;
        }
        String normalized = linkValue.trim().toLowerCase();
        for (ShowroomVisitSource source : values()) {
            if (source.linkValue.equals(normalized) || source.name().toLowerCase().equals(normalized)) {
                return source;
            }
        }
        return DIRECT;
    }
}
