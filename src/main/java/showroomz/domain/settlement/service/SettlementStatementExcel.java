package showroomz.domain.settlement.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.type.SettlementStatementColumn;
import showroomz.domain.settlement.type.SettlementStatementColumn.Surface;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정산 명세 xlsx(44 어드민 설계서 7-8 · 파트너 2-5 · 스튜디오 4-1) — 세 서피스가 같은 행({@code settlement_item})을 서피스별 열 집합으로
 * 내린다({@link SettlementStatementColumn#forSurface}). 다운로드는 <b>확정 후만</b>(공통 결정 #15) — 확인 중 · 조정 협의면 409.
 *
 * <p>마지막에 합계 행(확정 거래액 · 항목 리워드 합계 · 합의 후 리워드)을 둔다 — 합의 금액은 회차 총액이라 항목 리워드 합계와 어긋날 수
 * 있다(4-3). 어드민은 분해 요약 시트를 하나 더 받는다.
 */
@Component
public class SettlementStatementExcel {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public record File(String filename, byte[] content) {
    }

    /** 확정 전이면 409 — 페이지 조회는 상태 무관하게 열리고 다운로드만 닫힌다. */
    public static void requireDownloadable(Settlement settlement) {
        if (!settlement.getStatus().isConfirmed()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_STATEMENT_NOT_READY);
        }
    }

    public File write(Settlement settlement, List<SettlementItem> items, Surface surface) {
        requireDownloadable(settlement);
        List<SettlementStatementColumn> columns = SettlementStatementColumn.forSurface(surface);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);

            Sheet sheet = workbook.createSheet("정산 명세");
            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).getHeader());
                cell.setCellStyle(bold);
                sheet.setColumnWidth(c, 16 * 256);
            }
            int rowIndex = 1;
            for (SettlementItem item : items) {
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < columns.size(); c++) {
                    setValue(row.createCell(c), columns.get(c), settlement, item);
                }
            }
            rowIndex++;
            long settled = items.stream().mapToLong(SettlementItem::getSettledAmount).sum();
            long itemReward = items.stream().mapToLong(SettlementItem::getRewardAmount).sum();
            rowIndex = total(sheet, bold, rowIndex, "확정 거래액", settled);
            rowIndex = total(sheet, bold, rowIndex, "항목 리워드 합계", itemReward);
            total(sheet, bold, rowIndex, "합의 후 리워드", settlement.getRewardAmount());

            if (surface == Surface.ADMIN) {
                summarySheet(workbook, bold, settlement);
            }
            workbook.write(out);
            return new File("정산명세_%s.xlsx".formatted(settlement.getSettlementNumber()), out.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private static int total(Sheet sheet, CellStyle bold, int rowIndex, String label, long amount) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(bold);
        row.createCell(1).setCellValue(amount);
        return rowIndex + 1;
    }

    /** 어드민 「분해 요약」 시트 — 07b 금액 분해 두 축 + 플랫폼 몫. */
    private static void summarySheet(XSSFWorkbook workbook, CellStyle bold, Settlement s) {
        Sheet sheet = workbook.createSheet("분해 요약");
        Map<String, Long> rows = new LinkedHashMap<>();
        rows.put("총 주문 금액", s.getGrossOrderAmount());
        rows.put("취소 차감", -s.getCancelDeduction());
        rows.put("반품 차감", -s.getReturnDeduction());
        rows.put("배송 예외 차감", -s.getDeliveryExceptionDeduction());
        rows.put("확정 거래액", s.getConfirmedSalesAmount());
        rows.put("PG 수수료", -s.getPgFeeAmount());
        rows.put("플랫폼 수수료", -s.getPlatformFeeAmount());
        rows.put("리워드", -s.getRewardAmount());
        rows.put("리워드 부가세", -s.getRewardVatAmount());
        rows.put("재발송비", s.getReshipFeeAmount());
        rows.put("소비자 결제 배송비", s.getConsumerDeliveryFeeAmount());
        rows.put("브랜드 차감", -s.getBrandClawbackAmount());
        rows.put("브랜드 수취액", s.getBrandPayoutAmount());
        rows.put("리워드 차감", -s.getRewardClawbackAmount());
        rows.put("원천징수", -s.getWithholdingAmount());
        rows.put("인플루언서 부가세", s.getCreatorVatAmount());
        rows.put("인플루언서 실지급액", s.getCreatorPayoutAmount());
        rows.put("플랫폼 몫", s.getPlatformShareAmount());
        int index = 0;
        Row title = sheet.createRow(index++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue(s.getSettlementNumber());
        titleCell.setCellStyle(bold);
        for (Map.Entry<String, Long> entry : rows.entrySet()) {
            Row row = sheet.createRow(index++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 16 * 256);
    }

    private static void setValue(Cell cell, SettlementStatementColumn column, Settlement settlement,
                                 SettlementItem item) {
        switch (column) {
            case SETTLEMENT_NUMBER -> cell.setCellValue(settlement.getSettlementNumber());
            case ORDER_NUMBER -> cell.setCellValue(nullSafe(item.getOrderNumber()));
            case SUB_ORDER_NUMBER -> cell.setCellValue(nullSafe(item.getSubOrderNumber()));
            case CONSUMER -> cell.setCellValue(nullSafe(item.getConsumerNameMasked()));
            case PRODUCT -> cell.setCellValue(nullSafe(item.getProductName()));
            case OPTION -> cell.setCellValue(nullSafe(item.getOptionName()));
            case QUANTITY -> cell.setCellValue(item.getQuantity());
            case SETTLED_QUANTITY -> cell.setCellValue(item.getSettledQuantity());
            case UNIT_PRICE -> cell.setCellValue(item.getUnitPrice());
            case PAID_AMOUNT -> cell.setCellValue(item.getPaidAmount());
            case STATUS -> cell.setCellValue(item.getStatus().getLabel());
            case SETTLED_AMOUNT -> cell.setCellValue(item.getSettledAmount());
            case REWARD_RATE -> cell.setCellValue(item.getRewardRate().doubleValue());
            case REWARD_AMOUNT -> cell.setCellValue(item.getRewardAmount());
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
