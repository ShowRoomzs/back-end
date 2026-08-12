package showroomz.api.creator.profile.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.creator.profile.dto.MyShowroomResponse;
import showroomz.api.creator.profile.dto.ShowroomNameResponse;
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
class CreatorProfileServiceTest {

    private static final long USER_ID = 42L;

    @Mock
    private CreatorRepository creatorRepository;

    @InjectMocks
    private CreatorProfileService creatorProfileService;

    private final Creator me = Creator.builder()
            .id(5L)
            .showroomName("감성 룩북")
            .snsType(SnsType.INSTAGRAM)
            .channelUrl("https://instagram.com/example")
            .accountId("my_channel")
            .followerCount(12000)
            .businessEmail("creator@example.com")
            .bankName("국민은행")
            .accountNumber("110123456789")
            .connectionCode("AB3K7M9X")
            .build();

    @Test
    @DisplayName("내 쇼룸 정보를 조회하면 계좌번호는 뒤 6자리만 노출된다")
    void getMyShowroomMasksAccountNumber() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));

        MyShowroomResponse response = creatorProfileService.getMyShowroom(USER_ID);

        assertThat(response.getCreatorId()).isEqualTo(5L);
        assertThat(response.getShowroomName()).isEqualTo("감성 룩북");
        assertThat(response.getMaskedAccountNumber()).isEqualTo("******456789");
    }

    @Test
    @DisplayName("내 쇼룸명만 조회할 수 있다")
    void getMyShowroomNameReturnsNameOnly() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));

        ShowroomNameResponse response = creatorProfileService.getMyShowroomName(USER_ID);

        assertThat(response.getShowroomName()).isEqualTo("감성 룩북");
    }

    @Test
    @DisplayName("크리에이터가 아니면 조회할 수 없다")
    void unknownCreatorIsNotFound() {
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> creatorProfileService.getMyShowroom(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CREATOR_NOT_FOUND);
    }
}
