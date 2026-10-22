package showroomz.api.creator.basicinfo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.creator.basicinfo.dto.CreatorBasicInfoDto;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.entity.ChangeRequestItem;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 정산 정보 탭이 사업자 구분 변경 요청에서 읽는 값(50 설계서 5-1 · 5-2) — 배너(대기 중이거나 아직 닫지 않은 결과) · 최근 승인 시각 ·
 * 승인된 등록증 메타 · 열린 요청 여부. 요청 값 중 주민등록번호는 마스킹 값뿐이다.
 */
@Component
@RequiredArgsConstructor
public class CreatorBusinessTypeChangeReader {

    private final ChangeRequestRepository repository;

    public record View(CreatorBasicInfoDto.BusinessTypeChangeBanner banner, LocalDateTime confirmedAt,
                       CreatorBasicInfoDto.DocumentFile license, boolean pending) {
    }

    public View read(Creator creator) {
        // 정산 계좌 변경 요청(1021 기-22)은 같은 표에 있지만 이 배너 · 확정일의 대상이 아니다.
        Optional<ChangeRequest> open = repository.findOpenForCreator(creator.getId())
                .filter(r -> r.getType().isBusinessTypeChange());
        List<ChangeRequest> processed = repository.findProcessedForCreator(creator.getId()).stream()
                .filter(r -> r.getType().isBusinessTypeChange()).toList();
        // 배너 = 대기 중이거나, 처리됐는데 아직 닫지 않은 최신 1건(브랜드 배너와 같은 식).
        ChangeRequest bannerRequest = Stream.concat(open.stream(),
                        processed.stream().filter(r -> r.getResultAcknowledgedAt() == null))
                .max(Comparator.comparing(ChangeRequest::getRequestedAt).thenComparing(ChangeRequest::getId))
                .orElse(null);
        boolean pending = open.isPresent();
        LocalDateTime confirmedAt = processed.stream()
                .filter(r -> r.getStatus() == ChangeRequestStatus.APPROVED)
                .findFirst().map(ChangeRequest::getProcessedAt).orElse(null);
        CreatorBasicInfoDto.DocumentFile license = creator.getBusinessType() != CreatorBusinessType.BUSINESS ? null
                : processed.stream()
                        .filter(r -> r.getStatus() == ChangeRequestStatus.APPROVED && r.getType() == ChangeRequestType.TO_BUSINESS)
                        .findFirst()
                        .map(CreatorBusinessTypeChangeReader::evidence)
                        .orElse(null);
        return new View(bannerRequest == null ? null : banner(bannerRequest), confirmedAt, license, pending);
    }

    private static CreatorBasicInfoDto.BusinessTypeChangeBanner banner(ChangeRequest request) {
        boolean rejected = request.getStatus() == ChangeRequestStatus.REJECTED;
        return CreatorBasicInfoDto.BusinessTypeChangeBanner.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType().name())
                .status(request.getStatus().name())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .businessRegistrationNumber(requested(request, ChangeRequestField.CREATOR_BUSINESS_REG_NUMBER))
                .maskedResidentNumber(requested(request, ChangeRequestField.CREATOR_RESIDENT_NUMBER))
                .evidence(evidence(request))
                .rejectReason(rejected && request.getRejectReason() != null ? rejectReason(request.getRejectReason()) : null)
                .rejectReasonDetail(rejected ? request.getRejectReasonDetail() : null)
                .accountChangeRequiredOnApproval(false)
                .build();
    }

    private static CreatorBasicInfoDto.DocumentFile evidence(ChangeRequest request) {
        return CreatorBasicInfoDto.DocumentFile.builder()
                .url(request.getEvidenceFileUrl())
                .fileName(request.getEvidenceFileName())
                .fileSize(request.getEvidenceFileSize())
                .submittedAt(request.getRequestedAt())
                .build();
    }

    /** 정형 사유 문장 그대로(§16-5 「가공 없이」). */
    private static CreatorBasicInfoDto.RejectReason rejectReason(String code) {
        String label;
        try {
            label = ChangeRequestRejectReason.valueOf(code).getDescription();
        } catch (IllegalArgumentException e) {
            label = code;
        }
        return CreatorBasicInfoDto.RejectReason.builder().code(code).label(label).build();
    }

    private static String requested(ChangeRequest request, ChangeRequestField field) {
        return request.getItems().stream()
                .filter(i -> i.getFieldKey() == field)
                .map(ChangeRequestItem::getRequestedValue)
                .findFirst().orElse(null);
    }
}
