package com.uxplima.template.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A window a player sees is a file the operator can rewrite, unless it is a container.
 *
 * <p>Every menu of ours is drawn by the library engine off a spec in {@code menus/}, which is what lets an
 * operator move a tile, rename it and restyle it without us. A class that calls
 * {@code Bukkit.createInventory} or {@code openInventory} itself, or that implements
 * {@code InventoryHolder}, has stepped under the engine and has a window nobody can edit. That is how it
 * goes wrong: not by somebody deciding against the engine, but by one screen being quicker to hand-roll
 * on a Tuesday, and the next one copying it.
 *
 * <p>Some screens genuinely are not menus, and they are named below with the reason. A real container the
 * player fills, a reveal animation whose frames change every tick, a vanilla workstation: none of those is
 * a tile grid a spec could describe. The list is short on purpose, and a new name on it is a decision
 * somebody has to write down here.
 */
@AnalyzeClasses(packages = "com.uxplima.template", importOptions = ImportOption.DoNotIncludeTests.class)
final class OnlyALeafMakesItsOwnWindowTest {

    /**
     * Nothing is exempt here, and that is the point.
     *
     * <p>Every window this plugin draws is a spec in {@code menus/} that the engine renders, so there is
     * no screen that has to build its own inventory. The set is empty and the fence is absolute: a class
     * that needs to be on it has to be put here with the reason it is a container rather than a menu,
     * which is a decision somebody writes down rather than a line somebody adds in a hurry.
     */
    private static final Set<String> LEAVES = Set.of();

    @ArchTest
    static final ArchRule onlyANamedLeafBuildsItsOwnWindow = noClasses()
            .that(areNotANamedLeaf())
            .should(buildOrOpenARawInventory())
            .because("a menu is a spec the engine draws, so an operator can move a tile. A hand-rolled"
                    + " inventory is a window nobody can edit: put it in a spec, or name it here with the"
                    + " reason it is a container rather than a menu");

    /**
     * The exemption set cannot rot quietly.
     *
     * <p>A leaf that is renamed or deleted leaves a name here that matches nothing, and the fence silently
     * widens by one class nobody notices. So every name has to still be a class.
     */
    @Test
    @DisplayName("every leaf named in the exemption set is still a class in this plugin")
    void theNamedLeavesAreStillThere() {
        List<String> gone = new java.util.ArrayList<>();
        for (String leaf : LEAVES) {
            try {
                Class.forName(leaf);
            } catch (ClassNotFoundException missing) {
                gone.add(leaf);
            }
        }

        assertThat(gone)
                .describedAs("a name here that matches no class exempts nothing and hides that the fence"
                        + " has widened. Remove it, or correct it to the class it was renamed to")
                .isEmpty();
    }

    /**
     * A plugin that ships windows guards their Bedrock face.
     *
     * <p>This is the rot this fence cannot see on its own. A plugin with no {@code menus/} folder needs no
     * Bedrock guard, and five of the family have none. The day one of them gains a window, nothing would
     * remind anybody that a window is also a form, and the first Bedrock player to open it gets a chest.
     * So the two are tied together here: windows on disk mean {@code BedrockFormsTest} in the tests.
     */
    @Test
    @DisplayName("a plugin that ships windows also guards their Bedrock face")
    void windowsComeWithTheirBedrockGuard() throws IOException {
        Path menus = Path.of("src", "main", "resources", "menus");
        if (!Files.isDirectory(menus)) {
            return;
        }
        try (Stream<Path> files = Files.list(menus)) {
            if (files.noneMatch(file -> file.toString().endsWith(".conf"))) {
                return;
            }
        }

        List<Path> guard = new ArrayList<>();
        try (Stream<Path> tests = Files.walk(Path.of("src", "test", "java"))) {
            tests.filter(path -> path.endsWith("BedrockFormsTest.java")).forEach(guard::add);
        }

        assertThat(guard)
                .describedAs("this plugin ships windows, and a window is a form on a Bedrock client."
                        + " Copy BedrockFormsTest from a plugin that has one: it is the same file but the"
                        + " package and the bootstrap path")
                .isNotEmpty();
    }

    private static ArchCondition<JavaClass> buildOrOpenARawInventory() {
        return ArchConditions.implement("org.bukkit.inventory.InventoryHolder")
                .or(ArchConditions.callMethodWhere(callsBukkitInventoryFactoryOrOpen()))
                .as("build or open an inventory of its own");
    }

    /**
     * A call that builds or shows an inventory frame.
     *
     * <p>The match is on the declaring class as well as the name, never on the name alone, so a method of
     * ours that happens to be called {@code openInventory} is not mistaken for Bukkit's.
     */
    private static DescribedPredicate<JavaMethodCall> callsBukkitInventoryFactoryOrOpen() {
        return DescribedPredicate.describe("call Bukkit.createInventory or HumanEntity.openInventory", call -> {
            String owner = call.getTargetOwner().getPackageName();
            String method = call.getName();
            return owner.startsWith("org.bukkit")
                    && (method.equals("createInventory") || method.equals("openInventory"));
        });
    }

    /**
     * Everything but the named leaves.
     *
     * <p>A leaf's nested members carry the same signature, a holder written as an inner class or a nested
     * record, so the match is on the top-level enclosing class rather than on the exact nested name.
     */
    private static DescribedPredicate<JavaClass> areNotANamedLeaf() {
        return DescribedPredicate.describe("are not a named container leaf", javaClass -> {
            JavaClass outermost = javaClass;
            while (outermost.getEnclosingClass().isPresent()) {
                outermost = outermost.getEnclosingClass().get();
            }
            return !LEAVES.contains(outermost.getFullName());
        });
    }
}
