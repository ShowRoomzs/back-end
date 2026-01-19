package showroomz.api.admin.history.DTO;

import lombok.Getter;
import lombok.Setter;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.history.type.LoginStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class LoginHistorySearchCondition {
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    // ANDROID, IPHONE, DESKTOP_CHROME, DESKTOP_EDGE
    private DeviceType deviceType; 

    private String country;
    
    private LoginStatus status; // SUCCESS, ABNORMAL (전체는 null)
}
