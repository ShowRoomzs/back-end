package showroomz.event.entitiy;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannerLink {

    @Enumerated(EnumType.STRING) // EnumType.ORDINAL(숫자) 대신 STRING 사용 권장
    @Column(length = 50)
    private LinkType type; // PRODUCT_LIST, EXTERNAL_URL 등

    @Column(length = 2048) // 링크 타겟 URL/경로는 매우 길 수 있음
    private String target; // "?categoryId=...", "https://..."

    public BannerLink(LinkType type, String target) {
        this.type = type;
        this.target = target;
    }
}