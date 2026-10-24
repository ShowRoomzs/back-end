package showroomz.api.admin.changerequest.service;

import org.springframework.stereotype.Component;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.entity.ChangeRequestItem;
import showroomz.domain.changerequest.service.ChangeRequestFieldResolver;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 어드민 변경 요청 상세 — 브랜드 건(M1 사업자 정보 · M2 정산 계좌 · §16-2 C3). 대조표는 고정 항목을 항상 그리고, 요청하지 않은 항목은 현재 값을
 * 조회해 채운다({@code source = LIVE}). 서비스에 있던 브랜드 조립을 옮겼다(구현 계획서 5-4 · 정합 계획서 R3-1).
 */
@Component
public class BrandChangeRequestDetailAssembler implements ChangeRequestDetailAssembler {

    /** 대조표 고정 항목(§16-2 C3) — BUSINESS_TYPE·BUSINESS_REG_NUMBER는 요청 불가라 enum에 없지만 행은 항상 노출된다. */
    private static final List<DiffFieldSpec> BUSINESS_INFO_SPECS = List.of(
            new DiffFieldSpec("BUSINESS_TYPE", "사업자 유형", false),
            new DiffFieldSpec("MARKET_NAME", "브랜드명", false),
            new DiffFieldSpec("REPRESENTATIVE_NAME", "대표자명", false),
            new DiffFieldSpec("COMPANY_NAME", "사업자등록증 상호", false),
            new DiffFieldSpec("BUSINESS_REG_NUMBER", "사업자등록번호", true),
            new DiffFieldSpec("BUSINESS_CONDITION", "업태", false),
            new DiffFieldSpec("BUSINESS_ADDRESS", "사업장 주소", false),
            new DiffFieldSpec("MAIL_ORDER_REG_NUMBER", "통신판매업 신고번호", false));

    private static final List<DiffFieldSpec> SETTLEMENT_ACCOUNT_SPECS = List.of(
            new DiffFieldSpec("BANK_CODE", "은행", false),
            new DiffFieldSpec("ACCOUNT_NUMBER", "계좌번호", false),
            new DiffFieldSpec("ACCOUNT_HOLDER", "예금주", false));

    private record DiffFieldSpec(String fieldKey, String label, boolean locked) {
    }

    @Override
    public ChangeRequesterType requesterType() {
        return ChangeRequesterType.BRAND;
    }

    @Override
    public ChangeRequestDetailParts assemble(ChangeRequest request) {
        Market market = request.getMarket();
        Seller seller = market.getSeller();
        return new ChangeRequestDetailParts(market.getMarketName(), market.getId(), request.getRequesterName(),
                diff(request, seller, market), request.changedFieldLabels(), evidence(request),
                referenceItems(request.getType(), seller), holderCheck(request, seller), null, null);
    }

    private List<AdminChangeRequestDto.DiffRow> diff(ChangeRequest request, Seller seller, Market market) {
        List<DiffFieldSpec> specs = switch (request.getType()) {
            case BUSINESS_INFO -> BUSINESS_INFO_SPECS;
            case SETTLEMENT_ACCOUNT -> SETTLEMENT_ACCOUNT_SPECS;
            case TO_BUSINESS, TO_INDIVIDUAL, CREATOR_ACCOUNT ->
                    throw new IllegalStateException("브랜드 대조표에 인플루언서 요청: " + request.getType());
        };
        Map<String, ChangeRequestItem> itemsByKey = request.getItems().stream()
                .collect(Collectors.toMap(item -> item.getFieldKey().name(), item -> item));

        List<AdminChangeRequestDto.DiffRow> rows = new ArrayList<>();
        for (DiffFieldSpec spec : specs) {
            ChangeRequestItem item = itemsByKey.get(spec.fieldKey());
            String currentValue;
            String requestedValue = null;
            boolean changed = false;
            if (item != null) {
                currentValue = item.getCurrentValue();
                requestedValue = item.getRequestedValue();
                changed = true;
            } else {
                currentValue = liveValue(spec.fieldKey(), seller, market);
            }
            rows.add(AdminChangeRequestDto.DiffRow.builder()
                    .fieldKey(spec.fieldKey())
                    .label(spec.label())
                    .currentValue(currentValue)
                    .requestedValue(requestedValue)
                    .changed(changed)
                    .locked(spec.locked())
                    .source(changed ? "REQUEST" : "LIVE")
                    .build());
        }
        return rows;
    }

    private static String liveValue(String fieldKey, Seller seller, Market market) {
        return switch (fieldKey) {
            case "BUSINESS_TYPE" -> seller.getBusinessType();
            case "BUSINESS_REG_NUMBER" -> seller.getBusinessRegistrationNumber();
            default -> ChangeRequestFieldResolver.currentValue(ChangeRequestField.valueOf(fieldKey), seller, market);
        };
    }

    private static AdminChangeRequestDto.Evidence evidence(ChangeRequest request) {
        String documentLabel = request.getType() == ChangeRequestType.BUSINESS_INFO ? "사업자등록증" : "통장 사본";
        return AdminChangeRequestDto.Evidence.builder()
                .documentLabel(documentLabel)
                .fileName(request.getEvidenceFileName())
                .fileSizeBytes(request.getEvidenceFileSize())
                .extension(extension(request.getEvidenceFileName()))
                .fileUrl(request.getEvidenceFileUrl())
                .uploadedAt(request.getRequestedAt())
                .build();
    }

    private static List<AdminChangeRequestDto.ReferenceItem> referenceItems(ChangeRequestType type, Seller seller) {
        List<AdminChangeRequestDto.ReferenceItem> items = new ArrayList<>();
        items.add(AdminChangeRequestDto.ReferenceItem.builder()
                .label("사업자등록증 상호").value(seller.getCompanyName()).build());
        if (type == ChangeRequestType.BUSINESS_INFO) {
            items.add(AdminChangeRequestDto.ReferenceItem.builder()
                    .label("사업자등록번호").value(seller.getBusinessRegistrationNumber()).build());
        }
        return items;
    }

    private static AdminChangeRequestDto.HolderCheck holderCheck(ChangeRequest request, Seller seller) {
        if (request.getType() != ChangeRequestType.SETTLEMENT_ACCOUNT) {
            return null;
        }
        String requestedHolder = request.getItems().stream()
                .filter(item -> item.getFieldKey() == ChangeRequestField.ACCOUNT_HOLDER)
                .map(ChangeRequestItem::getRequestedValue)
                .findFirst().orElse(null);
        String companyName = seller.getCompanyName();
        return AdminChangeRequestDto.HolderCheck.builder()
                .requestedHolder(requestedHolder)
                .companyName(companyName)
                .mismatch(!Objects.equals(requestedHolder, companyName))
                .build();
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
}
