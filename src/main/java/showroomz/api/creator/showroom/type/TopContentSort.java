package showroomz.api.creator.showroom.type;

/**
 * §22-4 인기 콘텐츠 정렬 — 최신순은 넣지 않는다.
 * 순위표의 목적이 성과 비교라 시간순 나열은 순위로서 의미가 없다.
 */
public enum TopContentSort {

    LIKES("좋아요 많은 순"),
    VIEWS("노출 많은 순");

    public static final TopContentSort DEFAULT = LIKES;

    private final String label;

    TopContentSort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
