# Umbra

A high-performance, GPU-accelerated Android terminal powered by libghostty.

## Before You Start

1. Read `docs/ARCHITECTURE.md` to understand the project structure and module dependencies.
2. Create a new branch before making any changes.

## Tech Stack

Kotlin, Jetpack Compose, Material 3, Koin (DI), Room (database), Retrofit (network), Navigation 3

## Commands

```bash
./gradlew build                    # Build all modules
./gradlew :app:installDebug        # Install app
./gradlew test                     # Run tests
./gradlew spotlessApply            # Run formatter
```

## Testing Requirements

- Run `./gradlew test` after making any code changes
- Verify all tests pass before considering work complete
- If tests fail, fix them before moving on to new tasks
- Write tests for new features and bug fixes
- Do not skip or ignore failing tests

## Architecture Rules

- Features depend only on `:core`, never on each other
- `:app` depends on `:core` and feature modules
- Features must be offline-first
- UI strings live in `:core` for cross-platform reuse
- Every `@Composable` needs a `@Preview`
- Every NDK / native code goes in the terminal/ module
- New code must be backed by unit tests

# PR Description

- Describe what is the PR about.
- Tag the PR with [FIX], [FEATURE]
- Don't use bullet points to describe how the code was validated

## Project Docs

- `docs/ARCHITECTURE.md` — module structure and dependencies
- `docs/PRD.md` — Design doc
