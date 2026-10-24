package showroomz.api.admin.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.type.DeviceType;

/**
 * 회원 상세 「최근 로그인 · 기기」(53 설계서 3-1 · ui-admin-13 계정 정보) — 인플루언서 · 소비자 상세가 함께 쓴다(55 계획서 M3-2). 시각은
 * {@code users.last_login_at}이고, 기기는 가장 최근 로그인 이력의 User-Agent 로 판별한다. 앱 / 스튜디오 서피스는 로그인 이력에 없어 구분하지
 * 않는다.
 *
 * <p>판별 규칙은 로그인 이력 화면(기획 제외 패키지)과 같다 — 그 패키지를 참조하지 않으려고 여기에 둔다.
 */
@Component
@Transactional(readOnly = true)
public class AdminLoginDeviceReader {

    @PersistenceContext
    private EntityManager em;

    /** 가장 최근 로그인의 기기 — 이력이 없으면 null(「—」). */
    public DeviceType lastDevice(Long userId) {
        return em.createQuery("SELECT l FROM LoginHistory l WHERE l.user.id = :userId ORDER BY l.loginAt DESC, l.id DESC",
                        LoginHistory.class)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(login -> parse(login.getUserAgent()))
                .orElse(null);
    }

    /** User-Agent → 기기. Edge 는 Chrome 문자열을 함께 담아 먼저 본다. */
    public static DeviceType parse(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return DeviceType.UNKNOWN;
        }
        if (userAgent.contains("Android")) {
            return DeviceType.ANDROID;
        }
        if (userAgent.contains("iPhone")) {
            return DeviceType.IPHONE;
        }
        if (userAgent.contains("Edg")) {
            return DeviceType.DESKTOP_EDGE;
        }
        if (userAgent.contains("Chrome") && !userAgent.contains("Mobile")) {
            return DeviceType.DESKTOP_CHROME;
        }
        return DeviceType.UNKNOWN;
    }
}
