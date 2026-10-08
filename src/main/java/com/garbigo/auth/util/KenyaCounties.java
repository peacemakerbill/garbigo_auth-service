package com.garbigo.auth.util;

import java.util.List;
import java.util.Optional;

public final class KenyaCounties {

    private KenyaCounties() {
    }

    public static final List<String> ALL = List.of(
            "Baringo", "Bomet", "Bungoma", "Busia", "Elgeyo-Marakwet", "Embu", "Garissa", "Homa Bay",
            "Isiolo", "Kajiado", "Kakamega", "Kericho", "Kiambu", "Kilifi", "Kirinyaga", "Kisii",
            "Kisumu", "Kitui", "Kwale", "Laikipia", "Lamu", "Machakos", "Makueni", "Mandera",
            "Marsabit", "Meru", "Migori", "Mombasa", "Murang'a", "Nairobi", "Nakuru", "Nandi",
            "Narok", "Nyamira", "Nyandarua", "Nyeri", "Samburu", "Siaya", "Taita-Taveta", "Tana River",
            "Tharaka-Nithi", "Trans Nzoia", "Turkana", "Uasin Gishu", "Vihiga", "Wajir", "West Pokot");

    public static Optional<String> canonical(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String wanted = normalize(input);
        return ALL.stream().filter(c -> normalize(c).equals(wanted)).findFirst();
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z]", "");
    }
}