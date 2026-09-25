package showroomz.domain.groupbuy.service;

/**
 * 대가관계 표시(표시광고법) — <b>저장하지 않고 렌더링 시점에 타입으로 붙인다</b>(31 설계 2-8).
 *
 * <p>본문에 박으면 인플루언서가 승인 후 수정에서 지울 수 있다(§31-2 「사람이 지울 수 있으면 안 되는 문구」).
 * 스튜디오 미리보기(C3·C4)와 소비자 앱 렌더링이 <b>이 메서드 하나</b>를 쓴다 — 법정 문구가 두 벌이면 한쪽이 반드시 어긋난다.
 *
 * <p>문형은 법무 확인 전이다(31 설계 10-2 #7). 확정되면 이 클래스만 고친다.
 */
public final class GroupBuyDisclosure {

    private static final char HANGUL_FIRST = '가';
    private static final char HANGUL_LAST = '힣';
    /** 종성 ㄹ — 「로부터」를 쓴다(「서울로부터」). */
    private static final int JONG_RIEUL = 8;

    private GroupBuyDisclosure() {
    }

    /** 「유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다」 */
    public static String text(String brandName) {
        String name = brandName == null ? "" : brandName.strip();
        return "유료 광고 포함 · %s%s 대가를 받아 진행하는 공동구매입니다".formatted(name, fromParticle(name));
    }

    /**
     * 「로부터 / 으로부터」 — 받침 없음·ㄹ받침이면 「로부터」. 이름이 한글로 끝나지 않으면(영문·숫자) 읽는 법을
     * 알 수 없어 「(으)로부터」로 둔다 — 틀린 조사보다 낫다.
     */
    static String fromParticle(String name) {
        if (name.isEmpty()) {
            return "(으)로부터";
        }
        char last = name.charAt(name.length() - 1);
        if (last < HANGUL_FIRST || last > HANGUL_LAST) {
            return "(으)로부터";
        }
        int jong = (last - HANGUL_FIRST) % 28;
        return jong == 0 || jong == JONG_RIEUL ? "로부터" : "으로부터";
    }
}
