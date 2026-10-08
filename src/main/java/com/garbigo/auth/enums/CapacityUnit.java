package com.garbigo.auth.enums;

public enum CapacityUnit {

    KILOGRAMS("Kilograms"),
    LITRES("Litres"),
    CUBIC_METRES("Cubic metres");

    private final String label;

    CapacityUnit(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}