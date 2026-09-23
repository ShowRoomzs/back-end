package showroomz.api.admin.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시안 B3(서명 진행중 · 서명 현황 갱신) · C3(서명 현황 저장 확인) · 재발송 처리 기록 · C5(만료 처리).
 *
 * <p>이 화면이 존재하는 이유는 양측 화면의 「확인한 시점 기준 · N 기준」을 실제로 입력하는 곳이기 때문이다.
 * 기준 시각은 운영자가 저장을 누른 서버 시각이고, 서명 체크는 체결 전까지 해제할 수 있으며,
 * 상태(서명 진행중 ↔ 체결 처리 대기)는 두 서명 값의 조합에서 파생된다.
 */
@DisplayName("[통합] 어드민 계약 서명·재발송·만료 (B3·C3·C5)")
class AdminContractSigningIntegrationTest extends AdminContractTestSupport {

    /** 시안 B3 「여름 수분 세럼 공구」 — 08.13 16:40 발송 · 기한 08.21 23:55 · 아직 아무도 서명 전. */
    private Contract signing() {
        return seed(ContractStatus.SIGNING, s -> s.title("여름 수분 세럼 공구")
                .reviewRequestedAt(spec("2026-08-13T14:50")).sentAt(spec("2026-08-13T16:40"))
                .deadlineAt(spec("2026-08-21T23:55")));
    }

    // ------------------------------------------------------------------ B3 · C3

    @Test
    @DisplayName("B3: 브랜드 서명만 체크해 저장하면 서명 진행중에 머물고, 저장 시각이 기준 시각이 되어 양측에 그대로 나간다")
    void brandSignatureOnlyKeepsSigning() throws Exception {
        Contract c = signing();
        LocalDateTime brandSignedAt = spec("2026-08-14T09:12");
        LocalDateTime before = LocalDateTime.now().withNano(0);

        signatures(c, brandSignedAt, null, c.getVersion()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNING"));

        Contract saved = reload(c);
        assertThat(saved.getBrandSignedAt()).isEqualTo(brandSignedAt);
        assertThat(saved.getCreatorSignedAt()).isNull();
        assertThat(saved.getSignatureAsOf()).isAfterOrEqualTo(before).isBeforeOrEqualTo(LocalDateTime.now());

        String admin = body(detail(c).andExpect(jsonPath("$.contract.status").value("SIGNING"))
                .andExpect(jsonPath("$.stepper.signedCount").value(1))
                .andExpect(jsonPath("$.signature.asOfActorName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(true))
                .andExpect(jsonPath("$.permissions.canConclude").value(false))
                .andExpect(jsonPath("$.permissions.canUploadDocument").value(false)));
        LocalDateTime asOf = time(admin, "$.signature.asOf");

        // 양측 .s-asof = 이 저장 시각
        String seller = body(sellerDetail(c));
        assertThat(time(seller, "$.signature.asOf")).isEqualTo(asOf);
        assertThat(time(seller, "$.signature.brandSignedAt")).isEqualTo(brandSignedAt);
        String studio = body(creatorDetail(c));
        assertThat(time(studio, "$.signature.asOf")).isEqualTo(asOf);
        assertThat(time(studio, "$.signature.brandSignedAt")).isEqualTo(brandSignedAt);

        ContractHistory updated = historyOf(c).getLast();
        assertThat(updated.getEventType()).isEqualTo(ContractEventType.SIGNATURE_UPDATED);
        assertThat(updated.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        assertThat(updated.getDetail()).contains("브랜드 서명").doesNotContain("인플루언서 서명");
    }

    @Test
    @DisplayName("B3: 기준 시각은 클라이언트가 보낼 수 없다 — 과거 시각을 실어 보내도 서버 저장 시각이 쓰인다")
    void asOfIsServerTime() throws Exception {
        Contract c = signing();
        String body = """
                {"brandSignedAt":"%s","creatorSignedAt":null,"version":%d,
                 "signatureAsOf":"2026-01-01T00:00:00","asOf":"2026-01-01T00:00:00"}"""
                .formatted(spec("2026-08-14T09:12"), c.getVersion());

        mockMvc.perform(put(BASE + "/" + c.getId() + "/signatures").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        assertThat(reload(c).getSignatureAsOf()).isAfterOrEqualTo(now);
    }

    @Test
    @DisplayName("C3: 양측 서명이 모두 완료로 바뀌면 같은 저장으로 체결 처리 대기가 된다 — 체결 문서 업로드가 열린다")
    void bothSignaturesMoveToConclusionPending() throws Exception {
        Contract c = signing();
        signatures(c, spec("2026-08-14T09:12"), null, c.getVersion()).andExpect(status().isOk());

        signatures(c, spec("2026-08-14T09:12"), spec("2026-08-14T11:40"), versionOf(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"));

        detail(c).andExpect(jsonPath("$.contract.status").value("CONCLUSION_PENDING"))
                .andExpect(jsonPath("$.contract.statusLabel").value("체결 처리 대기"))
                .andExpect(jsonPath("$.stepper.signedCount").value(2))
                .andExpect(jsonPath("$.permissions.canUploadDocument").value(true))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(true))
                // 문서 2종이 아직 없다 — 버튼 비활성
                .andExpect(jsonPath("$.permissions.canConclude").value(false))
                .andExpect(jsonPath("$.permissions.canHandleResend").value(false))
                .andExpect(jsonPath("$.permissions.canExpire").value(false));
        // 인플루언서 서명 변경만 이력에 적힌다(브랜드는 변경 없음).
        assertThat(historyOf(c).getLast().getDetail()).contains("인플루언서 서명").doesNotContain("브랜드 서명");
        summary().andExpect(jsonPath("$.queues.CONCLUSION").value(1));
    }

    @Test
    @DisplayName("C3: 각자의 서명은 당사자·서명 시각으로 한 번씩, 양측 완료 확인은 체결 처리 대기로 넘어갈 때 한 번 남는다")
    void recordsEachSignatureAndBothSignedConfirmationOnce() throws Exception {
        Contract c = signing();
        LocalDateTime brandSignedAt = spec("2026-08-14T09:12");
        LocalDateTime creatorSignedAt = spec("2026-08-14T11:40");

        signatures(c, brandSignedAt, null, c.getVersion()).andExpect(status().isOk());
        // 같은 값으로 다시 저장해도 「브랜드 서명 완료」가 두 줄이 되지 않는다.
        signatures(c, brandSignedAt, null, versionOf(c)).andExpect(status().isOk());
        assertThat(historyOf(c)).filteredOn(h -> h.getEventType() == ContractEventType.BRAND_SIGNED)
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.getActorType()).isEqualTo(ContractActorType.SELLER);
                    assertThat(h.getActorDisplayName()).isEqualTo("글로우랩");
                    // 입력한 시각이 아니라 서명한 시각이다 — 스튜디오 S3a 「브랜드 서명 완료 · 08.14 09:12」.
                    assertThat(h.getOccurredAt()).isEqualTo(brandSignedAt);
                });
        // 한쪽만 서명했으면 「양측 서명 완료 확인」이 없다.
        assertThat(historyOf(c)).extracting(ContractHistory::getEventType)
                .doesNotContain(ContractEventType.CREATOR_SIGNED, ContractEventType.BOTH_SIGNED_CONFIRMED);

        signatures(c, brandSignedAt, creatorSignedAt, versionOf(c)).andExpect(status().isOk());
        // 체결 처리 대기에서 다시 저장해도 확인은 한 번이다.
        signatures(c, brandSignedAt, creatorSignedAt, versionOf(c)).andExpect(status().isOk());

        List<ContractHistory> history = historyOf(c);
        assertThat(history).filteredOn(h -> h.getEventType() == ContractEventType.CREATOR_SIGNED)
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.getActorType()).isEqualTo(ContractActorType.CREATOR);
                    assertThat(h.getOccurredAt()).isEqualTo(creatorSignedAt);
                });
        assertThat(history).filteredOn(h -> h.getEventType() == ContractEventType.BOTH_SIGNED_CONFIRMED)
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.getActorType()).isEqualTo(ContractActorType.ADMIN);
                    assertThat(h.getDetail()).isNull();
                });
        // 저장 1회 = 감사 기록 1줄은 그대로다.
        assertThat(history).filteredOn(h -> h.getEventType() == ContractEventType.SIGNATURE_UPDATED).hasSize(4);
        assertThat(history.getLast().getEventType()).isEqualTo(ContractEventType.SIGNATURE_UPDATED);
    }

    @Test
    @DisplayName("B4: 체결 전까지는 체크를 해제할 수 있다 — 체결 처리 대기에서 한쪽을 지우면 서명 진행중으로 내려간다")
    void signaturesAreReversibleUntilConclusion() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);

        signatures(c, c.getBrandSignedAt(), null, c.getVersion()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNING"));
        Contract saved = reload(c);
        assertThat(saved.getCreatorSignedAt()).isNull();
        assertThat(saved.getBrandSignedAt()).isEqualTo(c.getBrandSignedAt());

        signatures(c, null, null, saved.getVersion()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNING"));
        assertThat(reload(c).getBrandSignedAt()).isNull();
        detail(c).andExpect(jsonPath("$.stepper.signedCount").value(0));
    }

    @Test
    @DisplayName("C3: 값이 바뀌지 않아도 저장할 수 있다 — 기준 시각만 새로워지고 이력에 「변경 없음」이 남는다")
    void saveWithoutChangeRefreshesAsOf() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.brandSignedAt(now.minusHours(5)).asOf(now.minusHours(4)));

        signatures(c, c.getBrandSignedAt(), null, c.getVersion()).andExpect(status().isOk());

        Contract saved = reload(c);
        assertThat(saved.getSignatureAsOf()).isAfter(c.getSignatureAsOf());
        assertThat(saved.getBrandSignedAt()).isEqualTo(c.getBrandSignedAt());
        assertThat(historyOf(c).getLast().getDetail()).contains("변경 없음");
    }

    @Test
    @DisplayName("B3 입력 검증: 미래 서명 · 발송 전 서명은 400 · 두 서명 간 순서와 기한 초과 서명은 막지 않는다")
    void validatesSignatureTimes() throws Exception {
        Contract c = signing();
        LocalDateTime sentAt = c.getSignatureRequestedAt();

        signatures(c, now.plusMinutes(10), null, c.getVersion()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_SIGNATURE_TIME_INVALID"));
        signatures(c, null, sentAt.minusMinutes(1), c.getVersion()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_SIGNATURE_TIME_INVALID"));
        assertThat(reload(c).getSignatureAsOf()).isNull();

        // 서명에 순서가 없다 — 인플루언서가 먼저 서명해도 된다.
        signatures(c, sentAt.plusHours(3), sentAt.plusHours(1), c.getVersion()).andExpect(status().isOk());

        // 기한이 지난 뒤 들어온 서명도 사실대로 옮겨 적을 수 있다.
        Contract late = seed(ContractStatus.SIGNING, s -> s.sentAt(now.minusDays(9)).deadlineAt(now.minusDays(2)));
        signatures(late, now.minusDays(1), null, late.getVersion()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("동시 편집 방어: version이 없으면 400, 낡은 version이면 409 · 응답 version으로 이어서 저장한다")
    void signatureUpdatesAreVersioned() throws Exception {
        Contract c = signing();

        mockMvc.perform(put(BASE + "/" + c.getId() + "/signatures").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandSignedAt\":null,\"creatorSignedAt\":null}"))
                .andExpect(status().isBadRequest());

        String first = body(signatures(c, spec("2026-08-14T09:12"), null, c.getVersion()).andExpect(status().isOk()));
        // 다른 운영자가 옛 화면에서 저장 — 먼저 저장된 값을 덮지 못한다.
        signatures(c, null, null, c.getVersion()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_MODIFIED_ELSEWHERE"));
        assertThat(reload(c).getBrandSignedAt()).isNotNull();

        signatures(c, null, null, readLong(first, "$.version")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명 갱신은 서명 진행중·체결 처리 대기에서만 — 체결완료는 잠금(409 SIGNATURE_UPDATE_LOCKED)")
    void signatureUpdateOnlyWhileSigning() throws Exception {
        for (ContractStatus status : new ContractStatus[]{ContractStatus.REVIEW_PENDING, ContractStatus.REVIEW_REJECTED,
                ContractStatus.EXPIRED, ContractStatus.CANCELED, ContractStatus.DECLINED}) {
            Contract c = seed(status);
            signatures(c, null, null, c.getVersion()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
            detail(c).andExpect(jsonPath("$.permissions.canUpdateSignature").value(false));
        }
        Contract concluded = seed(ContractStatus.CONCLUDED);
        signatures(concluded, null, null, concluded.getVersion()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_SIGNATURE_UPDATE_LOCKED"));
        Contract saved = reload(concluded);
        assertThat(saved.getBrandSignedAt()).isNotNull();
        assertThat(saved.getCreatorSignedAt()).isNotNull();
    }

    // ------------------------------------------------------------------ 재발송

    @Test
    @DisplayName("재발송: 인플루언서의 [서명 안내 다시 받기]가 B3 안내 블록과 재발송 큐에 도착하고, [재발송 처리 기록]으로 닫힌다")
    void resendRequestArrivesAndIsHandled() throws Exception {
        Contract c = signing();
        LocalDateTime deadline = c.getSignatureDeadlineAt();
        LocalDateTime sentAt = c.getSignatureRequestedAt();

        mockMvc.perform(post(CREATOR_CONTRACTS + "/" + c.getId() + "/resend-request")
                .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isOk());

        detail(c).andExpect(jsonPath("$.resend.pendingCount").value(1))
                .andExpect(jsonPath("$.resend.lastRequesterType").value("CREATOR"))
                .andExpect(jsonPath("$.resend.lastRequestedAt").exists())
                .andExpect(jsonPath("$.permissions.canHandleResend").value(true));
        list("queue", "RESEND").andExpect(jsonPath("$.content[0].contractId").value(c.getId()));
        summary().andExpect(jsonPath("$.queues.RESEND").value(1));
        assertThat(historyOf(c).getLast().getEventType()).isEqualTo(ContractEventType.RESEND_REQUESTED);
        assertThat(historyOf(c).getLast().getActorDisplayName()).isEqualTo("뷰티_하윤");

        handleResend(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNING"));

        detail(c).andExpect(jsonPath("$.resend.pendingCount").value(0));
        summary().andExpect(jsonPath("$.queues.RESEND").value(0));
        ContractHistory handled = historyOf(c).getLast();
        assertThat(handled.getEventType()).isEqualTo(ContractEventType.RESEND_HANDLED);
        assertThat(handled.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        assertThat(resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId()))
                .allSatisfy(r -> assertThat(r.getHandledBy()).isEqualTo(admin.getId()));
        // 재발송은 기록만 한다 — 상태·발송 일시·서명 기한은 그대로다.
        Contract saved = reload(c);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(saved.getSignatureRequestedAt()).isEqualTo(sentAt);
        assertThat(saved.getSignatureDeadlineAt()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("재발송: 브랜드·인플루언서가 각각 요청해도 한 번의 처리로 둘 다 닫힌다 · 요청 없이 자발적으로 재발송해도 기록된다")
    void resendHandlesAllAndAllowsVoluntary() throws Exception {
        Contract c = signing();
        resendRequested(c, ContractActorType.SELLER, now.minusHours(2));
        resendRequested(c, ContractActorType.CREATOR, now.minusHours(1));
        detail(c).andExpect(jsonPath("$.resend.pendingCount").value(2))
                .andExpect(jsonPath("$.resend.lastRequesterType").value("CREATOR"));

        handleResend(c).andExpect(status().isOk());
        detail(c).andExpect(jsonPath("$.resend.pendingCount").value(0));
        assertThat(historyOf(c).getLast().getDetail()).contains("2건");

        Contract voluntary = signing();
        handleResend(voluntary).andExpect(status().isOk());
        assertThat(historyOf(voluntary).getLast().getEventType()).isEqualTo(ContractEventType.RESEND_HANDLED);
    }

    @Test
    @DisplayName("재발송 기록은 서명 진행중에서만 — 그 외 상태는 409")
    void resendOnlyWhileSigning() throws Exception {
        for (ContractStatus status : new ContractStatus[]{ContractStatus.REVIEW_PENDING, ContractStatus.CONCLUSION_PENDING,
                ContractStatus.CONCLUDED, ContractStatus.EXPIRED}) {
            Contract c = seed(status);
            handleResend(c).andExpect(status().isConflict());
            detail(c).andExpect(jsonPath("$.permissions.canHandleResend").value(false));
        }
    }

    // ------------------------------------------------------------------ C5

    @Test
    @DisplayName("C5: 기한 전에는 만료 버튼이 비활성이고 서버도 409로 막는다")
    void cannotExpireBeforeDeadline() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.deadlineAt(now.plusHours(1)));

        detail(c).andExpect(jsonPath("$.permissions.canExpire").value(false))
                .andExpect(jsonPath("$.signature.deadlinePassedDays").value(0));
        expire(c, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_EXPIRE_NOT_DUE"));
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.SIGNING);
    }

    @Test
    @DisplayName("C5: 기한이 지나도 시스템이 닫지 않는다 — 만료 확인 큐에 뜨고 경과일을 보여 준다")
    void overdueContractWaitsForOperator() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.title("수분 크림 겨울 공구").sentAt(now.minusDays(27))
                .deadlineAt(now.minusDays(23).minusMinutes(5)).brandSignedAt(now.minusDays(25)).asOf(now.minusDays(24)));

        detail(c).andExpect(jsonPath("$.contract.status").value("SIGNING"))
                .andExpect(jsonPath("$.permissions.canExpire").value(true))
                .andExpect(jsonPath("$.signature.deadlinePassedDays").value(23))
                .andExpect(jsonPath("$.signature.creatorSignedAt").doesNotExist());
        summary().andExpect(jsonPath("$.queues.EXPIRY").value(1));
    }

    @Test
    @DisplayName("C5: 대시보드 재확인 체크 없이는 만료할 수 없다(400)")
    void expiryRequiresDashboardRecheck() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.sentAt(now.minusDays(9)).deadlineAt(now.minusDays(2)));

        expire(c, false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));
        expire(c, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(historyOf(c)).isEmpty();
    }

    @Test
    @DisplayName("C5 만료: 한쪽만 서명한 건도 만료된다 · 종결=중립 · 사유 없음 · 처리자 실명 · 양측 통지 · 큐에서 내려간다")
    void expiresOneSidedContract() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.sentAt(now.minusDays(27))
                .deadlineAt(now.minusDays(23)).brandSignedAt(now.minusDays(25)).asOf(now.minusDays(24)));

        expire(c, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));

        Contract saved = reload(c);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.EXPIRED);
        assertThat(saved.getClosedAt()).isAfterOrEqualTo(now);
        assertThat(saved.getCloseActorType()).isEqualTo(ContractActorType.ADMIN);
        assertThat(saved.getCloseReasonCode()).isNull();
        assertThat(saved.getBrandSignedAt()).isNotNull();

        detail(c).andExpect(jsonPath("$.contract.statusLabel").value("만료"))
                .andExpect(jsonPath("$.contract.statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.closure.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.permissions.canExpire").value(false))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(false))
                .andExpect(jsonPath("$.permissions.canHandleResend").value(false));
        ContractHistory expired = historyOf(c).getLast();
        assertThat(expired.getEventType()).isEqualTo(ContractEventType.EXPIRED);
        assertThat(expired.getActorDisplayName()).isEqualTo(OPERATOR_NAME);

        summary().andExpect(jsonPath("$.queues.EXPIRY").value(0)).andExpect(jsonPath("$.tabCounts.CLOSED").value(1));
        sellerDetail(c).andExpect(jsonPath("$.status").value("EXPIRED"));
        creatorDetail(c).andExpect(jsonPath("$.status").value("EXPIRED"));
        verify(notifier).notifyBothParties(argThat(x -> x.getId().equals(c.getId())), eq("EXPIRED"));

        // 만료는 종결이다 — 되돌리거나 다시 만료시키는 경로가 없다.
        expire(c, true).andExpect(status().isConflict());
        signatures(c, null, null, saved.getVersion()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("C5: 양측이 서명한 건(체결 처리 대기)은 기한이 지났어도 만료할 수 없다 — 성립한 계약을 닫는 사고를 상태로 막는다")
    void cannotExpireSignedContract() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.sentAt(now.minusDays(9)).deadlineAt(now.minusDays(2))
                .brandSignedAt(now.minusDays(3)).creatorSignedAt(now.minusDays(1)).asOf(now.minusHours(5)));

        detail(c).andExpect(jsonPath("$.permissions.canExpire").value(false));
        expire(c, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
        summary().andExpect(jsonPath("$.queues.EXPIRY").value(0));
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.CONCLUSION_PENDING);
    }
}
