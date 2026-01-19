package showroomz.domain.history.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.history.type.LoginStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "login_history")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    private String clientIp;
    private String userAgent; // 디바이스/브라우저 정보

    private String country;
    private String city;

    @Enumerated(EnumType.STRING)
    private LoginStatus status;

    private LocalDateTime loginAt;

    @Builder
    public LoginHistory(Users user, String clientIp, String userAgent, String country, String city, LoginStatus status) {
        this.user = user;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.country = country;
        this.city = city;
        this.status = status;
        this.loginAt = LocalDateTime.now();
    }
}
