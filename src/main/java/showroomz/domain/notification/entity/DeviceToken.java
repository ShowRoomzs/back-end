package showroomz.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.notification.type.DevicePlatform;

import java.time.LocalDateTime;

/**
 * 푸시 발송 대상 기기 하나.
 *
 * <p><b>토큰이 유니크다</b> — (user, token) 쌍이 아니다. FCM 토큰은 "기기 + 앱 설치"를 가리키는
 * 식별자라 계정과 1:1이 아니다. 한 기기에서 로그아웃하고 다른 계정으로 로그인하면 같은 토큰이
 * 새 계정에 붙는데, 쌍으로 유니크를 걸면 두 행이 살아남아 <b>이전 사용자에게 갈 알림이 그 기기로
 * 계속 간다.</b> 토큰 유니크 + 로그인 시 소유자 재지정({@link #reassignTo})으로
 * "이 기기의 현재 주인은 한 명"을 DB가 보장하게 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "device_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_token_token", columnNames = "token"),
        indexes = @Index(name = "idx_device_token_user", columnList = "user_id")
)
public class DeviceToken {

    /** FCM 등록 토큰 길이는 규격상 고정이 아니다 — 넉넉히 잡되 인덱스 키 한도(3072B) 안에 둔다 */
    public static final int MAX_TOKEN_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "token", nullable = false, length = MAX_TOKEN_LENGTH)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 재로그인마다 갱신한다 — FCM이 오래 안 쓴 토큰을 만료시키므로 정리 기준이 된다 */
    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    public DeviceToken(Users user, String token, DevicePlatform platform, LocalDateTime now) {
        this.user = user;
        this.token = token;
        this.platform = platform == null ? DevicePlatform.UNKNOWN : platform;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    /**
     * 이미 등록된 토큰으로 다시 로그인했을 때 — 기기 주인을 지금 로그인한 사람으로 바꾼다.
     *
     * <p>같은 사람이 다시 로그인한 경우에도 그냥 통과시킨다. 사용 시각 갱신이 목적이라
     * 분기할 이유가 없다.
     */
    public void reassignTo(Users user, DevicePlatform platform, LocalDateTime now) {
        this.user = user;
        if (platform != null && platform != DevicePlatform.UNKNOWN) {
            this.platform = platform;
        }
        this.lastUsedAt = now;
    }
}
