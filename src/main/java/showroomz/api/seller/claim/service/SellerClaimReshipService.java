package showroomz.api.seller.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.seller.claim.dto.SellerClaimBatchResponse;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimReshipDto;
import showroomz.api.seller.order.service.SellerOrderAccessGuard;
import showroomz.api.seller.order.service.SellerOrderAccessGuard.SellerScope;
import showroomz.api.seller.order.service.ShipmentExcelParser;
import showroomz.domain.order.entity.MarketPurchaseOrderTemplate;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.PurchaseOrderDownloadLog;
import showroomz.domain.order.repository.MarketPurchaseOrderTemplateRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.PurchaseOrderDownloadLogRepository;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.ReshipResult;
import showroomz.domain.order.type.ClaimReshipColumn;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 파트너센터 재발송(35 설계서 3-4) — 송장 등록 · 수정 · 목록 엑셀 · 업로드 파싱. 주문 관리의 송장 패턴을 그대로 따른다:
 * 확정 지점은 등록 API 하나이고, 업로드는 분류만 해서 돌려준다. 교환 새 상품이든 반려 상품 반송이든 같은 길이다.
 */
@Service
@RequiredArgsConstructor
public class SellerClaimReshipService {

    private static final Pattern CLAIM_NUMBER = Pattern.compile("^CLM-?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String HEADER_CLAIM_NUMBER = ClaimReshipColumn.CLAIM_NUMBER.getHeader();

    private final SellerOrderAccessGuard accessGuard;
    private final OrderClaimService claimService;
    private final SellerClaimQueryService queryService;
    private final OrderClaimRepository claimRepository;
    private final MarketPurchaseOrderTemplateRepository templateRepository;
    private final PurchaseOrderDownloadLogRepository downloadLogRepository;
    private final ClaimReshipExcel excel;
    private final ShipmentExcelParser excelParser;
    private final OrderProperties orderProperties;

    public record ExportFile(byte[] content, String filename) {
    }

    // ------------------------------------------------------------------ 송장 등록 · 수정

    /** 등록 확정 — 다건 · 행 단위 부분 성공. 빈 값 행은 조용히 건너뛴다. 등록해도 완료가 아니라 재발송 중으로 간다. */
    public SellerClaimBatchResponse register(String sellerEmail, SellerClaimReshipDto.RegisterRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<SellerClaimBatchResponse.Skipped> skipped = new ArrayList<>();
        int succeeded = 0;
        for (SellerClaimReshipDto.Item item : request.items()) {
            if (item.trackingNumber() == null || item.trackingNumber().isBlank()) {
                continue;
            }
            ReshipResult result = claimService.registerReshipment(item.claimId(), scope.market().getId(),
                    scope.sellerId(), item.carrier(), item.trackingNumber(), now);
            if (result.success()) {
                succeeded++;
            } else {
                skipped.add(new SellerClaimBatchResponse.Skipped(item.claimId(), result.code().getCode(),
                        result.message()));
            }
        }
        return new SellerClaimBatchResponse(succeeded, skipped);
    }

    public SellerClaimDetailResponse update(String sellerEmail, Long claimId,
                                            SellerClaimReshipDto.UpdateRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        claimService.updateReshipment(claimId, scope.market().getId(), scope.sellerId(), request.carrier(),
                request.trackingNumber(), LocalDateTime.now());
        return queryService.getClaim(sellerEmail, claimId);
    }

    // ------------------------------------------------------------------ 목록 엑셀(E1)

    /**
     * 재발송 목록 — 재발송 대기만 싣는다. 수취인 개인정보가 나가는 유일한 경로라 반출 기록을 같은 트랜잭션에 남긴다.
     * 재발송 대기부터는 소비자가 수취지를 바꿀 수 없다 — 이 목록에 오른 주소는 바뀌지 않는다.
     */
    @Transactional
    public ExportFile export(String sellerEmail, SellerClaimReshipDto.ExportRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<ClaimReshipColumn> columns = new ArrayList<>(new LinkedHashSet<>(request.columns()));
        if (columns.isEmpty()) {
            throw new BusinessException(ErrorCode.CLAIM_EXPORT_COLUMNS_REQUIRED);
        }
        Long marketId = scope.market().getId();
        List<OrderClaim> claims = request.claimIds() != null && !request.claimIds().isEmpty()
                ? claimRepository.findOwnedByIds(marketId, new LinkedHashSet<>(request.claimIds())).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.RESHIP_READY).toList()
                : claimRepository.findReshipReady(marketId,
                PageRequest.of(0, orderProperties.getPurchaseOrderMaxGroups()));
        if (claims.isEmpty()) {
            throw new BusinessException(ErrorCode.CLAIM_EXPORT_EMPTY);
        }

        List<ClaimReshipExcel.Line> lines = claims.stream().map(this::toLine).toList();
        if (Boolean.TRUE.equals(request.saveAsDefault())) {
            upsertTemplate(scope, columns);
        }
        downloadLogRepository.save(PurchaseOrderDownloadLog.builder()
                .marketId(marketId)
                .sellerId(scope.sellerId())
                .kind(MarketPurchaseOrderTemplate.TYPE_CLAIM_RESHIP)
                .deliveryGroupCount(claims.size())
                .columns(csv(columns))
                .prepareStarted(false)
                .downloadedAt(now)
                .build());
        return new ExportFile(excel.write(columns, lines), "재발송목록_" + FILE_STAMP.format(now) + ".xlsx");
    }

    /**
     * 상품명·옵션은 <b>보낼 물건</b>이다 — 교환 재발송은 새 옵션, 거절 반송은 원래 옵션. 화면의 「보낼 상품·옵션」과 같은
     * 값이어야 거절 건에 새 옵션을 보내는 사고가 나지 않는다.
     */
    private ClaimReshipExcel.Line toLine(OrderClaim claim) {
        OrderClaimCollection collection = claim.getCollection();
        OrderProduct product = claim.getOrderProduct();
        OrderDeliveryGroup group = claim.getDeliveryGroup();
        boolean rejected = claim.getRejectedAt() != null;
        Map<ClaimReshipColumn, String> values = new EnumMap<>(ClaimReshipColumn.class);
        values.put(ClaimReshipColumn.CLAIM_NUMBER, claim.claimNumber());
        values.put(ClaimReshipColumn.RECIPIENT, collection.getReshipRecipient());
        values.put(ClaimReshipColumn.PHONE, collection.getReshipPhone());
        values.put(ClaimReshipColumn.ZIP_CODE, collection.getReshipZipCode());
        values.put(ClaimReshipColumn.ADDRESS, ((collection.getReshipAddress() == null ? "" : collection.getReshipAddress())
                + (collection.getReshipDetailAddress() == null ? "" : " " + collection.getReshipDetailAddress())).trim());
        values.put(ClaimReshipColumn.PRODUCT_NAME, product.getProductName());
        values.put(ClaimReshipColumn.OPTION, rejected || claim.getExchangeOptionName() == null
                ? product.getOptionName() : claim.getExchangeOptionName());
        values.put(ClaimReshipColumn.QUANTITY, String.valueOf(claim.getQuantity()));
        values.put(ClaimReshipColumn.RESHIP_REASON, rejected ? "거절 반송" : "교환 재발송");
        values.put(ClaimReshipColumn.REQUESTED_AT, DATE_TIME.format(claim.getRequestedAt()));
        values.put(ClaimReshipColumn.ORDER_NUMBER, group.getOrder().getOrderNumber());
        values.put(ClaimReshipColumn.GROUP_BUY_NAME, group.getGroupBuy() == null
                || group.getGroupBuy().getContract() == null ? null : group.getGroupBuy().getContract().getTitle());
        values.put(ClaimReshipColumn.CLAIM_TYPE, claim.getType().getLabel());
        return new ClaimReshipExcel.Line(values);
    }

    @Transactional(readOnly = true)
    public SellerClaimReshipDto.TemplateResponse getTemplate(String sellerEmail) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        List<ClaimReshipColumn> columns = templateRepository.findByMarket_IdAndTemplateType(scope.market().getId(),
                        MarketPurchaseOrderTemplate.TYPE_CLAIM_RESHIP)
                .map(template -> Arrays.stream(template.getColumns().split(","))
                        .map(String::trim).filter(code -> !code.isEmpty()).map(ClaimReshipColumn::valueOf).toList())
                .orElseGet(() -> Arrays.stream(ClaimReshipColumn.values()).filter(ClaimReshipColumn::isBasic).toList());
        return new SellerClaimReshipDto.TemplateResponse(columns, Arrays.stream(ClaimReshipColumn.values())
                .map(code -> new SellerClaimReshipDto.TemplateResponse.Available(code, code.getHeader(), code.isBasic()))
                .toList());
    }

    @Transactional
    public SellerClaimReshipDto.TemplateResponse updateTemplate(String sellerEmail,
                                                                SellerClaimReshipDto.TemplateUpdateRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        List<ClaimReshipColumn> columns = new ArrayList<>(new LinkedHashSet<>(request.columns()));
        if (columns.isEmpty()) {
            throw new BusinessException(ErrorCode.CLAIM_EXPORT_COLUMNS_REQUIRED);
        }
        upsertTemplate(scope, columns);
        return getTemplate(sellerEmail);
    }

    private void upsertTemplate(SellerScope scope, List<ClaimReshipColumn> columns) {
        templateRepository.findByMarket_IdAndTemplateType(scope.market().getId(),
                        MarketPurchaseOrderTemplate.TYPE_CLAIM_RESHIP)
                .ifPresentOrElse(
                        template -> template.updateCsv(csv(columns), scope.sellerId()),
                        () -> templateRepository.save(MarketPurchaseOrderTemplate.ofType(scope.market(),
                                MarketPurchaseOrderTemplate.TYPE_CLAIM_RESHIP, csv(columns), scope.sellerId())));
    }

    private static String csv(List<ClaimReshipColumn> columns) {
        return columns.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    // ------------------------------------------------------------------ 업로드 파싱(E2)

    /**
     * 일괄 업로드 검증 — <b>상태를 바꾸지 않는다.</b> 열은 머리글로 찾는다(「접수번호」 「택배사」 「송장번호」) — 컬럼 구성이
     * 브랜드마다 달라 위치가 고정이 아니다. 없는 번호와 남의 마켓 번호는 같은 사유로 답한다(존재를 가르지 않는다).
     */
    @Transactional(readOnly = true)
    public SellerClaimReshipDto.ParseResponse parse(String sellerEmail, MultipartFile file) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        Long marketId = scope.market().getId();
        List<List<String>> table;
        try {
            table = excelParser.readRows(file.getInputStream(), orderProperties.getShipmentUploadMaxRows());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SHIPMENT_FILE_INVALID);
        }
        if (table.isEmpty()) {
            throw new BusinessException(ErrorCode.CLAIM_UPLOAD_HEADER_MISSING);
        }
        List<String> header = table.get(0);
        int claimColumn = header.indexOf(HEADER_CLAIM_NUMBER);
        int carrierColumn = header.indexOf(ClaimReshipColumn.CARRIER_HEADER);
        int trackingColumn = header.indexOf(ClaimReshipColumn.TRACKING_HEADER);
        if (claimColumn < 0 || carrierColumn < 0 || trackingColumn < 0) {
            throw new BusinessException(ErrorCode.CLAIM_UPLOAD_HEADER_MISSING);
        }

        record Raw(int rowNumber, String claimNumber, Long claimId, String carrierText, String trackingNumber) {
        }
        List<Raw> raws = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            String claimNumber = cell(row, claimColumn);
            String carrierText = cell(row, carrierColumn);
            String trackingNumber = cell(row, trackingColumn).replaceAll("[^0-9]", "");
            if (claimNumber.isEmpty() && carrierText.isEmpty() && trackingNumber.isEmpty()) {
                continue; // 빈 행
            }
            Matcher matcher = CLAIM_NUMBER.matcher(claimNumber);
            raws.add(new Raw(i + 1, claimNumber, matcher.matches() ? parseId(matcher.group(1)) : null, carrierText,
                    trackingNumber));
        }
        Set<Long> ids = raws.stream().map(Raw::claimId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, OrderClaim> claims = ids.isEmpty() ? Map.of()
                : claimRepository.findOwnedByIds(marketId, ids).stream()
                .collect(Collectors.toMap(OrderClaim::getId, Function.identity()));

        Set<Long> seenClaims = new HashSet<>();
        Set<String> seenInvoices = new HashSet<>();
        List<SellerClaimReshipDto.ParsedRow> rows = new ArrayList<>();
        int valid = 0;
        for (Raw raw : raws) {
            OrderClaim claim = raw.claimId() == null ? null : claims.get(raw.claimId());
            DeliveryCarrier carrier = raw.carrierText().isEmpty() ? null : DeliveryCarrier.fromLabel(raw.carrierText());
            String code = null;
            String message = null;
            if (claim == null) {
                code = "CLAIM_NOT_FOUND";
                message = "접수번호를 찾을 수 없습니다.";
            } else if (!seenClaims.add(claim.getId())) {
                code = "CLAIM_DUPLICATE_IN_FILE";
                message = "파일 안에 같은 접수번호가 여러 번 있습니다. 첫 행만 사용합니다.";
            } else if (claim.getStatus() != ClaimStatus.RESHIP_READY) {
                code = "NOT_RESHIP_READY";
                message = "재발송 대기 상태가 아닙니다.";
            } else if (raw.trackingNumber().isEmpty()) {
                code = "TRACKING_REQUIRED";
                message = "송장번호를 입력해 주세요.";
            } else if (!raw.carrierText().isEmpty() && carrier == null) {
                code = "CARRIER_INVALID";
                message = "지원하지 않는 택배사입니다.";
            } else if (carrier != null) {
                String duplicate = !seenInvoices.add(carrier.name() + ":" + raw.trackingNumber())
                        ? "파일 안에서 중복된 송장번호입니다."
                        : claimService.findReshipInvoiceDuplicate(carrier, raw.trackingNumber(), claim.getId(), marketId);
                if (duplicate != null) {
                    code = "INVOICE_DUPLICATE";
                    message = duplicate;
                }
            }
            if (code == null) {
                valid++;
            }
            rows.add(new SellerClaimReshipDto.ParsedRow(raw.rowNumber(), raw.claimNumber(),
                    claim == null ? null : claim.getId(), carrier, raw.trackingNumber(), code == null, code, message));
        }
        return new SellerClaimReshipDto.ParseResponse(rows.size(), valid, rows);
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private static Long parseId(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
