package showroomz.domain.member.creator.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CreatorBusinessType {
    INDIVIDUAL("개인 (비사업자)"),
    /** 개인사업자만 — 쇼룸 스튜디오는 법인을 받지 않는다(1009 기획 · 스튜디오 03 rev.3). */
    BUSINESS("개인사업자");

    private final String description;
}
