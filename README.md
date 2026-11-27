# Hello Thuc Quyen Mobile

Independent Android Mobile Native Kotlin project. Phase 6.3 native interaction integration has passed the engineering verification gate; the Phase 6.2 presentation and locked runtime are preserved. Development repository is public; this is not a released application.

## Locked content

The approved runtime input is 5.3-official-1 (4664 files, 3897 resource IDs, 17 topics, 110 lessons, 330 activities, 660 tasks, 816 choices). CI reconstructs the exact ZIP from the four root files HTQ-5.3-runtime.part-01 through HTQ-5.3-runtime.part-04, verifies SHA-256 and extracts it into app/src/main/assets/htq/. Do not modify these parts or the locked resource catalog. The native UI projection is separate from the runtime and preserves the presentation/assessment boundary.

## Verified 6.2 baseline

- Tested application commit: 7c4e0ab9c4d8fdd8846c6b3143f1b9787d757fbc
- CI: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45935120228 (SUCCESS).
- Kotlin build, debug APK, lint, core contract and both Android 15 instrumentation tests passed.
- Native presentation implementation covers 33 general screens and 36 task-screen templates. The Android test renders all 660 tasks and checks the 39 listening-first gates, prompt visibility, home control and measured button dimensions. General screens, navigation, persistence and adaptive layout are exercised with representative cases.
- Approved 851 layout references guide the responsive implementation; the test is not a pixel-by-pixel comparison of every reference on every device. Vietnamese Noto Sans Regular/Bold and bundled OFL notice are verified during build.
- No duplicate instrumentation or mandatory bulk screenshot collection. Use targeted visual checks only when necessary.

## Verified 6.3 baseline

- Tested application commit: 51a6b33721ec5b37b557e57c73d8e2d9ed526a73.
- CI: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45951132928 (SUCCESS, 2026-11-27).
- The locked runtime was reconstructed and verified: 4664 files, SHA-256 a54ef041ffd7bfb825a4ff6edb81520c5290ea03720ea064e19c31014b949b62. Runtime parts and resource lock remain unchanged.
- All 660 task controllers across 23 response types passed the core contract checks, including 1400 invalid cases, 151 equivalent responses, 660 session round trips and 39 listening-first gates. All 12 source-bound motion cases passed.
- Debug build, lint and both Android 15 instrumentation tests passed. The suite renders all 660 tasks in 36 task templates and exercises representative real interaction, persistence, rewards/garden, adult settings, playback-completion gating, static/animated demonstrations, restoration without autoplay, and adaptive layout.
- This is an engineering acceptance of the tested integration, not a full visual, phonetic, content-specialist or physical-device audit. No test was removed to obtain the passing result. Preserve the tested baseline; use targeted changes and tests for new work.

Subsequent phases must follow the approved project plan. Do not fabricate completion, grading, playback completion or rewards.

## Toolchain and release gate

JDK 17, Gradle 8.9, AGP 8.7.2, Kotlin 2.1.21, Android SDK 35. Preserve the tested emulator and CI environment. Main, verify-6.2 and verify-6.3 are covered by CI. No child records, credentials or signing keys belong in this public repository.

release_ready=false. No release APK publication or public APK artifact upload. Source redistribution rights, specialist content review and final device validation remain release gates. Public source visibility does not establish redistribution permission.
