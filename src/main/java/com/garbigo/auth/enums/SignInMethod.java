package com.garbigo.auth.enums;

public enum SignInMethod {
    PASSWORD("Email and password"),
    GOOGLE("Google"),
    FACEBOOK("Facebook"),
    GITHUB("GitHub");

    private final String label;

    SignInMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
