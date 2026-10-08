package com.garbigo.auth.model;

public enum DocumentReviewStatus {

    PENDING("Waiting for review"),
    VERIFIED("Verified"),
    REJECTED("Needs to be replaced");

    private final String label;

    DocumentReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}