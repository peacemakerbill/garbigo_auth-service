package com.garbigo.auth.security;

import com.garbigo.auth.util.EmailAddresses;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AdminEmailPolicy {

    private final Set<String> initialAdmins;
    private final Set<String> autoAdmins;
    private final Set<String> eligible;
    private final List<String> invalidEntries;

    public AdminEmailPolicy(String initialAdminEmails, String autoAdminEmails) {
        EmailAddresses.ParsedList initial = EmailAddresses.parseList(initialAdminEmails);
        EmailAddresses.ParsedList auto = EmailAddresses.parseList(autoAdminEmails);

        this.initialAdmins = Collections.unmodifiableSet(new LinkedHashSet<>(initial.valid()));
        this.autoAdmins = Collections.unmodifiableSet(new LinkedHashSet<>(auto.valid()));

        Set<String> union = new LinkedHashSet<>(initial.valid());
        union.addAll(auto.valid());
        this.eligible = Collections.unmodifiableSet(union);

        java.util.List<String> invalid = new java.util.ArrayList<>(initial.invalidMasked());
        invalid.addAll(auto.invalidMasked());
        this.invalidEntries = Collections.unmodifiableList(invalid);
    }

    public Set<String> initialAdmins() {
        return initialAdmins;
    }

    public Set<String> autoAdmins() {
        return autoAdmins;
    }

    public Set<String> eligible() {
        return eligible;
    }

    public List<String> invalidEntriesMasked() {
        return invalidEntries;
    }

    public boolean isEligible(String email) {
        String normalized = EmailAddresses.normalize(email);
        return normalized != null && eligible.contains(normalized);
    }
}
