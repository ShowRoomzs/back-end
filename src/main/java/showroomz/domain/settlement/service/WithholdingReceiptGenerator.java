package showroomz.domain.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Entities;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.admin.contract.service.ContractPdfRenderer;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.port.SettlementTaxDocumentStorage;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.ContractPdfAsyncConfig;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.PersonalDataCipher;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 원천징수영수증 생성(44 어드민 설계서 5-3) — 인플루언서 몫 지급 완료 뒤 비동기로 「원천징수영수증(지급명세서 간이)」을 그려 S3 private
 * 에 올리고 행을 {@code GENERATED}로 바꾼다.
 *
 * <p>주민등록번호는 {@link PersonalDataCipher#decrypt}로 <b>렌더링 입력을 만드는 순간에만</b> 복호화하고 응답 · 로그 · 이력 어디에도 쓰지
 * 않는다. 생성 실패는 지급 완료를 막지 않는다 — {@code PENDING_ISSUE}로 두고 재시도 배치(매시 40분)가 다시 만든다.
 */
@Slf4j
@Component
public class WithholdingReceiptGenerator {

    private static final String TEMPLATE = "templates/settlement/withholding-receipt.html";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");

    private final SettlementTaxDocumentRepository documentRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementTaxDocumentStorage storage;
    private final SettlementHistoryRecorder historyRecorder;
    private final ContractPdfRenderer renderer;
    private final PersonalDataCipher cipher;
    private final SettlementProperties properties;
    private final TransactionTemplate newTransaction;
    private volatile String template;

    public WithholdingReceiptGenerator(SettlementTaxDocumentRepository documentRepository,
                                       SettlementRepository settlementRepository,
                                       SettlementPayoutRepository payoutRepository,
                                       SettlementTaxDocumentStorage storage,
                                       SettlementHistoryRecorder historyRecorder,
                                       ContractPdfRenderer renderer,
                                       PersonalDataCipher cipher,
                                       SettlementProperties properties,
                                       PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.settlementRepository = settlementRepository;
        this.payoutRepository = payoutRepository;
        this.storage = storage;
        this.historyRecorder = historyRecorder;
        this.renderer = renderer;
        this.cipher = cipher;
        this.properties = properties;
        // 커밋 직후(동기 실행기면 같은 스레드) 불리므로 끝난 트랜잭션에 합류하지 않게 새 트랜잭션을 연다.
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 지급 완료 커밋 뒤 — 계약서 PDF 와 같은 단일 스레드 실행기(렌더러가 한 번에 하나만 돈다). */
    @Async(ContractPdfAsyncConfig.CONTRACT_PDF_EXECUTOR)
    public void generateAsync(Long documentId) {
        generate(documentId);
    }

    /** @return 이번 호출로 생성했으면 true — 이미 생성됐거나 실패면 false */
    public boolean generate(Long documentId) {
        Receipt receipt;
        try {
            receipt = newTransaction.execute(status -> prepare(documentId));
        } catch (RuntimeException e) {
            log.warn("원천징수영수증 입력 준비 실패 - documentId: {}", documentId, e);
            return false;
        }
        if (receipt == null) {
            return false;
        }
        try {
            byte[] pdf = renderer.render(render(receipt.values()), receipt.settlementNumber());
            String key = storage.put(receipt.settlementId(), documentId, pdf);
            LocalDateTime now = LocalDateTime.now();
            Boolean done = newTransaction.execute(status -> {
                if (documentRepository.markGenerated(documentId, key, receipt.fileName(), now) != 1) {
                    return false;
                }
                historyRecorder.recordBySystem(receipt.settlementId(), SettlementEventType.WITHHOLDING_RECEIPT_GENERATED,
                        "원천징수영수증 생성", now);
                return true;
            });
            return Boolean.TRUE.equals(done);
        } catch (RuntimeException e) {
            log.warn("원천징수영수증 생성 실패 — 재시도 배치가 다시 만든다 - documentId: {}", documentId, e);
            return false;
        }
    }

    private record Receipt(Long settlementId, String settlementNumber, String fileName, Map<String, String> values) {
    }

    /** 렌더링 입력 — 생성 대기 행만. 주민등록번호는 여기서만 복호화한다. */
    private Receipt prepare(Long documentId) {
        SettlementTaxDocument document = documentRepository.findById(documentId).orElse(null);
        if (document == null || document.getType() != TaxDocumentType.WITHHOLDING_RECEIPT
                || document.getStatus() != TaxDocumentStatus.PENDING_ISSUE) {
            return null;
        }
        Settlement s = settlementRepository.findDetailById(document.getSettlementId()).orElse(null);
        if (s == null) {
            return null;
        }
        Creator creator = s.getCreator();
        if (creator.getResidentRegistrationNumberEnc() == null) {
            throw new IllegalStateException("주민등록번호 미등록 — 원천징수영수증을 만들 수 없다");
        }
        LocalDate paidDate = payoutRepository.findBySettlementIdAndPayee(s.getId(), SettlementPayee.CREATOR)
                .map(SettlementPayout::getPaidAt).map(LocalDateTime::toLocalDate).orElse(LocalDate.now());
        long gross = s.getRewardAfterClawback();
        long incomeTax = BigDecimal.valueOf(gross).multiply(s.getWithholdingIncomeRate())
                .setScale(0, RoundingMode.DOWN).longValue();
        long localTax = s.getWithholdingAmount() - incomeTax;
        SettlementProperties.Platform platform = properties.getPlatform();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("settlementNumber", s.getSettlementNumber());
        values.put("issuerName", platform.getBusinessName());
        values.put("issuerRepresentative", platform.getRepresentative());
        values.put("issuerRegistrationNumber", platform.getRegistrationNumber());
        values.put("issuerAddress", platform.getAddress());
        values.put("recipientName", creator.getRealName() == null ? creator.getShowroomName() : creator.getRealName());
        values.put("residentNumber", cipher.decrypt(creator.getResidentRegistrationNumberEnc()));
        values.put("paidDate", DATE.format(paidDate));
        values.put("groupBuyTitle", s.getContract().getTitle());
        values.put("grossAmount", "%,d".formatted(gross));
        values.put("incomeRate", percent(s.getWithholdingIncomeRate()));
        values.put("incomeTax", "%,d".formatted(incomeTax));
        values.put("localRate", percent(s.getWithholdingLocalRate()));
        values.put("localTax", "%,d".formatted(localTax));
        values.put("netAmount", "%,d".formatted(s.getCreatorPayoutAmount()));
        values.put("issuedDate", DATE.format(LocalDate.now()));
        return new Receipt(s.getId(), s.getSettlementNumber(), "원천징수영수증_" + s.getSettlementNumber() + ".pdf",
                values);
    }

    private String render(Map<String, String> values) {
        String html = template();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            html = html.replace("{{" + entry.getKey() + "}}", Entities.escape(entry.getValue() == null ? "" : entry.getValue()));
        }
        return html;
    }

    private String template() {
        String loaded = template;
        if (loaded == null) {
            try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
                loaded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("원천징수영수증 템플릿을 읽을 수 없다: " + TEMPLATE, e);
            }
            template = loaded;
        }
        return loaded;
    }

    private static String percent(BigDecimal rate) {
        return rate == null ? "" : rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
