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

echo "Renaming the template to ${prefix} (package com.uxplima.${slug}, command /${root})"

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
        -e "s/TemplatePlugin/${prefix}Plugin/g" \
        -e "s/uxmTemplate/uxm${prefix}/g" \
        -e "s/uxmtemplate/${root}/g" \
        "$file"
done < <(find . -type f \
    \( -name '*.java' -o -name '*.kts' -o -name '*.yml' -o -name '*.conf' -o -name '*.md' -o -name '*.toml' \) \
    -not -path './build/*' -not -path './.git/*' -print0)

echo
echo "Done. Next:"
echo "  1. Delete the worked example: ExampleName, ExampleEvent, ExampleRepository,"
echo "     CreateExample, InMemoryExampleRepository, ExampleCommand and their tests."
echo "  2. Strip the example keys from Keys.java and from both message catalogs."
echo "  3. Write docs/01-architecture.md for what this plugin actually does."
echo "  4. ./gradlew spotlessApply && ./gradlew build"
