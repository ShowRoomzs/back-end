package showroomz.api.seller.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * §13-6 — 쇼룸명 검색으로 찾은 creatorId, 또는 연결코드로 확인한 connectionCode 중
 * 정확히 하나만 채워서 보낸다(둘 다 없거나 둘 다 있으면 400).
 */
@Getter
@Setter
@NoArgsConstructor
public class ConnectRequest {

    @Schema(description = "쇼룸명 검색 결과에서 선택한 인플루언서 ID")
    private Long creatorId;

    @Schema(description = "연결코드 확인을 거쳐 얻은 연결코드")
    private String connectionCode;
}
