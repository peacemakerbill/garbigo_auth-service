package com.garbigo.auth.model;

public enum IdentityDocumentType {

    NATIONAL_ID("National ID card", true),
    PASSPORT("Passport", false),
    RESIDENCE_PERMIT("Residence permit", true),
    OTHER_GOVERNMENT_ID("Other government photo ID", true);

    private final String label;
    private final boolean hasBackSide;

    IdentityDocumentType(String label, boolean hasBackSide) {
        this.label = label;
        this.hasBackSide = hasBackSide;
    }

    public String label() {
        return label;
    }

    public boolean hasBackSide() {
        return hasBackSide;
    }
}