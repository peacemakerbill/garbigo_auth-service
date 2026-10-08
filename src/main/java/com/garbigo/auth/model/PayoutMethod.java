package com.garbigo.auth.model;

public enum PayoutMethod {

    MOBILE_MONEY("Mobile money"),
    BANK_ACCOUNT("Bank account"),
    DIGITAL_WALLET("Digital wallet");

    private final String label;

    PayoutMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}