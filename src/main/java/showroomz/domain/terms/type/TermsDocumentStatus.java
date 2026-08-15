package showroomz.domain.terms.type;

import lombok.Getter;

/**
 * 문서 목록 행의 상태 (기획 §21-1) — 저장하지 않고 표시 버전과 문서의 대체 여부로 계산한다.
 *
 * <p>버전 상태를 그대로 쓰지 않는 이유는 층이 다르기 때문이다 — 목록의 "구버전"은 후속
 * <b>문서</b>로 대체된 문서를 가리키며, 같은 문서 안에서 교체된 지난 버전은 "과거 버전"이라 부른다.
 */
@Getter
public enum TermsDocumentStatus {

    EFFECTIVE("시행중"),
    SCHEDULED("시행 예정"),
    SUPERSEDED("구버전");

    private final String displayName;

    TermsDocumentStatus(String displayName) {
        this.displayName = displayName;
    }
}
