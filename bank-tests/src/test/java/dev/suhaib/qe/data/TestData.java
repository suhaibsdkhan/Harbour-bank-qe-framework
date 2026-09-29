package dev.suhaib.qe.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Generates unique, readable test data so tests never depend on each other or on seeded rows. */
public final class TestData {

    private static final List<String> FIRST = List.of("Amara", "Liam", "Priya", "Noah", "Fatima", "Mateo", "Chloe", "Omar");
    private static final List<String> LAST = List.of("Singh", "Tremblay", "Nguyen", "Roy", "Khan", "Martin", "Chen", "Gagnon");

    private TestData() {
    }

    public static String ownerName() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return FIRST.get(r.nextInt(FIRST.size())) + " " + LAST.get(r.nextInt(LAST.size())) + " " + r.nextInt(1000, 9999);
    }

    public static BigDecimal cad(String amount) {
        return new BigDecimal(amount).setScale(2);
    }
}
