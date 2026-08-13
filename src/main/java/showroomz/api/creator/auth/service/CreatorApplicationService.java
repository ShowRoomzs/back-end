package showroomz.api.creator.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.creator.dto.CreatorApplicationDetailResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationDetailResponse.ProcessingHistoryItem;
import showroomz.api.admin.creator.dto.CreatorApplicationListResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationSearchCondition;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.auth.DTO.CreatorApplicationRequest;
import showroomz.api.creator.auth.DTO.MyCreatorApplicationResponse;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.history.entity.CreatorApplicationHistory;
import showroomz.domain.history.repository.CreatorApplicationHistoryRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;
import showroomz.global.utils.BusinessRegistrationNumberHasher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorApplicationService {

    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final MailService mailService;
    private final CreatorApplicationHistoryRepository applicationHistoryRepository;
    private final SellerRepository sellerRepository;

    @Transactional
    public void apply(Long userId, CreatorApplicationRequest request) {
        validateRequiredAgreements(request);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRoleType() == RoleType.CREATOR) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        if (creatorApplicationRepository.existsByUser_IdAndStatus(userId, CreatorApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION);
        }

        validateReapplyCooldown(userId);

        CreatorApplication application = CreatorApplication.createApplication(
                user,
                request.getSnsType(),
                request.getChannelUrl(),
                request.getAccountId(),
                request.getFollowerCount(),
                request.getBusinessEmail(),
                // 회원가입(C0-1) 시 본인인증(PASS)으로 이미 확인된 값을 그대로 사용 — 별도로 다시 받지 않는다
                user.getName(),
                user.getBirthday(),
                user.getPhoneNumber()
        );

        user.setServiceAgree(Boolean.TRUE.equals(request.getAgreeTermsOfService()));
        user.setPrivacyAgree(Boolean.TRUE.equals(request.getAgreePrivacyPolicy()));
        user.setMarketingAgree(Boolean.TRUE.equals(request.getAgreeMarketingPolicy()));

        creatorApplicationRepository.save(application);
    }

    public MyCreatorApplicationResponse getMyRejectedApplication(Long userId) {
        CreatorApplication application = creatorApplicationRepository
                .findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, CreatorApplicationStatus.REJECTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        return new MyCreatorApplicationResponse(application, application.resolveReapplyAvailableAt());
    }

    private void validateReapplyCooldown(Long userId) {
        creatorApplicationRepository
                .findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, CreatorApplicationStatus.REJECTED)
                .ifPresent(rejected -> {
                    if (LocalDateTime.now().isBefore(rejected.resolveReapplyAvailableAt())) {
                        throw new BusinessException(ErrorCode.APPLICATION_REAPPLY_COOLDOWN);
                    }
                });
    }

    public CreatorApplicationListResponse getApplications(
            CreatorApplicationSearchCondition condition,
            PagingRequest pagingRequest) {

        String keyword = normalizeKeyword(condition.getKeyword());

        Page<CreatorApplication> applicationPage = creatorApplicationRepository.search(
                condition.getStatus(),
                keyword,
                pagingRequest.toPageable()
        );

        List<CreatorApplicationResponse> content = applicationPage.getContent().stream()
                .map(CreatorApplicationResponse::new)
                .toList();

        return new CreatorApplicationListResponse(
                content,
                applicationPage,
                buildStatusCounts(keyword)
        );
    }

    private CreatorApplicationListResponse.StatusCounts buildStatusCounts(String keyword) {
        long pending = 0L;
        long approved = 0L;
        long rejected = 0L;

        for (Object[] row : creatorApplicationRepository.countByStatus(keyword)) {
            CreatorApplicationStatus status = (CreatorApplicationStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case PENDING -> pending = count;
                case APPROVED -> approved = count;
                case REJECTED -> rejected = count;
            }
        }

        return CreatorApplicationListResponse.StatusCounts.builder()
                .all(pending + approved + rejected)
                .pending(pending)
                .approved(approved)
                .rejected(rejected)
                .build();
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    public CreatorApplicationDetailResponse getApplicationDetail(Long applicationId) {
        CreatorApplication application = creatorApplicationRepository.findByIdWithUser(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Users user = application.getUser();
        boolean rejected = application.getStatus() == CreatorApplicationStatus.REJECTED;

        return CreatorApplicationDetailResponse.builder()
                .applicationId(application.getId())
                .status(application.getStatus())
                .appliedAt(application.getCreatedAt())
                .processedAt(application.getProcessedAt())
                .processorEmail(application.getProcessorEmail())
                .rejectReasonType(application.getRejectReasonType())
                .rejectReasonDetail(application.getRejectReasonDetail())
                .name(rejected ? null : application.getRealName())
                .birthday(rejected ? null : application.getBirthday())
                .phoneNumber(rejected ? null : application.getPhoneNumber())
                .phoneNumberHash(rejected ? application.getPhoneNumber() : null)
                .verificationMethod(CreatorApplicationDetailResponse.VERIFICATION_METHOD_PASS)
                .verificationMethodLabel(CreatorApplicationDetailResponse.VERIFICATION_METHOD_PASS_LABEL)
                .snsType(application.getSnsType())
                .channelUrl(application.getChannelUrl())
                .accountId(application.getAccountId())
                .followerCount(application.getFollowerCount())
                .businessEmail(application.getBusinessEmail())
                .marketingAgree(rejected ? null : user.isMarketingAgree())
                .processingHistory(buildProcessingHistory(application))
                .build();
    }

    private List<ProcessingHistoryItem> buildProcessingHistory(CreatorApplication application) {
        List<ProcessingHistoryItem> history = new ArrayList<>();

        history.add(ProcessingHistoryItem.builder()
                .type("APPLICATION_RECEIVED")
                .label("신청 접수")
                .processedAt(application.getCreatedAt())
                .build());

        List<CreatorApplicationHistory> statusHistories =
                applicationHistoryRepository.findByApplication_IdOrderByCreatedAtAsc(application.getId());

        for (CreatorApplicationHistory statusHistory : statusHistories) {
            if (statusHistory.getNewStatus() == CreatorApplicationStatus.APPROVED) {
                history.add(ProcessingHistoryItem.builder()
                        .type("APPLICATION_APPROVED")
                        .label("승인 처리")
                        .processedAt(statusHistory.getCreatedAt())
                        .processorEmail(statusHistory.getProcessorEmail())
                        .build());
            } else if (statusHistory.getNewStatus() == CreatorApplicationStatus.REJECTED) {
                history.add(ProcessingHistoryItem.builder()
                        .type("APPLICATION_REJECTED")
                        .label("반려 처리")
                        .processedAt(statusHistory.getCreatedAt())
                        .processorEmail(statusHistory.getProcessorEmail())
                        .build());
            }
        }

        return history;
    }

    @Transactional
    public void approve(Long applicationId, Long adminUserId) {
        CreatorApplication application = creatorApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != CreatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        String processorEmail = resolveProcessorEmail(adminUserId);
        CreatorApplicationStatus previousStatus = application.getStatus();

        application.approve(processorEmail);

        Users user = application.getUser();
        user.updateRoleType(RoleType.CREATOR);

        Creator creator = Creator.builder()
                .user(user)
                .snsType(application.getSnsType())
                .channelUrl(application.getChannelUrl())
                .accountId(application.getAccountId())
                .followerCount(application.getFollowerCount())
                .businessEmail(application.getBusinessEmail())
                .realName(application.getRealName())
                .birthday(application.getBirthday())
                .phoneNumber(application.getPhoneNumber())
                .isNewMember(true)
                .build();
        creatorRepository.save(creator);

        applicationHistoryRepository.save(CreatorApplicationHistory.builder()
                .application(application)
                .previousStatus(previousStatus)
                .newStatus(CreatorApplicationStatus.APPROVED)
                .processorEmail(processorEmail)
                .build());

        mailService.sendCreatorApprovalEmail(
                application.getBusinessEmail(),
                user.getNickname(),
                application.getProcessedAt()
        );
    }

    @Transactional
    public void reject(Long applicationId, CreatorApplicationRejectRequest request, Long adminUserId) {
        CreatorApplication application = creatorApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != CreatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        String processorEmail = resolveProcessorEmail(adminUserId);
        CreatorApplicationStatus previousStatus = application.getStatus();

        String reasonSummary = request.getRejectReasonType().getDescription();
        String reasonDetail = request.getRejectReasonDetail();

        String fullRejectReason = reasonSummary;
        if (reasonDetail != null && !reasonDetail.isBlank()) {
            fullRejectReason += " - " + reasonDetail;
        }

        application.reject(
                request.getRejectReasonType().name(),
                reasonDetail,
                fullRejectReason,
                processorEmail
        );

        Users user = application.getUser();
        String phoneHash = BusinessRegistrationNumberHasher.hash(application.getPhoneNumber());
        user.purgeAgreementsOnCreatorRejection();
        application.purgePersonalData(phoneHash);

        applicationHistoryRepository.save(CreatorApplicationHistory.builder()
                .application(application)
                .previousStatus(previousStatus)
                .newStatus(CreatorApplicationStatus.REJECTED)
                .reason(fullRejectReason)
                .processorEmail(processorEmail)
                .build());
    }

    private String resolveProcessorEmail(Long adminUserId) {
        if (adminUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 관리자(SUPER/ADMIN/SELLER)는 Seller 테이블에 존재
        Seller admin = sellerRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return admin.getEmail();
    }

    private void validateRequiredAgreements(CreatorApplicationRequest request) {
        if (!Boolean.TRUE.equals(request.getAgreeTermsOfService())
                || !Boolean.TRUE.equals(request.getAgreeOperationalPolicy())
                || !Boolean.TRUE.equals(request.getAgreePrivacyPolicy())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
