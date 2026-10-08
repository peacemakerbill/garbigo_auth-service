package com.garbigo.auth.enums;

public enum DocumentType {

    ID_FRONT("ID document (front)",
            "The front of your ID card or residence permit, or the photo page of your passport.",
            "Place it on a flat, plain surface in good light. All four corners should be visible.",
            false, false),
    ID_BACK("ID document (back)",
            "The back of your ID card or residence permit. You do not need this for a passport.",
            "Make sure the text is sharp and not covered by your fingers.",
            false, false),
    PASSPORT_PHOTO("Passport-style photo",
            "A recent, clear photo of your face against a plain background.",
            "No hats or sunglasses. A phone selfie in daylight works well.",
            true, false),
    TAX_CERTIFICATE("Tax registration document",
            "Your tax ID certificate or a recent letter from your tax authority, if you have one.",
            "A PDF download or a clear photo of the printed copy both work.",
            false, false),
    GOOD_CONDUCT_CERTIFICATE("Police clearance certificate",
            "A certificate of good conduct or criminal record check from your country, issued within the last 12 months.",
            "Your local police or government portal issues this. Upload the PDF or a clear photo.",
            false, false),
    DRIVING_LICENCE("Driving licence",
            "A valid driving licence that covers the vehicle you will use.",
            "Upload the front and back together in one PDF, or the side with your photo.",
            false, false),
    VEHICLE_REGISTRATION("Vehicle registration document",
            "The registration certificate, logbook or equivalent document for your vehicle.",
            "The vehicle registration number must be clearly readable.",
            false, false),
    INSURANCE_CERTIFICATE("Vehicle insurance certificate",
            "A valid insurance certificate for your vehicle.",
            "Check that the expiry date is in the future.",
            false, false),
    WASTE_PERMIT("Waste transport permit",
            "The permit or licence from your local authority or environmental agency to transport waste or sewage.",
            "Required for sewage exhauster services.",
            false, false),
    PROOF_OF_ADDRESS("Proof of address",
            "A utility bill, lease, bank statement or official letter showing where you live.",
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