package com.uxplima.auth.paper.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.core.domain.properties.HasParameterTypes.Predicates.rawParameterTypes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The four practices this repository's own rules forbid, none of which had a guard.
 *
 * <p>{@code CLAUDE.md} names five in one line: {@code Future.get()} on a server thread,
 * {@code new Thread(...)}, an empty {@code catch}, {@code printStackTrace()}, and SQL built by string
 * concatenation. Two already had one. The thread is guarded by {@code ArchitectureTest}, and the empty
 * catch by the compiler: Error Prone's {@code [EmptyCatch]} refuses one under {@code -Werror}, so a second
 * guard here would be two things to maintain for one rule. The other three were checked by hand on
 * 2026-09-10, found clean in all five plugins, and nothing kept them that way.
 *
 * <p>Two are asked of the bytecode and one of the source, because a concatenated string is gone by the
 * time the class file exists.
 */
@AnalyzeClasses(packages = "com.uxplima.auth", importOptions = ImportOption.DoNotIncludeTests.class)
final class ForbiddenPracticesTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /**
     * A stack trace printed to the console rather than logged.
     *
     * <p>It goes to standard error with no logger name, no level and no plugin in front of it, so an
     * operator reading a console cannot tell whose it is, and a server that routes its log somewhere never
     * sees it at all. Every throw we swallow has a logger to hand.
     */
    @ArchTest
    static final ArchRule nothingPrintsAStackTrace = noClasses()
            .should()
            .callMethodWhere(target(nameMatching("printStackTrace")))
            .because("a stack trace on standard error carries no plugin name and no level: log it");

    /**
     * Waiting on a future with no end to the wait.
     *
     * <p>{@code get()} and {@code join()} block until the work finishes, however long that is. On a thread
     * that ticks a region that is the region frozen for as long as the other end takes, and if the other
     * end is waiting on this one it is frozen forever. Nothing needs an unbounded wait: work that has to
     * finish before something else is chained onto the future.
     *
     * <p>The timed {@code get(long, TimeUnit)} is a different call and is allowed. It is how this estate
     * hops a question onto the thread that owns a player and takes the answer back, and it is the shape
     * the classes that use it document: the caller is on the async lane, the wait is bounded, and the
     * timeout is the answer when the other thread does not come back.
     */
    @ArchTest
    static final ArchRule nothingWaitsWithoutAnEnd = noClasses()
            .should()
            .callMethodWhere(target(nameMatching("get|join"))
                    .and(target(owner(nameMatching("java\\.util\\.concurrent\\.(Future|CompletableFuture)"))))
                    .and(target(rawParameterTypes(new Class<?>[0]))))
            .because("an unbounded wait on a region thread is that region frozen for as long as it takes");

    /**
     * A statement that adds something to a string holding SQL.
     *
     * <p>What is refused is a value added to a query, because a value can rewrite the query it is added
     * to. A table name is not a value: every store here writes {@code "SELECT ... FROM " + TABLE + " WHERE
     * id = ?"}, where {@code TABLE} is a constant of that class and every value the query reads is bound
     * with a question mark. So the concatenation is found first and then asked what it is adding, and a
     * constant is allowed by its name being the shape a constant's is.
     *
     * <p>A query written as one literal is fine however long it is, and a message that happens to contain
     * the word {@code from} is not a query. The clause words are matched in upper case only for that
     * reason: uxmBackup refuses a move with "something already stands where the old data would go: " and
     * the word {@code where} in that sentence had this guard calling a file path an injection. Every
     * statement the estate writes is upper case, and a statement verb is still matched either way.
     */
    private static final Pattern SQL_CONCATENATION = Pattern.compile("\"[^\"]*(?:\\b(?i:select|insert into"
            + "|update|delete from)\\b|\\b(?:WHERE|VALUES|FROM|SET)\\b)[^\"]*\"\\s*\\+\\s*([\\w.\"]+)");

    /** Whether what is being added is a compile time constant, which a table name is and a value is not. */
    private static final Pattern A_CONSTANT = Pattern.compile("(?:\\w+\\.)*[A-Z][A-Z0-9_]*");

    /**
     * A name this file gives a table.
     *
     * <p>Three ways a table name arrives, and every store here uses one of them: assigned from anything
     * that names a table, walked out of a constant list of them, or written as a literal. A table name
     * cannot be bound with a question mark, so it is the one thing that has to be added to a query, and
     * these are the shapes in which it is. The first shape reads {@code table} as well as {@code tables},
     * because a dump quotes one name at a time: {@code String quoted = quoteName(table, engine)}.
     */
    private static final List<Pattern> A_TABLE_NAME = List.of(
            Pattern.compile("(?:this\\.)?(\\w+)\\s*=\\s*[^;]*\\btables?\\b[^;]*;"),
            Pattern.compile("for\\s*\\(\\s*(?:final\\s+)?String\\s+(\\w+)\\s*:\\s*[A-Z][A-Z0-9_]*\\s*\\)"),
            Pattern.compile("for\\s*\\(\\s*(?:final\\s+)?String\\s+(\\w+)\\s*:\\s*[^)]*\\btables?\\b[^)]*\\)"),
            Pattern.compile("(?:private|static|final)[\\w .]*\\s(\\w+)\\s*=\\s*\""));

    /**
     * A name assigned from a choice between string literals.
     *
     * <p>{@code String where = direction.isPresent() ? " AND direction = ?" : ""} is a clause chosen by the
     * code and written by the code: both halves are literals and neither can hold anything anybody typed.
     * It is how every store here writes an optional clause, and the alternative is two whole statements.
     * Both arms are read rather than assumed, so a choice between a literal and a value is still a value.
     */
    private static final Pattern A_CHOICE_OF_LITERALS =
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*([^;]*\\?[^;]*:[^;]*);");

    /**
     * The compositions this plugin makes into a query that are not values.
     *
     * <p>Named one at a time with the reason, because the shape is indistinguishable from the dangerous
     * one to anything short of following the data. {@code String.join} in uxmCrates' opening log joins a
     * list every element of which is a literal written three lines above it, and the method that builds it
     * says so in its own comment: a value never reaches that string, it reaches the statement as a bound
     * parameter beside it.
     *
     * <p>A test below fails if a name here stops appearing beside a query, so an exemption cannot outlive
     * the thing it was written for.
     */
    private static final java.util.Set<String> COMPOSED = java.util.Set.of();

    /**
     * The parameter names this plugin's stores give a table or a column.
     *
     * <p>A table name cannot be bound with a question mark, so it is the one thing a statement has to be
     * built with, and a store that deletes from six tables takes it as a parameter rather than writing the
     * same method six times. A regex cannot follow a parameter to its call sites, so each name is listed
     * here, one at a time, by the plugin whose stores take it. Read the callers before adding one: every
     * caller must pass a name off the plugin's own {@code Tables} or a literal, and never anything a player
     * wrote.
     *
     * <p>The test below fails when a name here stops being a parameter of anything, so an exemption cannot
     * outlive its reason.
     */
    private static final java.util.Set<String> TABLE_PARAMETERS = java.util.Set.of();

    /** A parameter declared {@code String <name>}, which is how a table name reaches a private helper. */
    private static final Pattern A_STRING_PARAMETER = Pattern.compile("\\bString\\s+(\\w+)\\s*[,)]");

    /**
     * A name assigned from a {@code switch} whose every arm is a string literal.
     *
     * <p>A column name cannot be bound either, and the honest way to pick one is a switch over an enum with
     * a literal in each arm: nothing a player wrote can come out of it. The arms are checked rather than
     * assumed, so a switch that yields a value is not exempt.
     */
    private static final Pattern A_SWITCH_OF_LITERALS =
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*switch\\s*\\([^)]*\\)\\s*\\{([^}]*)\\}");

    /** An arm of a switch that yields something other than a string literal. */
    private static final Pattern AN_ARM = Pattern.compile("->\\s*([^;\\n]+)");

    @Test
    @DisplayName("no SQL is built by concatenation, because that is where an injection gets in")
    void nosqlIsConcatenated() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : sources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher added = SQL_CONCATENATION.matcher(text);
            java.util.Set<String> fields = tableNamesIn(text);
            while (added.find()) {
                String what = added.group(1);
                if (isNotAValue(what, fields)) {
                    continue;
                }
                offenders.add(
                        SOURCE.relativize(file) + ":" + lineOf(text, added.start()) + " adds " + what + " to a query");
            }
        }

        assertThat(offenders)
                .describedAs("A query with a value added to it is a query a value can rewrite. Bind the"
                        + " value with a parameter, and keep the statement one literal.")
                .isEmpty();
    }

    @Test
    @DisplayName("the query guard sees what it is for, so it cannot pass by matching nothing")
    void thequeryGuardCanFail() {
        assertThat(addedToAQuery("\"select * from jobs where id = \" + id")).containsExactly("id");
        assertThat(addedToAQuery("\"insert into paid values (?, ?)\"")).isEmpty();
        assertThat(addedToAQuery("\"the job \" + id + \" was read from the file\""))
                .isEmpty();
        assertThat(addedToAQuery("\"SELECT amount FROM \" + BALANCE + \" WHERE player_id = ?\""))
                .describedAs("a table name is a constant of the class and not a value a player wrote")
                .isEmpty();
        assertThat(addedToAQuery("\"DELETE FROM \" + Tables.BOOSTER + \" WHERE id = ?\""))
                .isEmpty();
        assertThat(addedToAQuery("String table = tables.points();\n\"DELETE FROM \" + table + \" WHERE id = ?\""))
                .describedAs("a table name cannot be bound, so it is the one thing a query is built with")
                .isEmpty();
        assertThat(addedToAQuery("for (String table : PLAYER_TABLES) {\n\"DELETE FROM \" + table + \" WHERE p = ?\""))
                .isEmpty();
        assertThat(addedToAQuery("String skill = row.skill();\n\"DELETE FROM t WHERE name = \" + skill"))
                .describedAs("a name that did not come from the tables is a value")
                .containsExactly("skill");
        assertThat(addedToAQuery("String column = switch (level) { case A -> \"material\"; case B -> \"hash\"; };\n"
                        + "\"SELECT * FROM t WHERE \" + column + \" = ?\""))
                .describedAs("a column picked by a switch of literals is not a value")
                .isEmpty();
        assertThat(addedToAQuery("String column = switch (level) { case A -> row.name(); };\n"
                        + "\"SELECT * FROM t WHERE \" + column + \" = ?\""))
                .describedAs("a switch that yields a value is still a value")
                .containsExactly("column");
        assertThat(addedToAQuery("throw new IOException(\"something already stands where it would go: \" + aside)"))
                .describedAs("a sentence with the word where in it is a sentence")
                .isEmpty();
        assertThat(addedToAQuery("String quoted = quoteName(table, engine);\n"
                        + "statement.executeQuery(\"SELECT * FROM \" + quoted)"))
                .describedAs("a name quoted off a table is a table name")
                .isEmpty();
        assertThat(addedToAQuery("String where = asked.isPresent() ? \" AND direction = ?\" : \"\";\n"
                        + "\"SELECT * FROM t WHERE player = ?\" + where + \" LIMIT ?\""))
                .describedAs("a clause chosen between two literals is written by the code")
                .isEmpty();
        assertThat(addedToAQuery("String where = asked.isPresent() ? row.clause() : \"\";\n"
                        + "\"SELECT * FROM t WHERE player = ?\" + where"))
                .describedAs("a choice with a value in one half is a value")
                .containsExactly("where");
        assertThat(addedToAQuery("for (String table : List.of(tables.task(), tables.board())) {\n"
                        + "\"DELETE FROM \" + table + \" WHERE player = ?\""))
                .describedAs("a walk over a list of tables is a table name")
                .isEmpty();
    }

    /**
     * Whether what is added to a query is something other than a value.
     *
     * <p>Four shapes are not values: a string literal, a constant, a name this file gave a table, and a
     * named composition. The third is there because a table name cannot be bound with a question mark, so
     * it is the one thing a query has to be built with; every store here takes one off the plugin's own
     * {@code Tables}, walks one out of a constant list, or writes one as a literal. A name that arrived
     * any other way is a value, which is where an injection gets in.
     */
    private static boolean isNotAValue(String what, java.util.Set<String> tableNames) {
        if (what.startsWith("\"") || A_CONSTANT.matcher(what).matches() || COMPOSED.contains(what)) {
            return true;
        }
        String head = what.contains(".") ? what.substring(0, what.indexOf('.')) : what;
        return tableNames.contains(head);
    }

    /** Every name this file gives a table, by whichever of the ways it took one. */
    private static java.util.Set<String> tableNamesIn(String text) {
        java.util.Set<String> named = new java.util.LinkedHashSet<>();
        named.add("tables");
        for (Pattern shape : A_TABLE_NAME) {
            Matcher found = shape.matcher(text);
            while (found.find()) {
                named.add(found.group(1));
            }
        }
        Matcher parameter = A_STRING_PARAMETER.matcher(text);
        while (parameter.find()) {
            if (TABLE_PARAMETERS.contains(parameter.group(1))) {
                named.add(parameter.group(1));
            }
        }
        Matcher chosen = A_SWITCH_OF_LITERALS.matcher(text);
        while (chosen.find()) {
            if (everyArmIsALiteral(chosen.group(2))) {
                named.add(chosen.group(1));
            }
        }
        Matcher either = A_CHOICE_OF_LITERALS.matcher(text);
        while (either.find()) {
            if (bothHalvesAreLiterals(either.group(2))) {
                named.add(either.group(1));
            }
        }
        return named;
    }

    /** Whether both halves of a ternary are string literals, which is what makes the name safe. */
    private static boolean bothHalvesAreLiterals(String choice) {
        int asked = choice.indexOf('?');
        int otherwise = choice.lastIndexOf(':');
        if (asked < 0 || otherwise < asked) {
            return false;
        }
        String yes = choice.substring(asked + 1, otherwise).trim();
        String no = choice.substring(otherwise + 1).trim();
        return yes.startsWith("\"") && yes.endsWith("\"") && no.startsWith("\"") && no.endsWith("\"");
    }

    /** Whether every arm of this switch body yields a string literal, which is what makes the name safe. */
    private static boolean everyArmIsALiteral(String body) {
        Matcher arm = AN_ARM.matcher(body);
        boolean any = false;
        while (arm.find()) {
            any = true;
            if (!arm.group(1).trim().startsWith("\"")) {
                return false;
            }
        }
        return any;
    }

    @Test
    @DisplayName("every named table parameter is still a parameter, so an exemption cannot outlive its reason")
    void thetableParametersAreStillThere() throws IOException {
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        for (Path file : sources()) {
            Matcher parameter = A_STRING_PARAMETER.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (parameter.find()) {
                found.add(parameter.group(1));
            }
        }
        List<String> gone =
                TABLE_PARAMETERS.stream().filter(name -> !found.contains(name)).toList();

        assertThat(gone)
                .describedAs("This name is exempt from the query guard and nothing takes it as a parameter"
                        + " any more. Take the exemption out.")
                .isEmpty();
    }

    @Test
    @DisplayName("every named composition is still beside a query, so an exemption cannot outlive its reason")
    void thecompositionsAreStillThere() throws IOException {
        List<String> gone = new ArrayList<>();

        for (String composition : COMPOSED) {
            boolean found = false;
            for (Path file : sources()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher added = SQL_CONCATENATION.matcher(text);
                while (added.find()) {
                    found = found || added.group(1).equals(composition);
                }
            }
            if (!found) {
                gone.add(composition);
            }
        }

        assertThat(gone)
                .describedAs("This composition is exempt from the query guard and nothing composes with it"
                        + " any more. Take the exemption out.")
                .isEmpty();
    }

    /** What each SQL concatenation in this text is adding, once what is not a value is dropped. */
    private static List<String> addedToAQuery(String text) {
        List<String> added = new ArrayList<>();
        java.util.Set<String> fields = tableNamesIn(text);
        Matcher found = SQL_CONCATENATION.matcher(text);
        while (found.find()) {
            if (!isNotAValue(found.group(1), fields)) {
                added.add(found.group(1));
            }
        }
        return added;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int at = 0; at < index; at++) {
            if (text.charAt(at) == '\n') {
                line++;
            }
        }
        return line;
    }
}
