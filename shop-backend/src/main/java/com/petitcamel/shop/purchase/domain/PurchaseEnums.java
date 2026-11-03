package com.petitcamel.shop.purchase.domain;

/** Enumerations of the purchase ledger, with the Korean labels used in the 매입장부 sheet. */
public final class PurchaseEnums {

    private PurchaseEnums() {
    }

    public enum KeptStatus {
        KEPT("보관"),
        NOT_KEPT("미보관"),
        NOT_APPLICABLE("해당없음");

        private final String label;

        KeptStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 적격증빙 종류. */
    public enum EvidenceType {
        NONE("없음"),
        TAX_INVOICE("세금계산서"),
        CASH_RECEIPT("현금영수증(지출증빙)"),
        CARD_SLIP("카드전표"),
        INVOICE("계산서");

        private final String label;

        EvidenceType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PaymentMethod {
        CASH("현금"),
        TRANSFER("계좌이체"),
        CARD("카드"),
        OTHER("기타");

        private final String label;

        PaymentMethod(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum VerificationStatus {
        NEEDS_REVIEW("자료 확인 필요"),
        BASIC_COMPLETE("기본자료 보관");

        private final String label;

        VerificationStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum EntrySource {
        MANUAL,
        AI
    }

    public enum EvidenceCategory {
        STATEMENT("거래명세표"),
        CASH_RECEIPT("현금영수증"),
        CARD_RECEIPT("카드영수증"),
        TRANSFER("이체내역"),
        TAX_INVOICE("세금계산서"),
        OTHER("기타");

        private final String label;

        EvidenceCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum EvidenceAiStatus {
        NOT_ANALYZED,
        ANALYZED,
        FAILED
    }

    public enum MatchStatus {
        UNMATCHED,
        MATCHED,
        CANDIDATE,
        NEW_PRODUCT,
        IGNORED
    }

    public enum ReceiptStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    public enum AllocationMethod {
        QUANTITY,
        AMOUNT
    }
}
