package com.garbigo.auth.service;

import com.garbigo.auth.dto.ApplicationDtos.ProgressView;
import com.garbigo.auth.dto.ApplicationDtos.SectionProgress;
import com.garbigo.auth.model.CollectorApplication;
import com.garbigo.auth.model.CollectorApplication.ApplicationDocument;
import com.garbigo.auth.model.DocumentReviewStatus;
import com.garbigo.auth.model.DocumentType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApplicationReadiness {

    private ApplicationReadiness() {
    }

    public static ProgressView evaluate(CollectorApplication app) {
        Map<String, Boolean> sectionDone = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        int[] counts = new int[2];

        class Checker {
            void check(String section, String action, boolean done) {
                counts[1]++;
                if (done) {
                    counts[0]++;
                } else {
                    missing.add(action);
                }
                sectionDone.merge(section, done, (a, b) -> a && b);
            }
        }
        Checker c = new Checker();

        c.check("Personal details", "Choose the type of ID you will use", app.getIdType() != null);
        c.check("Personal details", "Add your ID number", filled(app.getIdNumber()));
        c.check("Personal details", "Add your date of birth", app.getDateOfBirth() != null);

        c.check("Where you live", "Choose your country", filled(app.getCountryCode()));
        c.check("Where you live", "Add your city or town", filled(app.getCity()));
        c.check("Where you live", "Add your physical address", filled(app.getPhysicalAddress()));

        boolean motorized = app.getVehicleType() != null && app.getVehicleType().motorized();
        c.check("Services and vehicle", "Choose at least one service you will offer",
                app.getServiceTypes() != null && !app.getServiceTypes().isEmpty());
        c.check("Services and vehicle", "Choose the vehicle or equipment you will use", app.getVehicleType() != null);
        if (motorized) {
            c.check("Services and vehicle", "Add your vehicle registration number", filled(app.getVehicleRegistration()));
        }
        c.check("Services and vehicle", "Add at least one area you will serve",
                app.getServiceAreas() != null && !app.getServiceAreas().isEmpty());

        c.check("Availability", "Choose the days you can work",
                app.getAvailableDays() != null && !app.getAvailableDays().isEmpty());
        c.check("Availability", "Add your start and end time",
                filled(app.getShiftStart()) && filled(app.getShiftEnd()));
        c.check("Availability", "Add your years of experience (enter 0 if you are new)",
                app.getYearsOfExperience() != null);

        c.check("Payout", "Choose how you want to be paid", app.getPayoutMethod() != null);
        c.check("Payout", "Add the name of your bank, wallet or mobile money provider", filled(app.getPayoutProvider()));
        c.check("Payout", "Add your account or mobile money number", filled(app.getPayoutAccountNumber()));
        c.check("Payout", "Add the name on the account", filled(app.getPayoutAccountName()));

        var ec = app.getEmergencyContact();
        c.check("Emergency contact", "Add an emergency contact with name, relationship and phone",
                ec != null && filled(ec.getName()) && filled(ec.getRelationship()) && filled(ec.getPhone()));

        c.check("Agreements", "Accept the terms and conditions", Boolean.TRUE.equals(app.getAcceptedTerms()));
        c.check("Agreements", "Agree to the background check", Boolean.TRUE.equals(app.getConsentToBackgroundCheck()));
        c.check("Agreements", "Confirm that your information is true", Boolean.TRUE.equals(app.getConfirmsInfoIsTrue()));

        for (var rule : DocumentRequirementRules.forApplication(app.getVehicleType(), app.getServiceTypes(), app.getIdType())) {
            if (!rule.required()) {
                continue;
            }
            DocumentType type = rule.type();
            ApplicationDocument doc = find(app, type);
            if (doc == null) {
                c.check("Documents", "Upload your " + type.label(), false);
            } else if (doc.getReviewStatus() == DocumentReviewStatus.REJECTED) {
                String reason = filled(doc.getReviewNote()) ? " (" + doc.getReviewNote() + ")" : "";
                c.check("Documents", "Replace your " + type.label() + reason, false);
            } else {
                c.check("Documents", "", true);
            }
        }

        int percent = counts[1] == 0 ? 0 : (int) Math.round(100.0 * counts[0] / counts[1]);
        List<SectionProgress> sections = sectionDone.entrySet().stream()
                .map(e -> new SectionProgress(e.getKey(), e.getValue()))
                .toList();
        return new ProgressView(percent, missing.isEmpty(), List.copyOf(missing), sections);
    }

    private static ApplicationDocument find(CollectorApplication app, DocumentType type) {
        if (app.getDocuments() == null) {
            return null;
        }
        return app.getDocuments().stream().filter(d -> d.getType() == type).findFirst().orElse(null);
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}