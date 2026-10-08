package com.garbigo.auth.enums;

public enum VehicleType {

    HANDCART("Handcart or on foot", false),
    MOTORBIKE("Motorbike", true),
    TUKTUK("Tuk tuk", true),
    PICKUP_TRUCK("Pickup truck", true),
    LORRY("Lorry or garbage truck", true),
    EXHAUSTER_TRUCK("Sewage exhauster truck", true);

    private final String label;
    private final boolean motorized;

    VehicleType(String label, boolean motorized) {
        this.label = label;
        this.motorized = motorized;
    }

    public String label() {
        return label;
    }

    public boolean motorized() {
        return motorized;
    }
}