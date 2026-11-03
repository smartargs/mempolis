package com.smartargs.mempolis.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A city's lot registry as the deed contract takes it, exported from the city plan.
 *
 * @param lots one registry byte per lot, from lot 1: its tier (1 to 4) plus 8 times its ring
 * @param queue the standard lots in the order free claims get them
 */
public record CityPlanFile(List<Integer> lots, List<Integer> queue) {

    private static final int MAX_INFO = 127;

    /** Reads and checks the file at {@code file}. */
    public static CityPlanFile read(Path file) throws IOException {
        CityPlanFile plan = new ObjectMapper().readValue(file.toFile(), CityPlanFile.class);
        if (plan.lots() == null || plan.queue() == null) throw new IllegalArgumentException(file + " needs lots and queue.");
        for (int info : plan.lots()) {
            int tier = info & 7;
            if (info < 1 || info > MAX_INFO || tier < 1 || tier > 4) throw new IllegalArgumentException("Bad lot info " + info);
        }
        for (int lot : plan.queue()) {
            if (lot < 1 || lot > plan.lots().size() || (plan.lots().get(lot - 1) & 7) != 1) {
                throw new IllegalArgumentException("Queued lot " + lot + " is not a standard lot of the plan.");
            }
        }
        if (plan.queue().stream().distinct().count() != plan.queue().size()) {
            throw new IllegalArgumentException("The queue holds a lot twice.");
        }
        return plan;
    }
}
