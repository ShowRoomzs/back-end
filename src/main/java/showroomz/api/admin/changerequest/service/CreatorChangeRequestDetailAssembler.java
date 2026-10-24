package showroomz.api.admin.changerequest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.common.AdminMemberMasker;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.entity.ChangeRequestItem;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.contract.type.WithholdingType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.global.utils.PersonalDataCipher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 어드민 변경 요청 상세 — 인플루언서 사업자 구분 변경 건(C3a · C3b · C5b · C7b · 16 설계서 3-4 · 3-5). 저장하는 것은 요청 항목 2개뿐이고
 * 지급 방식 · 예금주 판정 · 계좌 · 상호/대표자 행은 조회 시 계산한다({@code source}로 행 성격을 내린다).
 *
 * <p>개인정보: 본인확인 실명 · 생년월일은 <b>검토 대기 건에서만</b> 실값(서류 대조 목적) · 처리 후는 마스킹. 계좌는 항상 마스킹이다(1-10 · 1-11).
 * 주민등록번호는 항목의 마스킹 값만 내리고 암호문은 생년월일 대조에만 쓴다(검토 대기 동안만 행에 있다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorChangeRequestDetailAssembler implements ChangeRequestDetailAssembler {

    private static final String PAYOUT_INDIVIDUAL = "원천징수 3.3%";
    private static final String PAYOUT_BUSINESS = "세금계산서 · 리워드 + 부가세 10%";

    private final SettlementRepository settlementRepository;
    private final PersonalDataCipher cipher;

    @Override
    public ChangeRequesterType requesterType() {
        return ChangeRequesterType.CREATOR;
    }

    /** 변경 사유 입력란이 없어 reason 은 null 이 정상이다 · 요청자 이름 스냅샷도 없다(본인). */
    @Override
    public ChangeRequestDetailParts assemble(ChangeRequest request) {
        if (request.getType() == ChangeRequestType.CREATOR_ACCOUNT) {
            return assembleAccount(request);
        }
        Creator creator = request.getCreator();
        boolean pending = request.isPending();
        boolean toBusiness = request.getType() == ChangeRequestType.TO_BUSINESS;
        String holder = pending ? creator.getRealName() : AdminMemberMasker.maskName(creator.getRealName());
        String birthday = pending ? creator.getBirthday() : AdminMemberMasker.maskBirthdayYearOnly(creator.getBirthday());
        ChangeRequestItem typeItem = item(request, ChangeRequestField.CREATOR_BUSINESS_TYPE);
        String account = account(creator);

        List<AdminChangeRequestDto.DiffRow> diff = new ArrayList<>();
        diff.add(row("CREATOR_BUSINESS_TYPE", "사업자 구분", businessTypeLabel(typeItem == null ? null : typeItem.getCurrentValue()),
                businessTypeLabel(typeItem == null ? null : typeItem.getRequestedValue()), true, "REQUEST"));
        if (toBusiness) {
            ChangeRequestItem brn = item(request, ChangeRequestField.CREATOR_BUSINESS_REG_NUMBER);
            diff.add(row("CREATOR_BUSINESS_REG_NUMBER", "사업자등록번호", brn == null ? null : brn.getCurrentValue(),
                    brn == null ? null : brn.getRequestedValue(), true, "REQUEST"));
            // 상호 · 대표자는 받지 않는다(§50-2) — 운영자가 등록증에서 읽는다.
            diff.add(row("LICENSE_COMPANY_REPRESENTATIVE", "상호 · 대표자 (등록증)", null, null, false, "DOCUMENT"));
        } else {
            // 폐업일은 서버가 모른다 — 「폐업」까지만(16 설계서 3-4).
            diff.add(row("CREATOR_BUSINESS_REG_NUMBER", "사업자등록번호", creator.getBusinessRegistrationNumber(), "폐업", true, "DERIVED"));
            ChangeRequestItem rrn = item(request, ChangeRequestField.CREATOR_RESIDENT_NUMBER);
            diff.add(row("CREATOR_RESIDENT_NUMBER", "주민등록번호", rrn == null ? null : rrn.getCurrentValue(),
                    rrn == null ? null : rrn.getRequestedValue(), true, "REQUEST"));
        }
        diff.add(row("PAYOUT_METHOD", "지급 방식", payoutLabel(request.getType().sourceBusinessType()),
                payoutLabel(request.getType().targetBusinessType()), true, "DERIVED"));
        diff.add(row("ACCOUNT_HOLDER", "정산 계좌 예금주", holder, null, false, "CHECK"));
        diff.add(row("SETTLEMENT_ACCOUNT", "정산 계좌", account, null, false, "LIVE"));

        List<String> changedFieldLabels = request.getItems().stream()
                .sorted(Comparator.comparing(ChangeRequestItem::getSortOrder))
                .map(i -> i.getFieldKey().getLabel())
                .toList();

        AdminChangeRequestDto.Evidence evidence = AdminChangeRequestDto.Evidence.builder()
                .documentLabel(toBusiness ? "사업자등록증" : "폐업사실증명원")
                .fileName(request.getEvidenceFileName())
                .fileSizeBytes(request.getEvidenceFileSize())
                .extension(extension(request.getEvidenceFileName()))
                .fileUrl(request.getEvidenceFileUrl())
                .uploadedAt(request.getRequestedAt())
                .build();

        List<AdminChangeRequestDto.ReferenceItem> references = new ArrayList<>();
        if (!toBusiness) {
            ChangeRequestItem rrn = item(request, ChangeRequestField.CREATOR_RESIDENT_NUMBER);
            references.add(reference("주민등록번호", rrn == null ? null : rrn.getRequestedValue()));
        }
        references.add(reference("정산 계좌 예금주", holder));

        String ruleAfter = toBusiness ? "REPRESENTATIVE_NAME" : "REAL_NAME";
        AdminChangeRequestDto.HolderCheck holderCheck = AdminChangeRequestDto.HolderCheck.builder()
                .currentHolder(holder)
                .rule(ruleAfter)
                .mismatch(false)
                .build();

        List<AdminChangeRequestDto.CompareItem> compare = new ArrayList<>();
        if (toBusiness) {
            ChangeRequestItem brn = item(request, ChangeRequestField.CREATOR_BUSINESS_REG_NUMBER);
            compare.add(compare("REPRESENTATIVE_NAME", "대표자명", holder, null));
            compare.add(compare("BUSINESS_REG_NUMBER", "사업자등록번호", brn == null ? null : brn.getRequestedValue(), null));
            compare.add(AdminChangeRequestDto.CompareItem.builder().key("OPENING_DATE").label("개업일")
                    .mustBeBefore(request.getRequestedAt()).build());
        } else {
            compare.add(compare("REPRESENTATIVE_NAME", "대표자 성명", holder, null));
            compare.add(compare("BIRTHDAY", "생년월일", birthday,
                    pending ? birthdayCheck(request.getResidentNumberEnc(), creator.getBirthday()) : null));
            compare.add(compare("BUSINESS_REG_NUMBER", "사업자등록번호", creator.getBusinessRegistrationNumber(), null));
            compare.add(AdminChangeRequestDto.CompareItem.builder().key("CLOSURE_DATE").label("폐업일")
                    .mustBeBefore(request.getRequestedAt()).build());
        }

        AdminChangeRequestDto.ApprovalImpact impact = pending ? AdminChangeRequestDto.ApprovalImpact.builder()
                .appliesFrom("NEXT_SETTLEMENT")
                .payoutMethodBefore(WithholdingType.from(request.getType().sourceBusinessType()).name())
                .payoutMethodAfter(WithholdingType.from(request.getType().targetBusinessType()).name())
                .retainedSettlements(creator.getBusinessType() == null ? List.of()
                        : settlementRepository.findRetainedForBusinessTypeChange(creator.getId(), creator.getBusinessType())
                        .stream()
                        .map(s -> AdminChangeRequestDto.RetainedSettlement.builder()
                                .settlementId(s.getId())
                                .settlementNumber(s.getSettlementNumber())
                                .status(s.getStatus().name())
                                .build())
                        .toList())
                .accountChangeRequired(false)
                .accountHolderRuleAfter(ruleAfter)
                .build() : null;

        return new ChangeRequestDetailParts(null, null, null, diff, changedFieldLabels, evidence, references, holderCheck,
                compare, impact);
    }

    /**
     * 인플루언서 정산 계좌 변경(1021 기-22 · 어드민 C4b) — 은행 · 계좌번호(확인 중에는 전체 노출) · 예금주 대조. 예금주 기준은 본인 인증 실명이다.
     * 처리가 끝나면 계좌번호 원문(암호문)이 비워져 요청 행의 마스킹 값만 남는다.
     */
    private ChangeRequestDetailParts assembleAccount(ChangeRequest request) {
        Creator creator = request.getCreator();
        boolean pending = request.isPending();
        ChangeRequestItem bank = item(request, ChangeRequestField.CREATOR_ACCOUNT_BANK);
        ChangeRequestItem number = item(request, ChangeRequestField.CREATOR_ACCOUNT_NUMBER);
        String currentNumber = pending ? creator.getAccountNumber() : AdminMemberMasker.maskAccountNumber(creator.getAccountNumber());
        String requestedNumber = pending && request.getAccountNumberEnc() != null
                ? cipher.decrypt(request.getAccountNumberEnc()) : number == null ? null : number.getRequestedValue();
        String realName = pending ? creator.getRealName() : AdminMemberMasker.maskName(creator.getRealName());
        String holder = pending ? request.getAccountHolderName() : AdminMemberMasker.maskName(request.getAccountHolderName());

        List<AdminChangeRequestDto.DiffRow> diff = new ArrayList<>();
        diff.add(row("BANK", "은행", bank == null ? creator.getBankName() : bank.getCurrentValue(),
                bank == null ? null : bank.getRequestedValue(), true, "REQUEST"));
        diff.add(row("ACCOUNT_NUMBER", "계좌번호", currentNumber, requestedNumber, true, "REQUEST"));
        diff.add(row("HOLDER", "예금주", realName, holder, false, "CHECK"));

        List<String> changedFieldLabels = request.getItems().stream()
                .sorted(Comparator.comparing(ChangeRequestItem::getSortOrder))
                .map(i -> i.getFieldKey().getLabel())
                .toList();
        AdminChangeRequestDto.Evidence evidence = AdminChangeRequestDto.Evidence.builder()
                .documentLabel("통장 사본")
                .fileName(request.getEvidenceFileName())
                .fileSizeBytes(request.getEvidenceFileSize())
                .extension(extension(request.getEvidenceFileName()))
                .fileUrl(request.getEvidenceFileUrl())
                .uploadedAt(request.getRequestedAt())
                .build();
        List<AdminChangeRequestDto.ReferenceItem> references = new ArrayList<>();
        references.add(reference("본인 인증 실명", realName));
        AdminChangeRequestDto.HolderCheck holderCheck = AdminChangeRequestDto.HolderCheck.builder()
                .currentHolder(realName)
                .rule("REAL_NAME")
                .mismatch(request.getAccountHolderName() != null && creator.getRealName() != null
                        && !request.getAccountHolderName().equals(creator.getRealName()))
                .build();
        List<AdminChangeRequestDto.CompareItem> compare = new ArrayList<>();
        compare.add(compare("HOLDER", "예금주(통장 사본) = 본인 실명", realName, null));
        compare.add(compare("BANK", "은행", bank == null ? null : bank.getRequestedValue(), null));
        compare.add(compare("ACCOUNT_NUMBER", "계좌번호", requestedNumber, null));
        return new ChangeRequestDetailParts(null, null, null, diff, changedFieldLabels, evidence, references, holderCheck,
                compare, null);
    }

    /**
     * 요청 주민번호 앞 6자리 + 세기(7번째 자리) ↔ 본인확인 생년월일. 서버가 확인한 값에만 「일치」를 붙인다 — 값이 없으면 null.
     */
    private String birthdayCheck(String residentNumberEnc, String birthday) {
        String birth = digits(birthday);
        if (residentNumberEnc == null || birth == null || birth.length() != 8) {
            return null;
        }
        try {
            String rrn = digits(cipher.decrypt(residentNumberEnc));
            if (rrn == null || rrn.length() != 13) {
                return null;
            }
            String century = switch (rrn.charAt(6)) {
                case '1', '2', '5', '6' -> "19";
                case '3', '4', '7', '8' -> "20";
                case '9', '0' -> "18";
                default -> null;
            };
            if (century == null) {
                return "MISMATCH";
            }
            return (century + rrn.substring(0, 6)).equals(birth) ? "MATCH" : "MISMATCH";
        } catch (RuntimeException e) {
            log.warn("주민등록번호 생년월일 대조 실패 — 대조 없이 내린다", e);
            return null;
        }
    }

    private static String account(Creator creator) {
        String masked = AdminMemberMasker.maskAccountNumber(creator.getAccountNumber());
        if (creator.getBankName() == null) {
            return masked;
        }
        return masked == null ? creator.getBankName() : creator.getBankName() + " " + masked;
    }

    private static String businessTypeLabel(String code) {
        if (code == null) {
            return null;
        }
        try {
            return CreatorBusinessType.valueOf(code).getDescription();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private static String payoutLabel(CreatorBusinessType type) {
        return type == CreatorBusinessType.BUSINESS ? PAYOUT_BUSINESS : PAYOUT_INDIVIDUAL;
    }

    private static AdminChangeRequestDto.DiffRow row(String key, String label, String current, String requested,
                                                     boolean changed, String source) {
        return AdminChangeRequestDto.DiffRow.builder()
                .fieldKey(key).label(label).currentValue(current).requestedValue(requested)
                .changed(changed).locked(false).source(source)
                .build();
    }

    private static AdminChangeRequestDto.ReferenceItem reference(String label, String value) {
        return AdminChangeRequestDto.ReferenceItem.builder().label(label).value(value).build();
    }

    private static AdminChangeRequestDto.CompareItem compare(String key, String label, String value, String serverCheck) {
        return AdminChangeRequestDto.CompareItem.builder().key(key).label(label).value(value).serverCheck(serverCheck).build();
    }

    private static ChangeRequestItem item(ChangeRequest request, ChangeRequestField field) {
        return request.getItems().stream().filter(i -> i.getFieldKey() == field).findFirst().orElse(null);
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot == -1 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private static String digits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }
}
