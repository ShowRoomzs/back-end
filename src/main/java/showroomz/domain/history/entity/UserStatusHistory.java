package showroomz.domain.history.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;

@Entity
@Table(name = "user_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStatusHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_status_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private UserStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private UserStatus newStatus;

    @Column(name = "reason", length = 500)
    private String reason;


    @Builder
    public UserStatusHistory(Users user, UserStatus previousStatus, UserStatus newStatus, String reason) {
        this.user = user;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }
}
