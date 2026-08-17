package showroomz.api.creator.showroom.docs;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileUpdateRequest;
import showroomz.api.creator.showroom.dto.ShowroomStatsResponse;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.api.creator.showroom.type.TopContentSort;

@Hidden
@Tag(name = "Creator - Showroom", description = "쇼룸 스튜디오 쇼룸 관리(#8) API — 공개 프로필 · 쇼룸 현황")
@SecurityRequirement(name = "Authorization")
public interface CreatorShowroomControllerDocs {

    @Operation(
            summary = "쇼룸 프로필 조회 (§22-1)",
            description = "쇼룸 관리 화면의 공개 정보를 조회합니다.\n\n" +
                    "담기는 값은 **소비자에게 공개되는 것만**입니다 — 쇼룸명 · 프로필 이미지 · 쇼룸 주소 · " +
                    "소개글 · 인스타그램 URL · 연결코드.\n" +
                    "계정 · 사업자 정보 · 정산 계좌 · 활동 채널은 비공개라 기본정보 관리(#9) 소관입니다.\n\n" +
                    "- `showroomAddress`는 가입 시 쇼룸명 기준으로 자동 생성된 뒤 **쇼룸명을 바꿔도 따라 바뀌지 않습니다.**\n" +
                    "- `showroomUrl`은 복사 버튼이 그대로 쓰는 전체 주소입니다.\n" +
                    "- 프로필 이미지는 소비자 앱 계정의 프로필과 **공유하지 않는 별개 값**입니다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ShowroomProfileResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 예시",
                                    value = "{\n" +
                                            "  \"creatorId\": 5,\n" +
                                            "  \"showroomName\": \"뷰티 소연\",\n" +
                                            "  \"profileImageUrl\": \"https://cdn.showroomz.co.kr/uploads/showroom_profile/abc.jpg\",\n" +
                                            "  \"showroomAddress\": \"beauty_soyeon\",\n" +
                                            "  \"showroomUrl\": \"https://www.showroomz.co.kr/@beauty_soyeon\",\n" +
                                            "  \"introduction\": \"뷰티 소품을 좋아하는 소연입니다\",\n" +
                                            "  \"instagramUrl\": \"https://www.instagram.com/beauty_soyeon\",\n" +
                                            "  \"connectionCode\": \"SRZ4K7M9XQ\",\n" +
                                            "  \"connectionCodeIssuedAt\": \"2026-07-01T10:00:00\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 크리에이터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "크리에이터 없음",
                                    value = "{\"code\": \"CREATOR_NOT_FOUND\", \"message\": \"존재하지 않는 크리에이터입니다.\"}"
                            )
                    )
            )
    })
    ResponseEntity<ShowroomProfileResponse> getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal);

    @Operation(
            summary = "쇼룸 프로필 저장 (§22-1)",
            description = "쇼룸 관리 화면의 공개 정보를 저장합니다. 화면의 값 전체를 그대로 보냅니다.\n\n" +
                    "| 필드 | 필수 | 규칙 |\n" +
                    "| --- | --- | --- |\n" +
                    "| `showroomName` | 필수 | 2~20자 · 한글·영문·숫자·공백만 · 중복 불가 |\n" +
                    "| `profileImageUrl` | 선택 | 업로드 API가 돌려준 URL. **삭제는 빈 문자열** |\n" +
                    "| `introduction` | 선택 | 최대 50자 |\n" +
                    "| `instagramUrl` | 선택 | `https://` 형식 |\n\n" +
                    "**쇼룸 주소는 요청에 없습니다** — 자동 생성 후 수정 불가이며, 쇼룸명을 바꿔도 따라 바뀌지 않습니다.\n\n" +
                    "여기서 바꾼 쇼룸명·프로필 이미지는 **소비자 앱 계정의 닉네임·프로필 이미지에 전파되지 않습니다.**\n\n" +
                    "이미지는 `POST /v1/creator/images` 에 `type=SHOWROOM_PROFILE` 로 먼저 업로드합니다" +
                    "(최소 160×160 · 정비율 · 최대 20MB · JPG·PNG·GIF).\n\n" +
                    "**FE 참고(§22-2)** — 필수 미입력은 에러 문구 없이 저장 버튼 비활성으로만 처리하고, " +
                    "쇼룸명 중복 · URL 형식 오류는 필드 아래 문구 1줄로 표시합니다. 두 오류는 동시에 뜰 수 있으므로 " +
                    "중복 여부는 저장 전에 `GET /v1/creator/showroom/profile/check-name` 으로 확인하세요 " +
                    "(이 API는 한 번에 하나의 오류 코드만 돌려줍니다).\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "저장 성공 — 저장된 프로필을 그대로 돌려줍니다",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ShowroomProfileResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "쇼룸명 형식 오류",
                                            value = "{\"code\": \"INVALID_SHOWROOM_NAME_FORMAT\", \"message\": \"쇼룸명은 2~20자, 한글·영문·숫자·공백만 사용할 수 있습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "쇼룸명 중복",
                                            value = "{\"code\": \"DUPLICATE_SHOWROOM_NAME\", \"message\": \"이미 사용 중인 쇼룸명입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "URL 형식 오류",
                                            value = "{\"code\": \"INVALID_INSTAGRAM_URL\", \"message\": \"https://로 시작하는 올바른 URL을 입력해 주세요.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "쇼룸명 미입력",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"must not be blank\"}",
                                            description = "@NotBlank 검증 실패 — showroomName을 비우거나 생략하면 서비스 로직 이전에 Bean Validation에서 막힙니다"
                                    ),
                                    @ExampleObject(
                                            name = "소개글 초과",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"size must be between 0 and 50\"}",
                                            description = "@Size(max=50) 검증 실패 — introduction이 50자를 넘으면 서비스 로직(SHOWROOM_INTRODUCTION_TOO_LONG) 이전에 Bean Validation에서 막힙니다"
                                    ),
                                    @ExampleObject(
                                            name = "인스타그램 URL 길이 초과",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"size must be between 0 and 512\"}",
                                            description = "@Size(max=512) 검증 실패 — 소개글 50자 제한(SHOWROOM_INTRODUCTION_TOO_LONG)과 달리 서비스 코드에 도달하기 전에 막힙니다"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 크리에이터",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<ShowroomProfileResponse> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ShowroomProfileUpdateRequest request);

    @Operation(
            summary = "쇼룸명 중복 확인 (§22-2)",
            description = "저장 전에 쇼룸명을 확인합니다. **자기 자신의 현재 쇼룸명은 중복으로 보지 않습니다.**\n\n" +
                    "`code`는 `AVAILABLE` · `DUPLICATE` · `INVALID_FORMAT` 중 하나입니다.\n\n" +
                    "대소문자 · 공백 · 유사문자를 어떻게 볼지는 아직 확정되지 않아, 현재는 **입력값을 다듬은 뒤 완전 일치**로만 판정합니다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "확인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ShowroomNameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용 가능",
                                            value = "{\"isAvailable\": true, \"code\": \"AVAILABLE\", \"message\": \"사용 가능한 쇼룸명입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "중복",
                                            value = "{\"isAvailable\": false, \"code\": \"DUPLICATE\", \"message\": \"이미 사용 중인 쇼룸명입니다. 다른 이름을 입력해주세요.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "형식 오류",
                                            value = "{\"isAvailable\": false, \"code\": \"INVALID_FORMAT\", \"message\": \"쇼룸명은 2~20자, 한글·영문·숫자·공백만 사용할 수 있습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 크리에이터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "크리에이터 없음",
                                    value = "{\"code\": \"CREATOR_NOT_FOUND\", \"message\": \"존재하지 않는 크리에이터입니다.\"}"
                            )
                    )
            )
    })
    ResponseEntity<ShowroomNameCheckResponse> checkShowroomName(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "확인할 쇼룸명", example = "뷰티 소연")
            @RequestParam("showroomName") String showroomName);

    @Operation(
            summary = "쇼룸 현황 조회 (§22-4)",
            description = "쇼룸이라는 공개 채널의 반응 지표입니다. 기간은 **화면 전체에 하나로** 적용됩니다(기본 30일).\n\n" +
                    "**지표 정의**\n" +
                    "- `reach.visits`(순방문) = 방문 **횟수** — 같은 소비자의 재방문은 30분 세션 기준 1회\n" +
                    "- `reach.visitors`(방문자 수) = 중복 제거한 **사람 수**\n" +
                    "- `reach.followConversionRate` = 기간 내 신규 팔로워 ÷ **방문자 수**(횟수가 아니라 사람 기준)\n" +
                    "- `follower.changeRate` = **직전 동일 기간** 대비 신규 팔로워 증감률\n\n" +
                    "**담기지 않는 것** — 개별 팔로워 목록, 언팔로우 수, 인스타그램 링크 클릭, 판매·정산 수치. " +
                    "개인 단위 정보는 어떤 카드에도 들어가지 않습니다.\n\n" +
                    "**빈 상태** — 값이 없어도 카드는 사라지지 않습니다. 수치는 `0`, 분포는 빈 배열, " +
                    "비교 불가한 비율은 `null`로 내려갑니다(FE가 `—`로 표시).\n\n" +
                    "**집계 한계(§22-5)**\n" +
                    "- 연령대·성별은 소셜 로그인 **동의자만** 집계되어 나머지는 `미확인` 항목으로 남습니다.\n" +
                    "- 지역은 별도 수집 항목이 없어 팔로워의 **배송지 시·도**로 추정하며, 배송지가 없는 팔로워는 표본에서 빠집니다" +
                    "(`region.sampleSize`로 표본 크기를 함께 내려줍니다).\n" +
                    "- 팔로워가 적으면 비율이 개인을 특정할 수 있어 `minimumSampleSize` 미만이면 " +
                    "`ratioSuppressed=true`와 함께 비율을 비웁니다(기준 인원은 잠정값).\n" +
                    "- 유입 경로는 쇼룸 링크의 소스 값(`?from=ig`)에 의존하므로, 소스가 없는 방문은 `DIRECT`로 뭉칩니다.\n" +
                    "- 인기 콘텐츠의 노출·좋아요는 게시물 **누적값**이며, 기간은 게시일에 적용됩니다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ShowroomStatsResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 예시",
                                    value = "{\n" +
                                            "  \"period\": \"DAYS_30\",\n" +
                                            "  \"periodLabel\": \"최근 30일\",\n" +
                                            "  \"from\": \"2026-07-16T09:00:00\",\n" +
                                            "  \"to\": \"2026-08-15T09:00:00\",\n" +
                                            "  \"follower\": {\"total\": 1240, \"newFollowers\": 42, \"changeRate\": 3.5},\n" +
                                            "  \"reach\": {\"visits\": 3180, \"visitors\": 2410, \"followConversionRate\": 1.7},\n" +
                                            "  \"composition\": {\n" +
                                            "    \"ageGroups\": [{\"label\": \"18–24세\", \"ratio\": 12.0}, {\"label\": \"25–34세\", \"ratio\": 41.0}, {\"label\": \"35–44세\", \"ratio\": 22.0}, {\"label\": \"45세 이상\", \"ratio\": 9.0}, {\"label\": \"미확인\", \"ratio\": 16.0}],\n" +
                                            "    \"genders\": [{\"label\": \"여성\", \"ratio\": 88.0}, {\"label\": \"남성\", \"ratio\": 7.0}, {\"label\": \"미확인\", \"ratio\": 5.0}],\n" +
                                            "    \"sampleSize\": 1240, \"ratioSuppressed\": false, \"minimumSampleSize\": 30\n" +
                                            "  },\n" +
                                            "  \"region\": {\n" +
                                            "    \"items\": [{\"label\": \"서울\", \"ratio\": 31.0}, {\"label\": \"기타\", \"ratio\": 23.0}],\n" +
                                            "    \"sampleSize\": 780, \"ratioSuppressed\": false, \"minimumSampleSize\": 30\n" +
                                            "  },\n" +
                                            "  \"behavior\": {\"averageVisitsPerFollower\": 2.4, \"followerRevisitRate\": 38.0, \"followerShareOfVisitors\": 61.0},\n" +
                                            "  \"topContentSort\": \"LIKES\",\n" +
                                            "  \"topContents\": [\n" +
                                            "    {\"rank\": 1, \"postId\": 301, \"thumbnailUrl\": \"https://cdn.showroomz.co.kr/uploads/post/301/thumb.jpg\", \"excerpt\": \"여름 끝 무너진 장벽, 3주 루틴\", \"publishedAt\": \"2026-08-02T11:00:00\", \"viewCount\": 1510, \"likeCount\": 31}\n" +
                                            "  ],\n" +
                                            "  \"sources\": [\n" +
                                            "    {\"source\": \"INSTAGRAM_LINK\", \"label\": \"인스타그램 링크\", \"ratio\": 62.0, \"visits\": 1972}\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 크리에이터",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<ShowroomStatsResponse> getStats(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "집계 기간 — 기본 30일", example = "DAYS_30")
            @RequestParam(value = "period", defaultValue = "DAYS_30") StatsPeriod period,
            @Parameter(description = "인기 콘텐츠 정렬 — 최신순은 제공하지 않습니다", example = "LIKES")
            @RequestParam(value = "topContentSort", defaultValue = "LIKES") TopContentSort topContentSort);
}
