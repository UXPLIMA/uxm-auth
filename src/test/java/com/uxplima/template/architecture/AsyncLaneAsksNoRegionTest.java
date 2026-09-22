package com.uxplima.template.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when work the async lane runs can ask for something only a ticking region can answer.
 *
 * <p>This is the class of defect that compiles, passes every test and throws on the first real boot. A
 * server tick belongs to the region that is ticking it: on Folia there is no server-wide tick and no main
 * thread, so {@code Server.getCurrentTick} answers on a region thread and throws
 * {@code IllegalStateException: No currently ticking region} anywhere else. The async pool is anywhere else.
 *
 * <p>uxmMinions shipped exactly that. Its index walk was posted to the async lane, which is right, because
 * it reads tables, and the tick it was walking at was read from the server inside the same closure. Every
 * pass threw before the walk began, so on Folia no minion worked at all, and 207 tests were green while it
 * was true. A unit test cannot catch it: MockBukkit answers the call, a domain that takes a {@code long}
 * has no opinion about where it came from, and the throw lives in the server rather than in anything we
 * wrote. So the guard is at the bytecode, and it is here because the rule is the estate's and not that
 * plugin's.
 *
 * <h2>The walk</h2>
 *
 * <p>It starts at every method that hands work to the async lane, which is a call on {@code Scheduler.async},
 * {@code asyncLater} or {@code asyncTimer}. The work itself
 * arrives there as a closure or as a method reference, so those are the two starting points, and both are
 * followed. From there it is transitive over our own classes, and it resolves a port to the adapters that
 * implement it, because the async lane reaches a table through two or three of our classes and never
 * directly.
 *
 * <p>Past the entry it steps over anything written inside a closure, which is the convention
 * {@code RenderReadsMemoryTest} already keeps: a closure is how this plugin says which thread it wants, and
 * the closures the walk builds are the ones it hands to {@code WorkQueue.atBlock}, which is a region thread
 * and may ask a region anything.
 */
@AnalyzeClasses(packages = "com.uxplima.template", importOptions = ImportOption.DoNotIncludeTests.class)
final class AsyncLaneAsksNoRegionTest {

    /** Where this plugin's own code lives, which is as far as the walk follows a call. */
    private static final String OURS = "com.uxplima.template.";

    /** The library's scheduler. Three of its methods put work on a thread that owns no region. */
    private static final String SCHEDULER = "com.uxplima.uxmlib.scheduler.Scheduler";

    private static final Set<String> ASYNC = Set.of("async", "asyncLater", "asyncTimer");

    /**
     * What answers only while a region ticks, by the type that declares it.
     *
     * <p>The tick counters are the ones the boot log named. The world clock is the same counter read through
     * a world: Folia regionises the game time with everything else, so a read of it off a region thread is
     * the same throw with a different stack. Nothing here is a call this plugin needs on the async lane:
     * {@code WalkClock} gives the walk a tick count that any thread may read.
     */
    private static final Map<String, Set<String>> REGION_ONLY = Map.of(
            "org.bukkit.Server",
            Set.of("getCurrentTick", "getTPS", "getTickTimes", "getAverageTickTime"),
            "org.bukkit.Bukkit",
            Set.of("getCurrentTick", "getTPS", "getTickTimes", "getAverageTickTime"),
            "org.bukkit.World",
            Set.of("getTime", "setTime", "getFullTime", "setFullTime", "getGameTime", "isDayTime"));

    /**
     * What the global region owns, so a thread that owns no region may not read it either.
     *
     * <p>The roster is the list of who is on the server, and the global region is what maintains it: a
     * player joining or leaving is written there. A read of it from the async lane is a read of a list
     * somebody else is writing, so it answers with whoever happened to be in it at that instant and it can
     * answer halfway through a join. Worse, what a caller does next is walk it and touch each player, and a
     * player is owned by the region they stand in rather than by the lane that found them.
     *
     * <p>A whole world's list is the same answer at a larger size. Every entity in it belongs to the region
     * it is standing in, so the list is only ever coherent where the regions are known, and what a caller
     * does next is read each one.
     *
     * <p>The shape that is right costs one hop: read the roster on the global lane, keep the ids, and hand
     * those to the async lane. A caller that then needs something off a player asks that player's own
     * region for it. Both are one line more than the version that is wrong.
     * <p>A command is the same. Paper runs one on the main thread and refuses one from anywhere else, and
     * Folia runs a console command on the global region. A contest's prize commands were dispatched from the
     * settlement sweep on the async lane, so the first one threw, and it threw after the result was written:
     * the places after it were never paid, and a settled contest is never settled again.
     *
     */
    private static final Map<String, Set<String>> GLOBAL_ONLY = Map.of(
            "org.bukkit.Server",
            Set.of("getOnlinePlayers", "dispatchCommand"),
            "org.bukkit.Bukkit",
            Set.of("getOnlinePlayers", "dispatchCommand"),
            "org.bukkit.World",
            Set.of("getPlayers", "getEntities", "getLivingEntities", "getEntitiesByClass"));

    /**
     * What an entity answers only on the region that is ticking it.
     *
     * <p>A player is owned by the region they are standing in, and everything about where they are and what
     * they are carrying moves with them. Reading it from the async lane reads a field another thread is
     * writing, and on Folia the server refuses outright rather than answering something stale. uxmMinions'
     * view sweep did exactly this, once per player per beat, and 388 tests were green while it was true.
     *
     * <p>What is not here is as deliberate as what is. A name, an id and a locale are cached on the object
     * and are read from anywhere. So is a message, a title and a sound: those are packets, and sending one
     * is the one thing an entity is happy to be asked for off its thread.
     *
     * <p>The shape that is right is the same hop as the roster: hand the question to
     * {@code Scheduler.entity} and take the answer back. The walk knows the difference, so the hop back
     * written inside the async closure is not read as async work.
     */
    private static final Map<String, Set<String>> ENTITY_ONLY = Map.of(
            "org.bukkit.entity.Entity",
            Set.of(
                    "getLocation",
                    "getEyeLocation",
                    "getWorld",
                    "getChunk",
                    "teleport",
                    "getVelocity",
                    "setVelocity",
                    "getNearbyEntities"),
            "org.bukkit.entity.HumanEntity",
            Set.of("getInventory", "getEquipment", "openInventory", "closeInventory", "getGameMode"),
            "org.bukkit.attribute.Attributable",
            Set.of("getAttribute"),
            "org.bukkit.entity.Damageable",
            Set.of("getHealth", "setHealth"));

    /**
     * The calls this plugin makes that hand work to the thread that owns a player or a block.
     *
     * <p>The walk reads a closure as "not on this thread" because a closure is how the estate says which
     * lane it wants. A plugin can say the same thing with a method instead: uxmAuction and uxmShop hop to
     * the region that owns a player through a helper that takes the work and waits for the answer, and
     * what that helper runs is as much on the region as anything written inside {@code Scheduler.entity}.
     * A walk that does not know the helper follows the work straight through it and calls a correct hop a
     * defect.
     *
     * <p>So the methods behind a hop are named here, {@code Class.method}, by the plugin that has one: the
     * hop itself and whatever it runs. The walk stops at a name in this set and reports nothing through it.
     * Name only a method that really runs on a region or entity lane, and read it before you name it:
     * naming anything else switches the guard off for everything that method reaches. The test below fails
     * when a name here stops being called, so a hop that is deleted or renamed cannot leave a hole behind.
     */
    private static final Set<String> REGION_HOPS = Set.of();

    /**
     * The one call that started this, forbidden outright.
     *
     * <p>The walk below is the rule. This is the same rule with no walking, and it is here because there is
     * no reason for any class of ours to ask the server what tick it is: a plugin that needs a count of
     * ticks can hold one, and a plugin that needs the time of day can ask the region that owns the block.
     */
    @ArchTest
    static final ArchRule nothingAsksTheServerForATick = noClasses()
            .should()
            .callMethodWhere(target(nameMatching("getCurrentTick"))
                    .and(target(owner(nameMatching("org\\.bukkit\\.(Server|Bukkit)")))))
            .because("a tick belongs to the region that is ticking, and WalkClock answers on any thread");

    @Test
    @DisplayName("nothing the async lane runs asks for what only a ticking region can answer")
    void theAsyncLaneAsksNoRegion() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.uxplima.template");

        Set<String> offenders = new LinkedHashSet<>();
        for (JavaClass posting : classes) {
            for (JavaCodeUnit poster : posting.getCodeUnits()) {
                List<Lane> lanes = everyLaneOf(poster);
                if (lanes.stream().noneMatch(Lane::async)) {
                    continue;
                }
                offenders.addAll(fromTheWorkPostedBy(classes, poster, lanes));
            }
        }

        assertThat(offenders.stream().filter(chain -> !isRecorded(chain)).toList())
                .describedAs("Work on the async lane runs on a thread that owns no region, so it may not ask"
                        + " for a tick, a tick time or a world clock: on Folia the call throws \"No currently"
                        + " ticking region\" and the work never runs. Count the ticks yourself, or ask the"
                        + " question on the thread that owns the block through the scheduler's region lane."
                        + " The roster is the same rule from the other side: the global region owns it, so"
                        + " read it there and hand the ids to the lane. So is a player: where they are and"
                        + " what they carry belongs to the region ticking them, so ask that region through"
                        + " the entity lane.")
                .isEmpty();
    }

    /**
     * The crossings a plugin has not closed yet, named one at a time with the reason.
     *
     * <p>An exemption here is a defect with a note beside it and not a rule with a hole in it. A hop closes
     * almost every crossing this estate has had; the one it cannot close is work that reads a player and
     * runs a query in the same breath, because moving it to the player's thread puts the query on a region
     * thread. That wants a projection read from memory, which is a change to what the work is rather than
     * to where it runs, and until it is made the crossing is written down here with the note that explains
     * it.
     *
     * <p>Name the chain's first step, {@code Class.method}. The test below fails when a name here stops
     * being reached, so an exemption cannot outlive its reason.
     */
    private static final Set<String> RECORDED = Set.of();

    /** Whether this chain starts at one of the crossings that is recorded rather than closed. */
    private static boolean isRecorded(String chain) {
        for (String start : RECORDED) {
            if (chain.startsWith(start)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("every recorded crossing is still there, so an exemption cannot outlive its reason")
    void therecordedCrossingsAreStillThere() {
        if (RECORDED.isEmpty()) {
            return;
        }
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(OURS.substring(0, OURS.length() - 1));

        Set<String> found = new LinkedHashSet<>();
        for (JavaClass posting : classes) {
            for (JavaCodeUnit poster : posting.getCodeUnits()) {
                List<Lane> lanes = everyLaneOf(poster);
                if (lanes.stream().noneMatch(Lane::async)) {
                    continue;
                }
                found.addAll(fromTheWorkPostedBy(classes, poster, lanes));
            }
        }

        List<String> gone = RECORDED.stream()
                .filter(start -> found.stream().noneMatch(chain -> chain.startsWith(start)))
                .toList();

        assertThat(gone)
                .describedAs("This crossing is excused from the walk and the walk no longer finds it."
                        + " Take the name out, and the note with it.")
                .isEmpty();
    }

    @Test
    @DisplayName("every named hop is still called, so an exemption cannot outlive the method it was written for")
    void thehopsAreStillThere() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(OURS.substring(0, OURS.length() - 1));

        Set<String> called = new LinkedHashSet<>();
        for (JavaClass owner : classes) {
            for (JavaCodeUnit unit : owner.getCodeUnits()) {
                for (JavaAccess<?> access : everyAccessOf(unit)) {
                    called.add(call(access));
                }
            }
        }

        assertThat(REGION_HOPS.stream().filter(hop -> !called.contains(hop)).toList())
                .describedAs("This hop is named as a region hop and nothing calls it any more." + " Take the name out.")
                .isEmpty();
    }

    /** One call on the scheduler, from the line it opens on to the line it closes on. */
    private record Lane(int from, int to, boolean async) {}

    /**
     * Every scheduling call this method makes, with the lines its argument list covers.
     *
     * <p>A method that posts work also does other things, and some of those are closures too: a command
     * reads the roster inside the callback that found the crate and only then posts the disk work, and the
     * composition root hands a method reference to a listener three lines under an async timer. Both of
     * those look exactly like posted work to a walk that asks only whether a method posts anything at all,
     * so it asks where instead, and the where is the call's own argument list, found by balancing brackets
     * from the line the call sits on.
     *
     * <p>Every lane is collected and not only the async ones, because the shape this estate writes most is
     * a hop out and a hop back: the query goes to the lane and the answer is handed to the thread that owns
     * the player, inside the same closure. What is written inside the hop back belongs to that thread, so
     * the innermost call around an access is the one that decides which thread it runs on.
     */
    private static List<Lane> everyLaneOf(JavaCodeUnit candidate) {
        List<Lane> lanes = new ArrayList<>();
        for (JavaAccess<?> access : everyAccessOf(candidate)) {
            String owner = access.getTargetOwner().getName();
            String called = access.getTarget().getName();
            if (!owner.equals(SCHEDULER)) {
                continue;
            }
            int[] span =
                    spanOf(candidate.getOwner(), called, access.getLineNumber()).orElse(null);
            if (span != null) {
                lanes.add(new Lane(span[0], span[1], ASYNC.contains(called)));
            }
        }
        return lanes;
    }

    /**
     * From the line a call is written on to the line its closing bracket is on.
     *
     * <p>Read out of the source, because the class file knows the line of a call and nothing about where its
     * arguments end. A class that cannot be found on disk answers nothing, which leaves the walk with no
     * span for that call rather than a wrong one.
     */
    private static Optional<int[]> spanOf(JavaClass owner, String called, int line) {
        List<String> source = sourceOf(owner);
        if (line < 1 || line > source.size()) {
            return Optional.empty();
        }
        int at = source.get(line - 1).indexOf(called + "(");
        if (at < 0) {
            return Optional.empty();
        }
        int depth = 0;
        for (int index = line - 1; index < source.size(); index++) {
            String text = source.get(index);
            int from = index == line - 1 ? at + called.length() : 0;
            for (int column = from; column < text.length(); column++) {
                char letter = text.charAt(column);
                if (letter == '(') {
                    depth++;
                } else if (letter == ')') {
                    depth--;
                    if (depth == 0) {
                        return Optional.of(new int[] {line, index + 1, at, column});
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The source of the file a class is written in, read once and kept. */
    private static List<String> sourceOf(JavaClass type) {
        String outer = type.getName().split("\\$", 2)[0];
        return SOURCES.computeIfAbsent(outer, name -> {
            Path file = Path.of("src", "main", "java", name.replace('.', '/') + ".java");
            try {
                return Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException notThere) {
                return List.of();
            }
        });
    }

    /**
     * Whether the innermost scheduling call around this access is one on the async lane.
     *
     * <p>Innermost is the one that opens last among those that contain the line, which is the closure the
     * access is actually written in. An access inside a hop back to a region, written inside the async
     * closure that posted it, therefore belongs to the region and not to the lane.
     */
    private static boolean isInside(List<Lane> lanes, JavaAccess<?> access) {
        Lane innermost = null;
        for (Lane lane : lanes) {
            if (access.getLineNumber() < lane.from() || access.getLineNumber() > lane.to()) {
                continue;
            }
            if (innermost == null || lane.from() > innermost.from()) {
                innermost = lane;
            }
        }
        return innermost != null && innermost.async();
    }

    private static final Map<String, List<String>> SOURCES = new LinkedHashMap<>();

    /**
     * Every chain that starts at the work this method posted.
     *
     * <p>The work arrives at the lane as a closure or as a method reference. A closure's body is written in
     * the posting method and marked as such, and a method reference names its target outright, so both are
     * read here and nothing else in the method is: a posting method also builds the rest of the plugin, and
     * following its plain calls would make every wiring line async.
     */
    private static List<String> fromTheWorkPostedBy(JavaClasses classes, JavaCodeUnit poster, List<Lane> lanes) {
        List<String> found = new ArrayList<>();
        for (JavaAccess<?> posted : everyAccessOf(poster)) {
            if (!isACall(posted)) {
                continue;
            }
            if (!posted.isDeclaredInLambda() && !isAReference(poster, posted)) {
                continue;
            }
            if (!isInside(lanes, posted)) {
                continue;
            }
            if (asksARegion(posted)) {
                found.add(named(poster) + " -> " + call(posted));
                continue;
            }
            JavaClass target = posted.getTargetOwner().getBaseComponentType();
            if (!target.getName().startsWith(OURS)) {
                continue;
            }
            for (JavaCodeUnit called :
                    everyBodyOf(classes, target, posted.getTarget().getName(), takenBy(posted))) {
                straightLineToARegion(classes, called).ifPresent(found::add);
            }
        }
        return found;
    }

    /** The chain from one method to the first region-only call it reaches without going through a closure. */
    private static Optional<String> straightLineToARegion(JavaClasses classes, JavaCodeUnit start) {
        Map<String, String> cameFrom = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<JavaCodeUnit> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(named(start));
        while (!queue.isEmpty()) {
            JavaCodeUnit next = queue.poll();
            for (JavaAccess<?> access : everyAccessOf(next)) {
                if (!isACall(access)) {
                    continue;
                }
                if (access.isDeclaredInLambda() && !runsWhereItIsWritten(next, access)) {
                    // Written inside a closure, which is how this plugin says "not on this thread".
                    continue;
                }
                if (REGION_HOPS.contains(call(access))) {
                    // A hop, which is the same sentence said with a method rather than a closure.
                    continue;
                }
                if (asksARegion(access)) {
                    cameFrom.put(call(access), named(next));
                    return Optional.of(chain(cameFrom, named(start), call(access)));
                }
                JavaClass target = access.getTargetOwner().getBaseComponentType();
                if (!target.getName().startsWith(OURS)) {
                    continue;
                }
                for (JavaCodeUnit called :
                        everyBodyOf(classes, target, access.getTarget().getName(), takenBy(access))) {
                    if (!seen.add(named(called))) {
                        continue;
                    }
                    cameFrom.put(named(called), named(next));
                    queue.add(called);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The calls that run the closure they are handed there and then, on the thread that called them.
     *
     * <p>Every closure used to read as "not on this thread", which is right for the scheduler and for a
     * callback of ours and wrong for these. A contest's prize commands were dispatched inside
     * {@code Optional.ifPresent} on the settlement sweep, which runs on the async lane: the walk stopped at
     * the closure and never saw the command, and Paper refuses a command from any thread but the main one.
     */
    private static final Map<String, Set<String>> SAME_THREAD = Map.of(
            "java.util.Optional",
            Set.of("ifPresent", "ifPresentOrElse", "map", "flatMap", "filter", "orElseGet", "or"),
            "java.lang.Iterable",
            Set.of("forEach"),
            "java.util.Collection",
            Set.of("forEach", "removeIf"),
            "java.util.List",
            Set.of("forEach", "removeIf", "replaceAll"),
            "java.util.Set",
            Set.of("forEach", "removeIf"),
            "java.util.Map",
            Set.of("forEach", "computeIfAbsent", "computeIfPresent", "compute", "merge"),
            "java.util.stream.Stream",
            Set.of("forEach", "map", "filter", "flatMap", "anyMatch", "allMatch", "noneMatch", "peek"));

    /**
     * Whether a call written inside a closure runs on the thread of the method it is written in.
     *
     * <p>A closure runs here when some call around it runs it in place, {@code ifPresent} or {@code forEach},
     * and no call around it hands it on: one handed to the scheduler or to a callback of ours runs wherever
     * that sends it, and so does everything nested inside it. A call that does neither, a logger holding the
     * access as an argument, is neutral; counting it as a hand-off hid a dispatch written inside a log line
     * (found by the uxm-plots session). Reading only the innermost call got both
     * directions wrong once each: a {@code forEach} inside {@code scheduler.entity} read as here, and a tie
     * between two calls on one line picked whichever came first. A closure with no call around it that can be
     * found is read as it always was, as somewhere else.
     */
    private static boolean runsWhereItIsWritten(JavaCodeUnit unit, JavaAccess<?> access) {
        int line = access.getLineNumber();
        List<String> source = sourceOf(unit.getOwner());
        // Where the access itself opens on its line. A call that opens there or further right is one of its
        // arguments or comes after it, never a call around it: getConsoleSender( inside a one-line
        // lines.forEach(line -> server.dispatchCommand(server.getConsoleSender(), line)) must not win.
        int column = line >= 1 && line <= source.size()
                ? source.get(line - 1).indexOf(access.getTarget().getName() + "(")
                : -1;
        boolean enclosed = false;
        for (JavaAccess<?> around : everyAccessOf(unit)) {
            if (!isACall(around) || around.equals(access)) {
                continue;
            }
            if (around.getLineNumber() == line
                    && around.getTarget().getName().equals(access.getTarget().getName())) {
                continue;
            }
            int[] span = spanOf(unit.getOwner(), around.getTarget().getName(), around.getLineNumber())
                    .orElse(null);
            if (span == null || !encloses(span, line, column)) {
                continue;
            }
            // A call that hands its closure on, the scheduler or a method of ours, sends everything inside it
            // somewhere else, however many in-place calls sit between: a forEach inside scheduler.entity runs
            // on the entity's thread. A call that runs its closure in place keeps it here. Anything else, a
            // logger or a message call holding the access as an argument, says nothing about the thread.
            JavaClass owner = around.getTargetOwner();
            if (sendsElsewhere(owner)) {
                return false;
            }
            if (isIn(SAME_THREAD, owner, around)) {
                enclosed = true;
            }
        }
        return enclosed;
    }

    /** Whether a call hands the closure it is given to another thread, or to code of ours that may. */
    private static boolean sendsElsewhere(JavaClass owner) {
        return owner.getName().startsWith(OURS) || owner.isAssignableTo(SCHEDULER);
    }

    /**
     * Whether a call's argument list holds a position, to the column when the column is known.
     *
     * <p>Lines alone are not enough: {@code List.of("x").forEach(line -> dispatch(line))} is one line, and
     * {@code List.of(} opens and closes on it before the dispatch, so by lines it held the dispatch and hid it.
     */
    private static boolean encloses(int[] span, int line, int column) {
        if (line < span[0] || line > span[1]) {
            return false;
        }
        if (column < 0) {
            return true;
        }
        boolean afterStart = line > span[0] || column > span[2];
        boolean beforeEnd = line < span[1] || column < span[3];
        return afterStart && beforeEnd;
    }

    /** Whether this access is one of the calls that only a ticking region answers. */
    private static boolean asksARegion(JavaAccess<?> access) {
        for (JavaClass named : withEveryTypeAbove(access.getTargetOwner().getBaseComponentType())) {
            if (isIn(REGION_ONLY, named, access)
                    || isIn(GLOBAL_ONLY, named, access)
                    || isIn(ENTITY_ONLY, named, access)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this call is one of the names that table holds against the type it was written on. */
    private static boolean isIn(Map<String, Set<String>> table, JavaClass named, JavaAccess<?> access) {
        Set<String> forbidden = table.get(named.getName());
        return forbidden != null && forbidden.contains(access.getTarget().getName());
    }

    /**
     * The type the call names, and every type above it.
     *
     * <p>A call written against {@code CraftServer} or against a world implementation reaches the same method
     * as one written against the interface, so the guard asks the hierarchy rather than the one name the
     * compiler happened to record.
     */
    private static List<JavaClass> withEveryTypeAbove(JavaClass type) {
        List<JavaClass> family = new ArrayList<>();
        family.add(type);
        family.addAll(type.getAllRawSuperclasses());
        family.addAll(type.getAllRawInterfaces());
        return family;
    }

    /**
     * Every body a call under this name can reach: the ones on the type named, and the ones on our classes
     * that implement it.
     *
     * <p>The port is where a walk would otherwise stop. A port is an interface with no body of its own, and
     * everything it can do lives in an adapter, which is exactly where a call to the server is written.
     */
    private static List<JavaCodeUnit> everyBodyOf(
            JavaClasses classes, JavaClass target, String name, List<String> takes) {
        List<JavaCodeUnit> bodies = new ArrayList<>(sameName(target, name, takes));
        if (target.isInterface()) {
            for (JavaClass candidate : classes) {
                if (candidate.getName().startsWith(OURS)
                        && candidate.getAllRawInterfaces().contains(target)) {
                    bodies.addAll(sameName(candidate, name, takes));
                }
            }
        }
        return bodies.stream()
                .sorted(Comparator.comparing(JavaCodeUnit::getFullName))
                .toList();
    }

    /**
     * Every code unit of {@code target} under that name and that argument list.
     *
     * <p>The signature and not the name alone. It was the name until the entity rule went in, and the name
     * makes every overload of a method the same method: uxmJobs reads a player's circumstance on the
     * player's own thread and then hands the finished value to the lane, and the walk read that as the lane
     * calling the overload which reads the player, because both are called {@code report}.
     */
    private static List<JavaCodeUnit> sameName(JavaClass target, String name, List<String> takes) {
        return target.getCodeUnits().stream()
                .filter(unit -> unit.getName().equals(name))
                .filter(unit -> takes.equals(parametersOf(unit)))
                .map(JavaCodeUnit.class::cast)
                .toList();
    }

    /** What a code unit takes, by type name, which is how one overload is told from another. */
    private static List<String> parametersOf(JavaCodeUnit unit) {
        return unit.getRawParameterTypes().stream().map(JavaClass::getName).toList();
    }

    /** What the call passes, by type name, so an overload is not read as its sibling. */
    private static List<String> takenBy(JavaAccess<?> access) {
        if (access.getTarget() instanceof com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget call) {
            return call.getRawParameterTypes().stream().map(JavaClass::getName).toList();
        }
        return List.of();
    }

    /** Every call, field read and reference this method makes, in one set. */
    private static Set<JavaAccess<?>> everyAccessOf(JavaCodeUnit unit) {
        Set<JavaAccess<?>> accesses = new LinkedHashSet<>(unit.getAccessesFromSelf());
        accesses.addAll(unit.getCodeUnitReferencesFromSelf());
        return accesses;
    }

    /**
     * Whether this access reaches a body at all.
     *
     * <p>A field read is an access too, and its name is the field's. {@code CrateFlow} holds a use case in a
     * field called {@code open} and declares a method called {@code open}, so a walk that looked bodies up by
     * name alone read the field and followed the method, which is the method it was already standing in.
     */
    private static boolean isACall(JavaAccess<?> access) {
        return !(access instanceof com.tngtech.archunit.core.domain.JavaFieldAccess);
    }

    /** Whether this access is a method or constructor reference, which is the other shape posted work takes. */
    private static boolean isAReference(JavaCodeUnit poster, JavaAccess<?> access) {
        return poster.getCodeUnitReferencesFromSelf().contains(access);
    }

    /** One call, as a failure names it: the type and the method and nothing else. */
    private static String call(JavaAccess<?> access) {
        return simple(access.getTargetOwner().getName()) + "."
                + access.getTarget().getName();
    }

    /** One method, as a failure names it: the class and the method and nothing else. */
    private static String named(JavaCodeUnit unit) {
        return simple(unit.getOwner().getName()) + "." + unit.getName();
    }

    /** The chain as a reader follows it: the posting method, then each step, then the call. */
    private static String chain(Map<String, String> cameFrom, String start, String asked) {
        Deque<String> steps = new ArrayDeque<>();
        String at = asked;
        while (!at.equals(start)) {
            steps.addFirst(at);
            at = cameFrom.get(at);
        }
        steps.addFirst(start);
        return String.join(" -> ", steps);
    }

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
