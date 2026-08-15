package showroomz.api.creator.showroom.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileUpdateRequest;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CreatorShowroomServiceTest {

    private static final long USER_ID = 42L;
    private static final long CREATOR_ID = 5L;
    private static final String BASE_URL = "https://www.showroomz.co.kr";

    @Mock
    private CreatorRepository creatorRepository;

    @InjectMocks
    private CreatorShowroomService creatorShowroomService;

    private Creator me;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(creatorShowroomService, "baseUrl", BASE_URL);
        me = Creator.builder()
                .id(CREATOR_ID)
                .showroomName("뷰티 소연")
                .showroomAddress("beauty_soyeon")
                .profileImageUrl("https://cdn.showroomz.co.kr/uploads/showroom_profile/old.jpg")
                .introduction("뷰티 소품을 좋아하는 소연입니다")
                .instagramUrl("https://www.instagram.com/beauty_soyeon")
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://www.instagram.com/beauty_soyeon")
                .connectionCode("SRZ4K7M9XQ")
                .build();
    }

    private ShowroomProfileUpdateRequest request(String name, String image, String intro, String instagram) {
        ShowroomProfileUpdateRequest request = new ShowroomProfileUpdateRequest();
        request.setShowroomName(name);
        request.setProfileImageUrl(image);
        request.setIntroduction(intro);
        request.setInstagramUrl(instagram);
        return request;
    }

    @Test
    @DisplayName("쇼룸 프로필 조회는 핸들과 전체 URL을 함께 내려준다")
    void getProfileBuildsShowroomUrl() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));

        ShowroomProfileResponse response = creatorShowroomService.getProfile(USER_ID);

        assertThat(response.getShowroomAddress()).isEqualTo("beauty_soyeon");
        assertThat(response.getShowroomUrl()).isEqualTo(BASE_URL + "/@beauty_soyeon");
        assertThat(response.getConnectionCode()).isEqualTo("SRZ4K7M9XQ");
    }

    @Test
    @DisplayName("쇼룸명을 바꿔도 쇼룸 주소는 따라 바뀌지 않는다")
    void updateProfileKeepsShowroomAddress() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("소연의 뷰티룸", CREATOR_ID)).willReturn(false);

        ShowroomProfileResponse response = creatorShowroomService.updateProfile(
                USER_ID, request("소연의 뷰티룸", null, null, null));

        assertThat(response.getShowroomName()).isEqualTo("소연의 뷰티룸");
        assertThat(response.getShowroomAddress()).isEqualTo("beauty_soyeon");
        assertThat(response.getShowroomUrl()).isEqualTo(BASE_URL + "/@beauty_soyeon");
    }

    @Test
    @DisplayName("프로필 이미지를 빈 값으로 보내면 삭제된다")
    void blankProfileImageClearsIt() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("뷰티 소연", CREATOR_ID)).willReturn(false);

        ShowroomProfileResponse response = creatorShowroomService.updateProfile(
                USER_ID, request("뷰티 소연", "  ", null, null));

        assertThat(response.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("이미 쓰이는 쇼룸명으로 저장하면 중복 오류가 난다")
    void duplicateShowroomNameIsRejected() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("뷰티소연", CREATOR_ID)).willReturn(true);

        assertThatThrownBy(() -> creatorShowroomService.updateProfile(
                USER_ID, request("뷰티소연", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_SHOWROOM_NAME);
    }

    @Test
    @DisplayName("특수문자가 섞인 쇼룸명은 형식 오류로 거절한다")
    void invalidShowroomNameFormatIsRejected() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));

        assertThatThrownBy(() -> creatorShowroomService.updateProfile(
                USER_ID, request("뷰티@소연", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SHOWROOM_NAME_FORMAT);
    }

    @Test
    @DisplayName("https 스킴이 없는 인스타그램 URL은 형식 오류로 거절한다")
    void instagramUrlWithoutSchemeIsRejected() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("뷰티 소연", CREATOR_ID)).willReturn(false);

        assertThatThrownBy(() -> creatorShowroomService.updateProfile(
                USER_ID, request("뷰티 소연", null, null, "instagram.com/beauty soyeon")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INSTAGRAM_URL);
    }

    @Test
    @DisplayName("소개글이 50자를 넘으면 거절한다")
    void tooLongIntroductionIsRejected() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("뷰티 소연", CREATOR_ID)).willReturn(false);

        assertThatThrownBy(() -> creatorShowroomService.updateProfile(
                USER_ID, request("뷰티 소연", null, "가".repeat(51), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHOWROOM_INTRODUCTION_TOO_LONG);
    }

    @Test
    @DisplayName("자기 자신의 현재 쇼룸명은 중복 확인에서 사용 가능으로 나온다")
    void ownNameIsAvailableOnCheck() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("뷰티 소연", CREATOR_ID)).willReturn(false);

        ShowroomNameCheckResponse response = creatorShowroomService.checkShowroomName(USER_ID, "뷰티 소연");

        assertThat(response.getIsAvailable()).isTrue();
        assertThat(response.getCode()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("중복 확인은 이미 쓰이는 이름을 DUPLICATE로 알려준다")
    void checkReportsDuplicate() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(creatorRepository.existsByShowroomNameAndIdNot("뷰티소연", CREATOR_ID)).willReturn(true);

        ShowroomNameCheckResponse response = creatorShowroomService.checkShowroomName(USER_ID, "뷰티소연");

        assertThat(response.getIsAvailable()).isFalse();
        assertThat(response.getCode()).isEqualTo("DUPLICATE");
    }
}
