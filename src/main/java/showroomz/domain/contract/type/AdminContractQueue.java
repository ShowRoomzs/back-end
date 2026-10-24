package showroomz.domain.contract.type;

/** 어드민 계약 조치 큐 — TERMINATION 은 처리 대기 해지 요청(1021 설계서 1-4). */
public enum AdminContractQueue { REVIEW, CONCLUSION, EXPIRY, RESEND, TERMINATION }
