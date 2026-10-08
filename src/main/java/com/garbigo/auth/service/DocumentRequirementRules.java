package com.garbigo.auth.service;

import com.garbigo.auth.model.DocumentType;
import com.garbigo.auth.model.ServiceType;
import com.garbigo.auth.model.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DocumentRequirementRules {

    private DocumentRequirementRules() {
    }

    public record Rule(DocumentType type, boolean required) {
    }

    public static List<Rule> forApplication(VehicleType vehicle, Set<ServiceType> services) {
        boolean motorized = vehicle != null && vehicle.motorized();
        boolean vehicleUnknown = vehicle == null;
        boolean sewage = services != null && services.contains(ServiceType.SEWAGE_EXHAUSTER);

        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(DocumentType.NATIONAL_ID_FRONT, true));
        rules.add(new Rule(DocumentType.NATIONAL_ID_BACK, true));
        rules.add(new Rule(DocumentType.PASSPORT_PHOTO, true));
        rules.add(new Rule(DocumentType.TAX_CERTIFICATE, true));
        rules.add(new Rule(DocumentType.GOOD_CONDUCT_CERTIFICATE, true));
        if (motorized || vehicleUnknown) {
            rules.add(new Rule(DocumentType.DRIVING_LICENCE, motorized));
            rules.add(new Rule(DocumentType.VEHICLE_LOGBOOK, motorized));
            rules.add(new Rule(DocumentType.INSURANCE_CERTIFICATE, motorized));
        }
        rules.add(new Rule(DocumentType.NEMA_LICENCE, sewage));
        rules.add(new Rule(DocumentType.PROOF_OF_ADDRESS, false));
        rules.add(new Rule(DocumentType.OTHER, false));
        return rules;
    }

    public static Set<DocumentType> requiredTypes(VehicleType vehicle, Set<ServiceType> services) {
        return forApplication(vehicle, services).stream()
                .filter(Rule::required)
                .map(Rule::type)
                .collect(Collectors.toSet());
    }
}