# Hello Thuc Quyen Mobile

Independent Android Mobile Native Kotlin project. Phase 7 emulator integration and representative visual QA have passed the engineering verification gate; the verified Phase 6 baseline and locked runtime are preserved. Development repository is public; this is not a released application.

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

## Verified 6.4 baseline

- Tested application commit: befe600c8e56f82b3ef011e1132bfde5f6da0abc.
- CI: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45953398370 (SUCCESS, 2026-11-27, first run on verify-6.4).
- Deferred earned rewards remain accessible from the garden and inventory, independent of the lesson. The existing completion/claim transaction prevents duplicate rewards on replay.
- A claimed item has its own task-keyed identity, including items sharing the same artwork. Place, relocate, put away and undo use the existing persisted coordinates and inventory; no schema migration or new media was introduced.
- Extended the existing two Android 15 instrumentation tests to exercise deferred claims, distinct identical items, counts, relocation, undo, recreation and replay. Both tests passed, together with the full existing core contract, 660-task rendering sweep, debug build and lint.
- Acceptance covers the implemented and tested 6.4 garden/reward scope; it does not replace release, specialist or physical-device validation.

## Verified 6.5 baseline

- Tested application commit: c4152b0a2cb4a057d43a520efc4bb6e48b36b8e6.
- CI: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45955161529 (SUCCESS, 2026-11-27, first run on verify-6.5).
- Local parent progress distinguishes opened lessons, validated completions and explicit participation; review suggestions use actual recorded lesson openings, without ranking or remote tracking.
- Storage schema v2 adds only the opened_lessons table. The tested v1-to-v2 migration preserves existing session, completion, reward and garden records. Historical openings can be recovered only from existing completion/session evidence, not invented.
- Parent gate tests cover incorrect challenges, exact core expiry boundary and closure; native UI tests cover reauthorization after recreation, persisted settings, the activity-end rest reminder, and cancellation and confirmation of progress reset. Reset preserves content and parent preferences.
- Both Android 15 instrumentation tests, the full existing core contract, debug build and lint passed. All locked resource files remain unchanged. No additional media or redundant test run was needed on the verification branch.
- This engineering acceptance is limited to the tested scope, not a physical-device, specialist-content or release audit.

## Verified Phase 7 engineering baseline

- Tested application commit: 5baba0dd230a1ca3d0a64b585cd856d6ad7ee9e4.
- Final integration CI: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45957039961 (SUCCESS); compact-presentation correction and regression CI: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45958034701 (SUCCESS, 2026-11-27).
- Verified corrected debug APK: 93284055 bytes; SHA-256 6f90cab737ee10c4bb149202af263950db325e66e03b4f6e283e4d957ce1e129. All 4664 locked runtime files matched inside the APK byte-for-byte; 660 task controllers and 660 native render checks, 39 listening gates and both Android 15 instrumentation tests passed. Offline operation, progress/garden restoration, parent settings and motion gates passed.
- Compared all 13 before/after representative screenshots from the two emulator runs. The compact home shows six areas, the primary task action remains visible, reward/garden imagery is clearer and the letter/number/English/story listening controls are easier to reach. Very small screens and long content may still require scrolling; the comparison is not an exhaustive pixel-level or real-device audit.
- Evidence artifact: https://github.com/xuanlocbo/HelloThucQuyen/actions/runs/45958034701/artifacts/15179483869 (screenshots, package.json and native QA log; expires per GitHub retention). The measured welcome startup was 2047 ms and PSS 197225 KiB on that emulator run, not a device performance guarantee.
- Outstanding content provenance: AUD-ANM-0007, AUD-ANM-0011, AUD-INS-0003, AUD-INS-0007, AUD-INS-0008, AUD-NAT-0008, AUD-OBJ-0018, AUD-TRN-0006; the independent excerpt listening check for AUD-OBJ-0010 was waived, not certified; AUD-VIG-0715 through AUD-VIG-0718 require existing voice-model terms review. The remaining 2307 audio records retain prior source evidence, not a fresh legal clearance. Preserve all approved files until genuine evidence or an authorized replacement is available.
- Engineering acceptance is not public-release acceptance: physical-device, specialist and source-rights gates remain open. The APK is still a development build (0.6.1-dev), with the default Android launcher icon and debug signature.

## Owner device QA handoff

For the one explicitly marked `[device-qa]` main push, the existing CI job runs package integrity verification and the existing Android regression suite before encrypting the complete debug APK to the QA recipient's public certificate. It publishes only the authenticated encrypted CMS file and its package report, never the plaintext APK or private key. The recipient's private key stays outside GitHub. This is a private device-test transfer, not release permission; `release_ready=false` remains unchanged. Debug signing certificates may vary between CI machines; a fresh install may be needed on the test device.

Do not fabricate completion, grading, playback completion or rewards.

## Toolchain and release gate

JDK 17, Gradle 8.9, AGP 8.7.2, Kotlin 2.1.21, Android SDK 35. Preserve the tested emulator and CI environment. Main, verify-6.2, verify-6.3, verify-6.4, verify-6.5 and verify-7 are covered by CI. No child records, credentials or signing keys belong in this public repository.

release_ready=false. No release APK publication or public APK artifact upload. Source redistribution rights, specialist content review and final device validation remain release gates. Public source visibility does not establish redistribution permission.
