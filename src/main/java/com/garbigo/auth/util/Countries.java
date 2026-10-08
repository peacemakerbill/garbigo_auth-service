package com.garbigo.auth.util;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Countries {

    public record Country(String code, String name, String dialCode) {
    }

    private static final Set<String> CODES = Arrays.stream(Locale.getISOCountries()).collect(Collectors.toSet());
    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    private static final List<Country> ALL = Arrays.stream(Locale.getISOCountries())
            .map(code -> new Country(code, Locale.of("", code).getDisplayCountry(Locale.ENGLISH), dial(code)))
            .filter(c -> !c.name().isBlank())
            .sorted(Comparator.comparing(Country::name))
            .toList();

    private Countries() {
    }

    public static List<Country> all() {
        return ALL;
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String upper = code.trim().toUpperCase(Locale.ENGLISH);
        return CODES.contains(upper) ? upper : null;
    }

    public static String nameOf(String code) {
        String normalized = normalize(code);
        return normalized == null ? null : Locale.of("", normalized).getDisplayCountry(Locale.ENGLISH);
    }

    private static String dial(String code) {
        int dial = PHONE_UTIL.getCountryCodeForRegion(code);
        return dial == 0 ? null : "+" + dial;
    }
}