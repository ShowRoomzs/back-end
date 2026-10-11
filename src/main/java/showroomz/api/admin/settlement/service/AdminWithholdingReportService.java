package showroomz.api.admin.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.common.settlement.dto.SettlementFile;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementWithholdingReportLog;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementWithholdingReportLogRepository;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.service.SettlementTableExcel;
import showroomz.global.utils.PersonalDataCipher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 원천세 신고 자료(44 어드민 설계서 5-5 · 07a 툴바) — 그 달에 인플루언서 몫이 지급된 <b>비사업자</b> 정산. 주민등록번호를 복호화해
 * <b>이 파일에만</b> 쓰고, 내려받을 때마다 반출 기록(운영자 · 월 · 행 수)을 남긴다(13절 C-6 · 고유식별정보).
 */
@Service
@RequiredArgsConstructor
public class AdminWithholdingReportService {

    private final SettlementRepository settlementRepository;
    private final SettlementWithholdingReportLogRepository logRepository;
    private final SettlementTableExcel tableExcel;
    private final PersonalDataCipher cipher;
    private final AdminOperatorResolver operators;

    @Transactional
    public SettlementFile download(YearMonth month, Long operatorId) {
        operators.operatorName(operatorId);
        List<Object[]> targets = settlementRepository.findWithholdingTargets(month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
        List<List<Object>> rows = new ArrayList<>();
        long[] totals = new long[3];
        for (Object[] target : targets) {
            Settlement s = (Settlement) target[0];
            LocalDateTime paidAt = (LocalDateTime) target[1];
            Creator creator = s.getCreator();
            long gross = s.getRewardAfterClawback();
            long incomeTax = BigDecimal.valueOf(gross).multiply(s.getWithholdingIncomeRate())
                    .setScale(0, RoundingMode.DOWN).longValue();
            long localTax = s.getWithholdingAmount() - incomeTax;
            rows.add(List.of(s.getSettlementNumber(), s.getContract().getTitle(),
                    creator.getRealName() == null ? "" : creator.getRealName(),
                    creator.getResidentRegistrationNumberEnc() == null ? ""
                            : cipher.decrypt(creator.getResidentRegistrationNumberEnc()),
                    gross, incomeTax, localTax, paidAt.toLocalDate().toString()));
            totals[0] += gross;
            totals[1] += incomeTax;
            totals[2] += localTax;
        }
        logRepository.save(SettlementWithholdingReportLog.of(operatorId, month.toString(), rows.size(),
                LocalDateTime.now()));
        SettlementStatementExcel.File file = tableExcel.write("원천세신고자료_%s.xlsx".formatted(month),
                "%s 원천세".formatted(month),
                List.of("정산번호", "공구", "인플루언서 실명", "주민등록번호", "지급액(차감 후 리워드)", "소득세", "지방소득세", "지급일"),
                rows, List.of("합계", "", "", "", totals[0], totals[1], totals[2], ""));
        return new SettlementFile(file.filename(), SettlementFile.XLSX, file.content());
    }
}
