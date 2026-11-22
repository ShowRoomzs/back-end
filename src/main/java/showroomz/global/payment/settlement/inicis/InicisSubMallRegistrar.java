package showroomz.global.payment.settlement.inicis;

import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.AmlThresholdAction;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.AmlStatus;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.SubMallRequest;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 이니시스 서브몰 등록 · 수정(60_KG이니시스_지급대행_전환_계획서.md 1-4 · D-3 · D-4 · D-6) — {@code upsert} {@code INS}/{@code UPD}.
 *
 * <ul>
 *   <li>id 는 우리가 정한다 — {@code idMall = {접두}b{marketId} / {접두}c{creatorId}}(10자 이내 · D-3). PG 발급 id 가 없어 id 복구가 없다.
 *   {@code INS} 가 「이미 사용중인 ID」(EM04)면 {@code UPD} 로, {@code UPD} 가 「수정 가능한 ID 없음」(EM05)이면 {@code INS} 로 1회 재시도한다.</li>
 *   <li>승인 단계가 없다 — 등록 성공 = 지급 가능(원시 상태 {@code REGISTERED} · 추정). 개인 / 사업자 구분 필드가 없다(토스 법인 판정 폐기).</li>
 *   <li>필수값(D-4) — 생년월일(브랜드는 대표자) · 휴대폰 · 이메일 · 대표자명이 비면 부르지 않고 {@code PARTNER_PROFILE_INCOMPLETE}. 계좌번호 >20자 ·
 *   예금주 >50byte · 예금주 허용 문자 밖은 {@code ACCOUNT_INVALID} — 임의 치환하지 않는다. 매뉴얼 은행코드 표 밖의 은행은 {@code UNSUPPORTED_BANK}.
 *   길이는 매뉴얼대로 UTF-8 byte 다(대표자명 30 · 이메일 60). 상호(50byte)만 넘으면 잘라 보낸다 — 대조되는 값이 아니어서 지급을 막지 않는다.</li>
 *   <li>AML(D-6) — {@code amlstatus} 를 짧은 원시값({@code E{심사}|R{재이행}|X{유효}})으로 접어 돌려준다. 막는 상태는 심사보류(B) · 경과(F) ·
 *   유효 N(만료). 미대상(SM02)은 막지 않는다. 스테이징({@code PG_TEST})은 조회가 막혀 있어 empty.</li>
 * </ul>
 */
@Slf4j
public class InicisSubMallRegistrar implements SettlementPartnerGateway {

    public static final String STATUS_REGISTERED = "REGISTERED";
    /** 길이는 매뉴얼대로 <b>byte</b>(요청은 UTF-8 — 한글 1자 = 3byte). ENC 필드는 평문 기준으로 본다(가정). */
    static final int ACCOUNT_NUMBER_MAX = 20;
    static final int HOLDER_MAX_BYTES = 50;
    static final int COMPANY_NAME_MAX_BYTES = 50;
    static final int BOSS_NAME_MAX_BYTES = 30;
    static final int PHONE_MIN = 8;
    static final int PHONE_MAX = 20;
    static final int EMAIL_MAX_BYTES = 60;
    /**
     * 매뉴얼 「은행코드」 표(2026-11-22 · {@code opmall_aml.html#bankcode}) — 이 밖의 코드는 이니시스가 EM06 「등록 불가능한 은행코드」로 거절한다.
     * 우리 은행 목록에서 빠지는 활성 은행은 012 단위농협(지역농축협)이다(부르기 전에 {@code UNSUPPORTED_BANK} 로 막아 사유를 분명히 한다).
     */
    static final java.util.Set<String> SUPPORTED_BANK_CODES = java.util.Set.of(
            "002", "003", "004", "007", "011", "020", "023", "027", "031", "032", "034", "035", "037", "039", "045", "048",
            "050", "064", "071", "081", "088", "089", "090", "092",
            "209", "218", "227", "238", "240", "243", "247", "261", "262", "263", "264", "265", "266", "267", "269", "270",
            "271", "278", "279", "280", "287");
    /** 예금주 허용 문자 — 한글 · 영문 · 숫자 · 공백 · {@code ( ) & / - ' , . ·}(1-4). */
    static final Pattern HOLDER_ALLOWED = Pattern.compile("^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 ()&/\\-',.·]+$");
    private static final DateTimeFormatter BIRTH = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private final InicisPayoutsClient client;
    private final boolean test;
    private final InicisPayoutsProperties properties;

    public InicisSubMallRegistrar(InicisPayoutsClient client, boolean test, InicisPayoutsProperties properties) {
        this.client = client;
        this.test = test;
        this.properties = properties;
    }

    @Override
    public String partnerRefPrefix() {
        return test ? properties.getSubMallRefPrefixTest() : "";
    }

    @Override
    public String partnerRef(SettlementPayee payee, Long refId) {
        return SettlementPartnerIds.subMallId(partnerRefPrefix(), payee, refId);
    }

    @Override
    public String env() {
        return test ? "TEST" : "LIVE";
    }

    @Override
    public String provider() {
        return "INICIS";
    }

    @Override
    public PartnerStatus statusOf(String rawStatus) {
        if (rawStatus == null) {
            return PartnerStatus.FAILED;
        }
        return STATUS_REGISTERED.equals(rawStatus) ? PartnerStatus.APPROVED : PartnerStatus.REJECTED;
    }

    @Override
    public String blockCodeOf(String rawStatus) {
        return InicisPayoutFailureClassifier.PARTNER_NOT_READY;
    }

    @Override
    public AmlThresholdAction amlThresholdAction() {
        return properties.getAmlPrecheck() == InicisPayoutsProperties.AmlPrecheck.BLOCK
                ? AmlThresholdAction.BLOCK : AmlThresholdAction.ALERT;
    }

    @Override
    public Optional<AmlState> amlStatus(String externalId) {
        if (test || externalId == null) {
            return Optional.empty();
        }
        InicisPayoutsClient.Reply<AmlStatus> reply = client.amlStatus(externalId);
        if (InicisPayoutFailureClassifier.AML_NOT_TARGET.equals(reply.code())) {
            // SM02 「AML 미대상 판매자ID」 — 고객확인 의무가 없다.
            return Optional.of(new AmlState(AML_NOT_TARGET_RAW, false));
        }
        AmlStatus status = reply.body();
        if (status == null) {
            return Optional.empty();
        }
        String raw = foldAml(status);
        return Optional.of(new AmlState(raw, isAmlBlocking(raw)));
    }

    /** AML 미대상(SM02)의 저장값. */
    public static final String AML_NOT_TARGET_RAW = "NOT_TARGET";
    /** 심사 상태 중 지급을 막는 값 — B 심사보류 · F 경과(매뉴얼 popup_104). G 심사중 · I 진행중 · N 대기는 막지 않는다(이니시스가 제한하면 송금 실패로 드러난다). */
    static final String AML_BLOCKING_EXAM = "BF";

    /**
     * {@code E{amlExmnYn}|R{reAmlExmnYn}|X{amlAuthExpiredYn}} — 16자 이내. 값이 없으면 {@code -}. 심사 · 재이행 코드는 매뉴얼 「AML 상태 코드」(B 심사보류 ·
     * F 경과 · G 심사중 · I 진행중 · N 대기 · Y 심사완료) 그대로다.
     */
    static String foldAml(AmlStatus status) {
        return "E" + oneChar(status.amlExmnYn()) + "|R" + oneChar(status.reAmlExmnYn()) + "|X" + oneChar(status.amlAuthExpiredYn());
    }

    /**
     * 막는 상태 — 심사 상태(또는 재이행 상태)가 B 심사보류 · F 경과이거나, 이행 유효가 N(매뉴얼 본문: Y 정상 · N 만료)이면. 미대상 · 심사완료 · 진행 중은
     * 막지 않는다. 「N = 만료」는 매뉴얼 본문의 정의를 따랐다(응답 예시와 함께 보면 해석이 갈릴 수 있어 운영 소액 R3 로 확인 · Q8).
     */
    @Override
    public boolean isAmlBlocking(String rawAmlStatus) {
        if (rawAmlStatus == null || rawAmlStatus.length() < 8 || !rawAmlStatus.startsWith("E")) {
            return false;
        }
        char exam = rawAmlStatus.charAt(1);
        char reExam = rawAmlStatus.charAt(4);
        return AML_BLOCKING_EXAM.indexOf(exam) >= 0 || AML_BLOCKING_EXAM.indexOf(reExam) >= 0
                || rawAmlStatus.endsWith("|XN");
    }

    // ------------------------------------------------------------------ 등록 · 수정

    @Override
    public PartnerResult ensure(PartnerProfile profile) {
        PartnerResult precheck = precheck(profile);
        if (precheck != null) {
            return precheck;
        }
        String idMall = profile.partnerRef();
        String dml = profile.existing() ? InicisPayoutsDtos.DML_UPDATE : InicisPayoutsDtos.DML_INSERT;
        try {
            client.upsert(request(profile, dml));
            return PartnerResult.approved(idMall, STATUS_REGISTERED);
        } catch (PaymentGatewayRejectedException e) {
            String retryDml = null;
            if (InicisPayoutsDtos.DML_INSERT.equals(dml) && InicisPayoutFailureClassifier.isAlreadyRegistered(e.getType())) {
                // EM04 — 이니시스에는 있는데 우리 DB 에 연결이 없다(이전 시도의 커밋 실패 · 환경 전환). UPD 로 맞춘다(D-3).
                retryDml = InicisPayoutsDtos.DML_UPDATE;
            } else if (InicisPayoutsDtos.DML_UPDATE.equals(dml) && InicisPayoutFailureClassifier.isNotRegistered(e.getType())) {
                // EM05 — 우리는 연결이 있다고 보는데 이니시스에 없다. 새로 등록한다.
                retryDml = InicisPayoutsDtos.DML_INSERT;
            }
            if (retryDml == null) {
                return rejected(idMall, e);
            }
            log.info("이니시스 서브몰 {} 거절({}) — {} 로 재시도 - idMall: {}", dml, e.getType(), retryDml, idMall);
            try {
                client.upsert(request(profile, retryDml));
                return PartnerResult.approved(idMall, STATUS_REGISTERED);
            } catch (PaymentGatewayRejectedException retry) {
                return rejected(idMall, retry);
            }
        }
    }

    private static PartnerResult rejected(String idMall, PaymentGatewayRejectedException e) {
        log.warn("이니시스 서브몰 등록 거절 - idMall: {}, code: {}", idMall, e.getType());
        return PartnerResult.failed(InicisPayoutFailureClassifier.subMallFailCode(e.getType()),
                e.getType() + " · " + e.getMessage());
    }

    /** 부르기 전 검사(D-4) — PG 를 부를 수 없는 프로필은 사유와 함께 실패로. 통과면 null. 테스트의 가짜 서브몰 포트도 같은 규칙을 쓴다. */
    public PartnerResult precheck(PartnerProfile profile) {
        if (profile.account() == null || profile.account().isMissing()) {
            return PartnerResult.failed(InicisPayoutFailureClassifier.ACCOUNT_MISSING, "등록 계좌 없음");
        }
        if (isBlank(profile.account().bankCode())) {
            return PartnerResult.failed(InicisPayoutFailureClassifier.UNSUPPORTED_BANK,
                    "은행 코드를 정할 수 없다 — " + profile.account().bankName());
        }
        if (!SUPPORTED_BANK_CODES.contains(profile.account().bankCode().trim())) {
            return PartnerResult.failed(InicisPayoutFailureClassifier.UNSUPPORTED_BANK,
                    "KG이니시스 지급대행 미지원 은행 — " + profile.account().bankName() + "(" + profile.account().bankCode().trim() + ")");
        }
        String number = digits(profile.account().accountNumber());
        if (number.isEmpty() || number.length() > ACCOUNT_NUMBER_MAX) {
            return PartnerResult.failed(InicisPayoutFailureClassifier.ACCOUNT_INVALID, "계좌번호 " + ACCOUNT_NUMBER_MAX + "자 초과");
        }
        String holder = profile.account().holder();
        if (isBlank(holder) || bytes(holder) > HOLDER_MAX_BYTES) {
            return PartnerResult.failed(InicisPayoutFailureClassifier.ACCOUNT_INVALID,
                    "예금주 없음 · " + HOLDER_MAX_BYTES + "byte 초과(한글 1자 3byte)");
        }
        if (!HOLDER_ALLOWED.matcher(holder).matches()) {
            return PartnerResult.failed(InicisPayoutFailureClassifier.ACCOUNT_INVALID, "예금주에 허용되지 않는 문자");
        }
        if (birthdate(profile.birthdate()) == null) {
            return incomplete(profile.payee() == SettlementPayee.BRAND ? "대표자 생년월일 없음" : "생년월일 없음");
        }
        String phone = digits(profile.contact() == null ? null : profile.contact().phoneNumber());
        if (phone.length() < PHONE_MIN || phone.length() > PHONE_MAX) {
            return incomplete("휴대폰 번호 없음");
        }
        String email = profile.contact() == null ? null : profile.contact().email();
        if (isBlank(email) || bytes(email) > EMAIL_MAX_BYTES) {
            return incomplete("이메일 없음 · " + EMAIL_MAX_BYTES + "byte 초과");
        }
        String boss = bossName(profile);
        if (isBlank(boss) || bytes(boss) > BOSS_NAME_MAX_BYTES) {
            return incomplete("대표자명 없음 · " + BOSS_NAME_MAX_BYTES + "byte 초과(한글 1자 3byte)");
        }
        if (isBlank(companyName(profile))) {
            // 상호는 50byte 를 넘으면 잘라 보낸다(request) — 법인 상호가 길다고 지급을 막지 않는다. 계좌 · 대표자처럼 대조되는 값이 아니다.
            return incomplete("상호 · 활동명 없음");
        }
        if (profile.business() != null && digits(profile.business().registrationNumber()).length() != 10) {
            return incomplete("사업자등록번호 10자리 아님");
        }
        return null;
    }

    SubMallRequest request(PartnerProfile profile, String dml) {
        String mid = properties.getMerchantId();
        String number = digits(profile.account().accountNumber());
        String noComp = profile.business() == null ? null : digits(profile.business().registrationNumber());
        return new SubMallRequest(mid, profile.partnerRef(), InicisPayoutsDtos.CL_ID_SUB_MALL, noComp,
                truncateBytes(companyName(profile), COMPANY_NAME_MAX_BYTES),
                InicisPayoutCrypto.enc(bossName(profile)), InicisPayoutCrypto.enc(profile.account().holder()),
                profile.account().bankCode().trim(), InicisPayoutCrypto.enc(number),
                InicisPayoutCrypto.enc(digits(profile.contact().phoneNumber())), InicisPayoutCrypto.enc(profile.contact().email()),
                InicisPayoutCrypto.enc(birthdate(profile.birthdate())), dml, InicisPayoutsDtos.SVC_REQUEST,
                InicisPayoutCrypto.userHash(KstDates.today(), mid, number, properties.getApiKey()));
    }

    /** 대표자명 — 사업자는 대표자, 비사업자 인플루언서는 본인 이름(Q6). */
    private static String bossName(PartnerProfile profile) {
        if (profile.business() != null && !isBlank(profile.business().representativeName())) {
            return profile.business().representativeName().trim();
        }
        return profile.contact() == null || profile.contact().name() == null ? null : profile.contact().name().trim();
    }

    /** 상호 — 사업자는 상호, 비사업자는 활동명(없으면 이름 · Q6). */
    private static String companyName(PartnerProfile profile) {
        if (profile.business() != null && !isBlank(profile.business().companyName())) {
            return profile.business().companyName().trim();
        }
        if (!isBlank(profile.name())) {
            return profile.name().trim();
        }
        return profile.contact() == null || profile.contact().name() == null ? null : profile.contact().name().trim();
    }

    /** 생년월일 → {@code yyyyMMdd}(숫자 8자리 · 실제 날짜). 아니면 null. */
    static String birthdate(String value) {
        String digits = digits(value);
        if (digits.length() != 8) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(digits, BIRTH);
            return date.isAfter(LocalDate.now()) ? null : digits;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static PartnerResult incomplete(String reason) {
        return PartnerResult.failed(InicisPayoutFailureClassifier.PARTNER_PROFILE_INCOMPLETE, reason);
    }

    private static String oneChar(String value) {
        return value == null || value.isBlank() ? "-" : value.trim().substring(0, 1).toUpperCase();
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    /** UTF-8 byte 길이 — 매뉴얼 길이 단위. */
    static int bytes(String value) {
        return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** UTF-8 {@code max} byte 안으로 — 글자(코드 포인트) 중간을 자르지 않는다. */
    static String truncateBytes(String value, int max) {
        if (value == null || bytes(value) <= max) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int size = bytes(ch);
            if (used + size > max) {
                break;
            }
            out.append(ch);
            used += size;
            i += Character.charCount(cp);
        }
        return out.toString().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
