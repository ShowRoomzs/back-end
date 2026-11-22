package showroomz.global.payment.settlement.inicis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 이니시스 지급대행(신버전 AML) 요청 · 응답 모양(60 전환 계획서 1-3 ~ 1-7). 매뉴얼은 필드마다 길이(byte)만 적고 타입을 적지 않아 요청 값은 전부
 * 문자열로 보낸다(가정 · Q4 — 응답 예시는 {@code amtSupply} 가 숫자). 모르는 필드는 버린다 — 이니시스가 필드를 더해도 깨지지 않게. 요청은
 * {@code null} 필드를 내보내지 않는다.
 */
public final class InicisPayoutsDtos {

    public static final String DML_INSERT = "INS";
    public static final String DML_UPDATE = "UPD";
    public static final String DML_DELETE = "DEL";
    public static final String SVC_REQUEST = "REQ";
    /** {@code clId} — 수취자는 전부 sub mall. */
    public static final String CL_ID_SUB_MALL = "2";
    /** {@code clService} — 익일지급. */
    public static final String CL_SERVICE_NEXT_DAY = "0";
    /** {@code clService} — 당일지급(별도 계약). */
    public static final String CL_SERVICE_SAME_DAY = "2";
    /** {@code clPay} — 송금 성공. 그 밖은 전부 실패(매뉴얼: 「"01":성공, 이외 실패」 · 실패 값 목록은 없다). */
    public static final String CL_PAY_SUCCESS = "01";

    private InicisPayoutsDtos() {
    }

    /** 서브몰 등록 · 수정({@code POST /upsert}) — ENC 필드는 {@link InicisPayoutCrypto#enc} 값이다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubMallRequest(String idMerchant, String idMall, String clId, String noComp, String nmComp, String nmBoss,
                                 String nmRegist, String cdBank, String noAcct, String noTel, String noEmail, String bthDt,
                                 String svcDmlCd, String svcType, String userHash) {
    }

    /** 지급 요청 · 삭제({@code POST /payreq}) — 계좌는 보내지 않는다(서브몰 등록 계좌로 나간다 · 1-3). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PayRequest(String idMerchant, String idMall, String dtPay, String amtSupply, String clService,
                             String svcDmlCd, String svcType) {
    }

    /**
     * 송금 결과 1건({@code GET /payresult} 의 {@code data}) — 매뉴얼 응답 예시는 계좌({@code cdBank} · {@code noAcct})가 평문이다. 계좌는 송금 계좌
     * 대조(끝 4자리)에만 쓰고 저장 · 로그하지 않는다. {@code amtSupply} 는 예시에서 숫자 — 문자열로 받아 숫자만 읽는다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PayResult(String svcType, String idMall, String noComp, String nmComp, String dtPay, String amtSupply,
                            String remitDtPay, String clPay, String clService, String cdBank, String noAcct) {
    }

    /** 잔액 대사({@code GET /paycheck}) — {@code totalAmt} 가 가용 잔액인지 기간 순증감인지는 미확인(Q1). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PayCheck(String noComp, String totalAmt, String idMerchant, String payTransDt) {
    }

    /** 서브몰 AML 상태({@code GET /amlstatus}) — 심사 · 재이행 코드는 매뉴얼 「AML 상태 코드」(B · F · G · I · N · Y) · 유효 Y 정상 / N 만료. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmlStatus(String amlExmnYn, String reAmlExmnYn, String amlReExecFromDt, String amlReExecToDt,
                            String amlAuthExpiredYn, String amlTransmDt) {
    }
}
