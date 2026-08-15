package showroomz.domain.terms.type;

import lombok.Getter;

/**
 * 버전 상태 (기획 §21-1) — 버전 이력 층의 용어를 쓴다.
 *
 * <p>문서 목록 층의 "구버전"({@link TermsDocumentStatus#SUPERSEDED})과 색은 같은 중립이지만
 * 대상이 다르다 — 구버전은 후속 <b>문서</b>로 대체된 문서, 과거 버전은 같은 문서 안에서 교체된 지난 <b>버전</b>이다.
 * 용어를 섞지 않는다.
 */
@Getter
public enum TermsVersionStatus {

    /** 등록됐으나 시행일 전 — 소비자 화면에 노출되지 않는다 */
    SCHEDULED("시행 예정"),
    /** 현재 적용되는 버전 */
    EFFECTIVE("시행중"),
    /** 후속 버전으로 교체된 지난 버전 — 동의 기록이 참조하므로 삭제하지 않는다 */
    PAST("과거 버전");

    private final String displayName;

    TermsVersionStatus(String displayName) {
        this.displayName = displayName;
    }
}
