package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when the thread an event arrives on can reach the database.
 *
 * <p>{@code START-HERE.md} calls this the worst defect found in this estate. An event handler runs on the
 * thread that owns whatever the event happened to: a block break is the region that owns the block, a join
 * is the global region. A statement on that thread is a disk wait on the thread that ticks the world, and
 * on a server whose database is on another host it is a network wait. uxmQuests shipped one, and the shape
 * of it is always the same: a listener asks a repository a question and the repository is SQL.
 *
 * <p>The walk starts at every method annotated {@code @EventHandler} and follows our own classes. It
 * resolves a port to the adapters that implement it, because a listener reaches a table through two or
 * three of our classes and never directly.
 *
 * <p>It steps over anything written inside a closure, which is the convention every walk in this repository
 * keeps: a closure is how this plugin says which thread it wants, and the hand off to
 * {@code Scheduler.async} is written as one. That makes this a walk of the straight line out of the handler
 * and not of everything a handler can eventually cause, which is the half that can be checked without
 * running the server.
 */
final class NoDatabaseOnAnEventThreadTest {

    /** This plugin's own code, which is as far as the walk follows a call. */
    private static final String OURS = "com.uxplima.auth.";

    /** The one class that runs a statement. Everything of ours that reads a table goes through it. */
    private static final String DATABASE = "com.uxplima.uxmlib.storage.sql.Database";

    /**
     * The calls on it that wait on the disk.
     *
     * <p>Not every method of that class is a statement. Asking it which dialect it speaks reads a field,
     * and a store does that outside the closure it hands the write to: it is how the SQL text is built
     * before the work is posted. Naming the four that open or use a connection keeps the guard on what it
     * is about.
     */
    private static final Set<String> STATEMENTS = Set.of("transaction", "inTransaction", "connection", "ping");

    @Test
    @DisplayName("nothing an event handler runs reaches the database on the thread the event arrived on")
    void noHandlerReachesTheDatabase() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.uxplima.auth");

        Set<String> offenders = new LinkedHashSet<>();
        for (JavaClass listening : classes) {
            for (JavaMethod handler : listening.getMethods()) {
                if (!isAHandler(handler)) {
                    continue;
                }
                straightLineToTheDatabase(classes, handler).ifPresent(offenders::add);
            }
        }

        assertThat(offenders)
                .describedAs("An event handler runs on the thread that owns what the event happened to, and"
                        + " a statement there is a disk wait on the thread that ticks the world. Read what"
                        + " is in memory, and hand the query and the write to the async lane.")
                .isEmpty();
    }

    @Test
    @DisplayName("the walk starts at the handlers, so it cannot pass by finding none")
    void thehandlersAreFound() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.uxplima.auth");

        List<String> handlers = new ArrayList<>();
        for (JavaClass listening : classes) {
            for (JavaMethod candidate : listening.getMethods()) {
                if (isAHandler(candidate)) {
                    handlers.add(named(candidate));
                }
            }
        }

        assertThat(handlers)
                .describedAs("A walk that starts nowhere is a guard that always passes. Every plugin of"
                        + " ours listens to something, so finding no handler means the import is wrong"
                        + " rather than that there is nothing to check.")
                .isNotEmpty();
    }

    /**
     * Whether this method is one the server calls on a thread that owns something.
     *
     * <p>An event whose name begins with {@code Async} is fired off the server threads by design: a
     * pre login happens before the player exists and is the one place a plugin is meant to read its rows
     * for them. Those are the events the server names for it, so the name is the question asked.
     */
    private static boolean isAHandler(JavaMethod method) {
        if (!method.isAnnotatedWith(org.bukkit.event.EventHandler.class)) {
            return false;
        }
        return method.getRawParameterTypes().stream()
                .noneMatch(type -> type.getSimpleName().startsWith("Async"));
    }

    /** The chain from a handler to the first statement it reaches without going through a closure. */
    private static Optional<String> straightLineToTheDatabase(JavaClasses classes, JavaCodeUnit start) {
        Map<String, String> cameFrom = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<JavaCodeUnit> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(named(start));
        while (!queue.isEmpty()) {
            JavaCodeUnit next = queue.poll();
            for (JavaAccess<?> access : everyAccessOf(next)) {
                if (access instanceof JavaFieldAccess || access.isDeclaredInLambda()) {
                    continue;
                }
                if (access.getTargetOwner().getName().equals(DATABASE)
                        && STATEMENTS.contains(access.getTarget().getName())) {
                    cameFrom.put(call(access), named(next));
                    return Optional.of(chain(cameFrom, named(start), call(access)));
                }
                JavaClass target = access.getTargetOwner().getBaseComponentType();
                if (!target.getName().startsWith(OURS)) {
                    continue;
                }
                for (JavaCodeUnit called : everyBodyOf(classes, target, access)) {
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
     * Every body a call can reach: the ones on the type named, and the ones on our classes that implement
     * it. A port is where a walk would otherwise stop, and a port is exactly where a table hides.
     */
    private static List<JavaCodeUnit> everyBodyOf(JavaClasses classes, JavaClass target, JavaAccess<?> access) {
        String name = access.getTarget().getName();
        List<String> takes = takenBy(access);
        List<JavaCodeUnit> bodies = new ArrayList<>(sameSignature(target, name, takes));
        if (target.isInterface()) {
            for (JavaClass candidate : classes) {
                if (candidate.getName().startsWith(OURS)
                        && candidate.getAllRawInterfaces().contains(target)) {
                    bodies.addAll(sameSignature(candidate, name, takes));
                }
            }
        }
        return bodies.stream()
                .sorted(Comparator.comparing(JavaCodeUnit::getFullName))
                .toList();
    }

    private static List<JavaCodeUnit> sameSignature(JavaClass target, String name, List<String> takes) {
        return target.getCodeUnits().stream()
                .filter(unit -> unit.getName().equals(name))
                .filter(unit -> takes.equals(unit.getRawParameterTypes().stream()
                        .map(JavaClass::getName)
                        .toList()))
                .map(JavaCodeUnit.class::cast)
                .toList();
    }

    private static List<String> takenBy(JavaAccess<?> access) {
        if (access.getTarget() instanceof com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget call) {
            return call.getRawParameterTypes().stream().map(JavaClass::getName).toList();
        }
        return List.of();
    }

    private static Set<JavaAccess<?>> everyAccessOf(JavaCodeUnit unit) {
        Set<JavaAccess<?>> accesses = new LinkedHashSet<>(unit.getAccessesFromSelf());
        accesses.addAll(unit.getCodeUnitReferencesFromSelf());
        return accesses;
    }

    /** One call, as a failure names it: the type and the method and nothing else. */
    private static String call(JavaAccess<?> access) {
        return simple(access.getTargetOwner().getName()) + "."
                + access.getTarget().getName();
    }

    private static String named(JavaCodeUnit unit) {
        return simple(unit.getOwner().getName()) + "." + unit.getName();
    }

    private static String simple(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /** The path back from the statement to the handler, written the way it was walked. */
    private static String chain(Map<String, String> cameFrom, String start, String end) {
        List<String> back = new ArrayList<>();
        String at = end;
        while (at != null && !at.equals(start)) {
            back.add(at);
            at = cameFrom.get(at);
        }
        back.add(start);
        java.util.Collections.reverse(back);
        return String.join(" -> ", back);
    }
}
