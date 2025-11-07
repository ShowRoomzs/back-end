package showroomz.event.entitiy;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannerImage {

    @Column(length = 1024) // URL은 길 수 있으므로
    private String desktopUrl;

    @Column(length = 1024)
    private String mobileUrl;

    public BannerImage(String desktopUrl, String mobileUrl) {
        this.desktopUrl = desktopUrl;
        this.mobileUrl = mobileUrl;
    }
}