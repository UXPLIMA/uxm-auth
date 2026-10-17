package com.uxplima.auth.adapter.outbound;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmlib.health.HealthCheck;
import com.uxplima.uxmlib.health.HealthResult;

/**
 * The things that can be wrong with this plugin, each answering in one line.
 *
 * <p>Everything this plugin could not read at boot went to the console and nowhere else. An operator who
 * was not watching it, or who is not the person with access to it, had no way to ask again, and the only
 * way to find out was to notice.
 *
 * <p>Each check takes what it reads as a supplier rather than the object it reads from, so the checks are
 * this plugin's own rules and the wiring stays in the composition root. Every one is read only, and a
 * storage check opens a connection, which is why the whole run is on the async lane.
 */
public final class PluginHealth {

    private PluginHealth() {}

    /**
     * How many language files were found.
     *
     * <p>None is a failure rather than a warning: every line a player reads would then be the default the code
     * carries, in one language, whatever the operator wrote.
     */
    public static HealthCheck languages(IntSupplier found) {
        Objects.requireNonNull(found, "found");
        return check("languages", () -> {
            int count = found.getAsInt();
            return count == 0
                    ? HealthResult.fail("no language file was found: every line is the default the code carries")
                    : HealthResult.ok(count + " found");
        });
    }

    /**
     * Whether the database answers.
     *
     * <p>A failure, never a warning: without it nobody registers and nobody logs in, so every player is held
     * at the door.
     */
    public static HealthCheck storage(BooleanSupplier answers) {
        Objects.requireNonNull(answers, "answers");
        return check(
                "storage",
                () -> answers.getAsBoolean()
                        ? HealthResult.ok("the database answered")
                        : HealthResult.fail("the database did not answer: nobody can register or log in"));
    }

    private static HealthCheck check(String name, Supplier<HealthResult> answer) {
        return new HealthCheck() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthResult check() {
                return answer.get();
            }
        };
    }
}
