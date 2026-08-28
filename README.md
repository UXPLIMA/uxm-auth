# uxmTemplate

The starting point for a UXPLIMA Paper plugin.

Hexagonal architecture on Paper 26.2 and Java 25, built on uxmLib. Folia ready, fully
configurable, multi-language, with every command renameable by the operator.

## Start a new plugin

```bash
git clone https://github.com/INOVIXI/uxm-plugin-template.git uxm-<name>
cd uxm-<name>
scripts/new-plugin.sh <slug> <ClassPrefix> <command-root>
```

Then delete the worked example, and read `docs/00-README.md`.

## Build

```bash
./gradlew spotlessApply
./gradlew build          # compile, static analysis, format check, tests
./gradlew shadowJar      # the deployable jar
./gradlew runServer      # a Paper dev server
```

## What the template already gives you

- The hexagonal package tree, with a worked example through every layer.
- Error Prone, NullAway, Spotless and Palantir formatting, all under `-Werror`.
- Seven ArchUnit fences and a source guard against untranslatable text, each one proved to
  fail before it was committed.
- A message key enum with English and Turkish catalogs, and a per-key default.
- A typed configuration snapshot, and `commands.conf` for renaming any command.
- uxmLib shaded and relocated, so the jar runs on its own.
