package showroomz.domain.member.creator.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CreatorBusinessType {
    INDIVIDUAL("개인 (비사업자)"),
    BUSINESS("개인사업자/법인");

    private final String description;
}
