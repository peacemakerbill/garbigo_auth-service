package com.garbigo.auth.enums;

public enum ServiceType {

    GENERAL_WASTE("Household and general waste"),
    RECYCLABLES("Recyclables"),
    ORGANIC_WASTE("Organic and food waste"),
    BULK_AND_CONSTRUCTION_WASTE("Bulky and construction waste"),
    SEWAGE_EXHAUSTER("Sewage exhauster");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}