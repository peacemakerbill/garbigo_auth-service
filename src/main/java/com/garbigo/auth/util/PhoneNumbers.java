package com.garbigo.auth.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public final class PhoneNumbers {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    public static String normalize(String input, String defaultCountryCode) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String region = defaultCountryCode == null || defaultCountryCode.isBlank() ? null : defaultCountryCode.trim().toUpperCase();
        try {
            Phonenumber.PhoneNumber number = UTIL.parse(input.trim(), region);
            if (!UTIL.isValidNumber(number)) {
                return null;
            }
            return UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return null;
        }
    }
}