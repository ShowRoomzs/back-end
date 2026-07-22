package showroomz.api.creator.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.service.AuthService;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAuthService {

    private final CreatorRepository creatorRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public TokenResponse completeRegistration(Long userId, CreatorCompleteRegistrationRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRoleType() != RoleType.CREATOR) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        Creator creator = creatorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        if (!Boolean.TRUE.equals(creator.getIsNewMember())) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        validateBusinessFields(request);
        validateShowroomNameAvailable(request.getShowroomName());

        boolean isBusiness = request.getBusinessType() == CreatorBusinessType.BUSINESS;
        creator.completeRegistration(
                request.getShowroomName(),
                request.getBusinessType(),
                isBusiness ? request.getBusinessRegistrationNumber() : null,
                isBusiness ? request.getBusinessLicenseImageUrl() : null,
                request.getBankName(),
                request.getAccountNumber(),
                request.getBankBookImageUrl()
        );

        return authService.generateTokens(
                user.getUsername(),
                user.getRoleType(),
                user.getId(),
                false
        );
    }

    private void validateBusinessFields(CreatorCompleteRegistrationRequest request) {
        if (request.getBusinessType() != CreatorBusinessType.BUSINESS) {
            return;
        }

        if (request.getBusinessRegistrationNumber() == null || request.getBusinessRegistrationNumber().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.getBusinessLicenseImageUrl() == null || request.getBusinessLicenseImageUrl().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateShowroomNameAvailable(String showroomName) {
        if (creatorRepository.existsByShowroomName(showroomName)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SHOWROOM_NAME);
        }
    }
}
