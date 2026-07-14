package showroomz.domain.communication.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ThreadWriterType {
    BRAND("브랜드"),
    INFLUENCER("인플루언서");

    private final String description;
}
