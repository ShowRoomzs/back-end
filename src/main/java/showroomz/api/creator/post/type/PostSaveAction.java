package showroomz.api.creator.post.type;

/**
 * 작성 화면의 두 버튼 (§24-3).
 *
 * <p>버튼마다 <b>활성 조건이 다르다</b> — 임시저장은 "사진 1장 또는 본문 1자", 게시하기는 "사진 최소 1장".
 * 같은 저장 API에 상태 값을 실어 보내는 대신 의도를 받는 이유가 여기 있다. 어떤 조건으로 검증할지가
 * 요청에 담겨야 서버가 FE와 같은 규칙으로 막는다.
 */
public enum PostSaveAction {

    /** 임시저장 — 작성중(DRAFT)으로 남는다 */
    DRAFT,

    /** 게시하기 — 곧바로 게시중(PUBLISHED)이 된다 */
    PUBLISH
}
