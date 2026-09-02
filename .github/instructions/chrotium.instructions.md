---
description: "Use when working on Chrotium Android/Kotlin code, Compose UI, Gradle config, or browser engine logic. Enforces project conventions and requires MAI-Code-1.1-Flash for all agent work."
name: "Chrotium Project Instructions"
applyTo: ["app/src/**/*.kt", "app/**/*.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle/**/*.toml"]
---

# Chrotium Project Instructions

## Required model

- Always use `MAI-Code-1.1-Flash` for this project when generating code, fixes, refactors, tests, or architectural guidance.
- Do not switch to another model unless the user explicitly requests it.

## Project context

- This repository is an Android browser app built with Kotlin and Jetpack Compose.
- Favor fixes that preserve app responsiveness and avoid UI jank, ANR, or stale async state.
- Prefer small, targeted changes rooted in the actual failure mode rather than broad rewrites.

## Core engineering rules

- Investigate root cause before patching. Trace the data flow and verify the failure mechanism.
- For bugs, write or update a failing test when practical before implementing the fix.
- Validate with the smallest relevant Gradle command for the affected area.
- Favor real behavior testing over mock-only assertions.
- Do not add test-only production methods or code paths created solely for tests.

## Android/Kotlin safety rules

- Never use `runBlocking` on the main thread in UI or startup code paths.
- Avoid broad `catch (Exception)` blocks; prefer specific exceptions and meaningful logging.
- Check nullability and lifecycle state before invoking WebView or callback operations.
- Ensure asynchronous results are invalidated when state changes, especially for suggestions, focus, and page-sync logic.
- Keep WebView injections scoped to the specific domains that need them instead of applying global page optimizations.

## Refactor and quality expectations

- Preserve existing behavior unless the task explicitly requires change.
- Keep refactors narrow and explainable; avoid unrelated cleanup in the same patch.
- Prefer explicit, readable code over clever but opaque abstractions.
- Keep logic and state guards clear enough to reason about during fast user interactions.

## Release and validation

- Prefer Gradle verification commands that match the changed area.
- If a fix affects app startup, settings, UI focus, or browser engine behavior, run the relevant compile or unit test task before considering it complete.
- If the build is already green, do not claim success without fresh evidence from the command output.

## Working style

- Keep responses concise and practical.
- Do not create new markdown summary files unless the user explicitly asks for one.
- Prefer direct action: patch, validate, and report the evidence.
