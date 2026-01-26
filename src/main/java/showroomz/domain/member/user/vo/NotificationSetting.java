package showroomz.domain.member.user.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class NotificationSetting {

    @Column(nullable = false)
    private boolean smsAgree = false;

    @Column(nullable = false)
    private boolean nightPushAgree = false;

    @Column(nullable = false)
    private boolean showroomPushAgree = true;

    @Column(nullable = false)
    private boolean marketPushAgree = true;

    // 생성자, 업데이트 메서드 등
    public NotificationSetting(boolean smsAgree, boolean nightPushAgree, boolean showroomPushAgree, boolean marketPushAgree) {
        this.smsAgree = smsAgree;
        this.nightPushAgree = nightPushAgree;
        this.showroomPushAgree = showroomPushAgree;
        this.marketPushAgree = marketPushAgree;
    }
    
    // 설정 변경 비즈니스 로직
    public void update(Boolean sms, Boolean night, Boolean showroom, Boolean market) {
        if (sms != null) this.smsAgree = sms;
        if (night != null) this.nightPushAgree = night;
        if (showroom != null) this.showroomPushAgree = showroom;
        if (market != null) this.marketPushAgree = market;
    }
}
