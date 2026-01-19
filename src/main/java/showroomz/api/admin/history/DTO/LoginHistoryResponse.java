package showroomz.api.admin.history.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.history.type.LoginStatus;
import showroomz.global.utils.LocationNameMapper;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class LoginHistoryResponse {

    private Long id;
    private Long userId;
    private String email; // 사용자 이메일
    
    private LocalDateTime loginAt; // 이제 자동으로 설정된 포맷으로 나갑니다.
    
    private String clientIp;
    private DeviceType deviceType; // 디바이스 타입
    private String country;
    private String city;
    private LoginStatus status;

    public LoginHistoryResponse(LoginHistory history) {
        this.id = history.getId();
        this.userId = history.getUser().getId();
        this.email = history.getUser().getEmail();
        this.loginAt = history.getLoginAt();
        this.clientIp = history.getClientIp();
        this.deviceType = parseDeviceType(history.getUserAgent());
        this.country = LocationNameMapper.toKoreanCountry(history.getCountry());
        this.city = LocationNameMapper.toKoreanCity(history.getCity());
        this.status = history.getStatus();
    }

    /**
     * User-Agent 문자열을 파싱하여 디바이스 타입을 반환합니다.
     */
    private DeviceType parseDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return DeviceType.UNKNOWN;
        }

        // Android
        if (userAgent.contains("Android")) {
            return DeviceType.ANDROID;
        }

        // iPhone
        if (userAgent.contains("iPhone")) {
            return DeviceType.IPHONE;
        }

        // Desktop (Edge) - Edg는 Edge의 약자
        if (userAgent.contains("Edg")) {
            return DeviceType.DESKTOP_EDGE;
        }

        // Desktop (Chrome) - Edge가 아니면서 Chrome이 있고 Mobile이 없는 경우
        if (userAgent.contains("Chrome") && !userAgent.contains("Mobile")) {
            return DeviceType.DESKTOP_CHROME;
        }

        return DeviceType.UNKNOWN;
    }
}
