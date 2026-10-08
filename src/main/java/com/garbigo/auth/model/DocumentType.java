package com.garbigo.auth.model;

public enum DocumentType {

    NATIONAL_ID_FRONT("National ID card (front)",
            "The front of your Kenyan national ID card, showing your photo and ID number.",
            "Place the card on a flat, plain surface in good light. All four corners should be visible.",
            false, false),
    NATIONAL_ID_BACK("National ID card (back)",
            "The back of your Kenyan national ID card.",
            "Make sure the text is sharp and not covered by your fingers.",
            false, false),
    PASSPORT_PHOTO("Passport photo",
            "A recent, clear photo of your face against a plain background.",
            "No hats or sunglasses. A phone selfie in daylight works well.",
            true, false),
    TAX_CERTIFICATE("KRA PIN certificate",
            "Your Kenya Revenue Authority PIN certificate (tax compliance certificate).",
            "Download it from the iTax portal as a PDF, or upload a clear photo of the printed copy.",
            false, false),
    GOOD_CONDUCT_CERTIFICATE("Certificate of good conduct",
            "A police clearance certificate issued within the last 12 months.",
            "You can apply for it on the eCitizen portal. Upload the PDF or a clear photo.",
            false, false),
    DRIVING_LICENCE("Driving licence",
            "A valid driving licence that covers the vehicle you will use.",
            "Upload the front and back together in one PDF, or the side with your photo.",
            false, false),
    VEHICLE_LOGBOOK("Vehicle logbook",
            "The logbook or a copy of the vehicle registration for your vehicle.",
            "The vehicle registration number must be clearly readable.",
            false, false),
    INSURANCE_CERTIFICATE("Vehicle insurance certificate",
            "A valid insurance certificate for your vehicle.",
            "Check that the expiry date is in the future.",
            false, false),
    NEMA_LICENCE("NEMA or county waste transport licence",
            "Your licence to transport waste or sewage, if you have one.",
            "Required for sewage exhauster services.",
            false, false),
    PROOF_OF_ADDRESS("Proof of address",
            "A utility bill, lease, or letter from your area chief showing where you live.",
            "It should be dated within the last 3 months.",
            false, false),
    OTHER("Other supporting document",
            "Anything else that helps us approve you, such as a training certificate or a recommendation letter.",
            "You can add up to 5 of these.",
            false, true);

    private final String label;
    private final String description;
    private final String tip;
    private final boolean imageOnly;
    private final boolean multiple;

    DocumentType(String label, String description, String tip, boolean imageOnly, boolean multiple) {
        this.label = label;
        this.description = description;
        this.tip = tip;
        this.imageOnly = imageOnly;
        this.multiple = multiple;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String tip() {
        return tip;
    }

    public boolean imageOnly() {
        return imageOnly;
    }

    public boolean multiple() {
        return multiple;
    }
}