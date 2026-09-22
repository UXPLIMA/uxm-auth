#!/usr/bin/env bash
#
# new-plugin.sh - turn this template into a real plugin.
#
# Rewrites the package root, the plugin id, the jar name, the relocation target and
# the permission prefix, then removes the worked example. Run it once, in a fresh
# clone, before you write any code.
#
# Usage:
#   scripts/new-plugin.sh <slug> <ClassPrefix> <command-root>
#
#   slug          the package segment and the repository name, e.g. glow
#   ClassPrefix   the prefix for the plugin class, e.g. Glow  -> GlowPlugin
#   command-root  the admin command, e.g. uxmglow
#
# Example:
#   scripts/new-plugin.sh glow Glow uxmglow
#
# Exit codes:
#   0  the rename is complete
#   1  the arguments are wrong
#   2  the tree is not a fresh template

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ $# -ne 3 ]]; then
    echo "usage: $(basename "$0") <slug> <ClassPrefix> <command-root>" >&2
    echo "  example: $(basename "$0") glow Glow uxmglow" >&2
    exit 1
fi

slug="$1"
prefix="$2"
root="$3"

if [[ ! "$slug" =~ ^[a-z][a-z0-9]*$ ]]; then
    echo "new-plugin: the slug must be lower case letters and digits, got: ${slug}" >&2
    exit 1
fi
if [[ ! "$prefix" =~ ^[A-Z][A-Za-z0-9]*$ ]]; then
    echo "new-plugin: the class prefix must start with a capital, got: ${prefix}" >&2
    exit 1
fi
if [[ ! -d src/main/java/com/uxplima/template ]]; then
    echo "new-plugin: this is not a fresh template; the template package is gone" >&2
    exit 2
fi

# The word a chat line opens with. It was left as TEMPLATE until 2026-09-22, so every plugin
# made from here shipped every line saying TEMPLATE until somebody noticed on a live server:
# uxmJobs shipped nine such lines and uxmCrates forty two, in both languages, and a full review
# and a product read missed all of them because nobody ran the command. The workspace's
# check-style.sh caught it afterwards, but that script is not the one a new plugin runs.
#
# The class prefix upper cased is a starting point and not an answer. uxmTeleportBow reads
# better as BOW than as TELEPORTBOW, so step 4 below asks for a look at it.
tag=$(printf '%s' "${prefix}" | tr '[:lower:]' '[:upper:]')

echo "Renaming the template to ${prefix} (package com.uxplima.${slug}, command /${root}, prefix ${tag})"

# Move the package before rewriting, so that the rewrite sees the final paths.
git mv src/main/java/com/uxplima/template "src/main/java/com/uxplima/${slug}"
git mv src/test/java/com/uxplima/template "src/test/java/com/uxplima/${slug}"
git mv "src/main/java/com/uxplima/${slug}/bootstrap/TemplatePlugin.java" \
       "src/main/java/com/uxplima/${slug}/bootstrap/${prefix}Plugin.java"

# Rewrite every reference. The order matters: the longest token first, so that a
# shorter one cannot match inside it.
while IFS= read -r -d '' file; do
    sed -i \
        -e "s/com\.uxplima\.template/com.uxplima.${slug}/g" \
        -e "s|com/uxplima/template|com/uxplima/${slug}|g" \
        -e "s/TemplatePlugin/${prefix}Plugin/g" \
        -e "s/uxmTemplate/uxm${prefix}/g" \
        -e "s/uxmtemplate/${root}/g" \
        -e "s/tag:'TEMPLATE'/tag:'${tag}'/g" \
        -e "s/etag:'TEMPLATE'/etag:'${tag}'/g" \
        "$file"
done < <(find . -type f \
    \( -name '*.java' -o -name '*.kts' -o -name '*.yml' -o -name '*.conf' -o -name '*.md' -o -name '*.toml' \) \
    -not -path './build/*' -not -path './.git/*' -not -path './docs/notes/*' -print0)

# docs/notes/ is a record of what happened. A note says "the template did X on this
# date". Rewriting the template name inside it makes the note claim that your plugin
# did it. History is not renamed.

# The comment over rootProject.name explains a script that step 5 below tells you to
# delete. Rewrite it now, while the script is still here to do it.
sed -i 's|^// The plugin id\. scripts/new-plugin\.sh rewrites it, along with the package root$|// The plugin id.|' settings.gradle.kts
sed -i '/^\/\/ and the jar name, when the template becomes a real plugin\.$/d' settings.gradle.kts

echo
echo "Done. Next:"
echo "  1. Delete the worked example: ExampleName, ExampleEvent, ExampleRepository,"
echo "     CreateExample, InMemoryExampleRepository, ExampleCommand and their tests."
echo "     Keep the lang branch of ExampleCommand: move it onto your own root command,"
echo "     with its keys, its catalog lines and LanguageSuggestions. Every plugin of"
echo "     ours carries it. See docs/04-i18n.md."
echo "  2. Strip the example keys from Keys.java and from both message catalogs."
echo "  3. Write docs/01-architecture.md for what this plugin actually does."
echo "  4. Rewrite the examples in docs/03-conventions.md, docs/04-i18n.md and"
echo "     docs/05-testing.md. They name the classes that step 1 deletes."
echo "  5. Rewrite README.md: it describes the template, not your plugin."
echo "  6. Delete this script. A real plugin is not a template."
echo "  7. ./gradlew spotlessApply && ./gradlew build"
