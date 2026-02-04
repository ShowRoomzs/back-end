package showroomz.domain.bank.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "BANK")
public class Bank {

    @Id
    @Column(name = "BANK_CODE", length = 3) // 표준 코드 (예: 004, 090)
    private String code;

    @Column(name = "BANK_NAME", nullable = false, length = 50)
    private String name;

    @Column(name = "IS_ACTIVE")
    private boolean isActive; // 점검 중이거나 미사용 시 false

    @Column(name = "DISPLAY_ORDER")
    private int displayOrder; // 노출 순서 (낮을수록 상단)

    @Builder
    public Bank(String code, String name, int displayOrder) {
        this.code = code;
        this.name = name;
        this.isActive = true; // 기본값 활성화
        this.displayOrder = displayOrder;
    }

    // 운영상 필요할 때 상태 변경을 위한 메서드
    public void updateStatus(boolean isActive) {
        this.isActive = isActive;
    }
}
