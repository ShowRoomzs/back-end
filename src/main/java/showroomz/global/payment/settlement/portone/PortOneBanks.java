package showroomz.global.payment.settlement.portone;

import io.portone.sdk.server.common.Bank;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 표준 은행 코드({@code bank.bank_code}) ↔ 포트원 SDK {@link Bank}(44_포트원_파트너정산_연동_BE_설계서.md 4-5). 한 곳의 상수 표 —
 * 없는 코드는 등록 실패({@code UNSUPPORTED_BANK}). 포트원이 지급을 지원하지 않는 은행은 등록 때 {@code PlatformNotSupportedBankError}로 걸러진다.
 */
public final class PortOneBanks {

    private static final Map<String, Bank> BY_CODE = new LinkedHashMap<>();

    static {
        // 은행
        put("004", Bank.Kookmin.INSTANCE);
        put("088", Bank.Shinhan.INSTANCE);
        put("020", Bank.Woori.INSTANCE);
        put("081", Bank.Hana.INSTANCE);
        put("011", Bank.Nonghyup.INSTANCE);
        put("012", Bank.LocalNonghyup.INSTANCE);
        put("090", Bank.Kakao.INSTANCE);
        put("092", Bank.Toss.INSTANCE);
        put("089", Bank.KBank.INSTANCE);
        put("003", Bank.Ibk.INSTANCE);
        put("023", Bank.StandardChartered.INSTANCE);
        put("071", Bank.Post.INSTANCE);
        put("045", Bank.Kfcc.INSTANCE);
        put("048", Bank.Shinhyup.INSTANCE);
        put("007", Bank.Suhyup.INSTANCE);
        put("027", Bank.Citi.INSTANCE);
        put("064", Bank.Nfcf.INSTANCE);
        put("050", Bank.SavingsBank.INSTANCE);
        put("031", Bank.Daegu.INSTANCE);
        put("032", Bank.Busan.INSTANCE);
        put("039", Bank.Kyongnam.INSTANCE);
        put("034", Bank.Kwangju.INSTANCE);
        put("037", Bank.Jeonbuk.INSTANCE);
        put("035", Bank.Jeju.INSTANCE);
        put("002", Bank.Kdb.INSTANCE);
        put("001", Bank.BankOfKorea.INSTANCE);
        // 증권
        put("261", Bank.KyoboSecurities.INSTANCE);
        put("267", Bank.DaishinSecurities.INSTANCE);
        put("287", Bank.MeritzSecurities.INSTANCE);
        put("238", Bank.MiraeAssetSecurities.INSTANCE);
        put("290", Bank.BookookSecurities.INSTANCE);
        put("240", Bank.SamsungSecurities.INSTANCE);
        put("291", Bank.ShinyoungSecurities.INSTANCE);
        put("278", Bank.ShinhanSecurities.INSTANCE);
        put("209", Bank.YuantaSecurities.INSTANCE);
        put("280", Bank.EugeneSecurities.INSTANCE);
        put("264", Bank.KiwoomSecurities.INSTANCE);
        put("271", Bank.TossSecurities.INSTANCE);
        put("270", Bank.HanaSecurities.INSTANCE);
        put("262", Bank.HiSecurities.INSTANCE);
        put("243", Bank.KoreaSecurities.INSTANCE);
        put("269", Bank.HanhwaSecurities.INSTANCE);
        put("263", Bank.HyundaiMotorSecurities.INSTANCE);
        put("279", Bank.DbSecurities.INSTANCE);
        put("218", Bank.KbSecurities.INSTANCE);
        put("227", Bank.DaolSecurities.INSTANCE);
        put("292", Bank.LeadingSecurities.INSTANCE);
        put("247", Bank.NhSecurities.INSTANCE);
        put("266", Bank.SkSecurities.INSTANCE);
    }

    private PortOneBanks() {
    }

    private static void put(String code, Bank bank) {
        BY_CODE.put(code, bank);
    }

    public static Optional<Bank> byCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code.trim()));
    }

    /** 포트원 값({@code KOOKMIN}) → 표준 코드. 파트너 현재 계좌와 우리 계좌를 비교할 때. */
    public static Optional<String> codeOf(Bank bank) {
        if (bank == null) {
            return Optional.empty();
        }
        return BY_CODE.entrySet().stream()
                .filter(e -> e.getValue().getValue().equals(bank.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public static int size() {
        return BY_CODE.size();
    }
}
