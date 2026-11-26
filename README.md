# Hello Thuc Quyen Mobile

Independent Android Native Kotlin project. Phase 6.1, public development and GitHub Actions.

The approved runtime input is 5.3-official-1 (4664 files, 3897 resource IDs, 660 tasks, 816 choices). CI reconstructs the exact ZIP from four files at the repository root (`HTQ-5.3-runtime.part-01` through `HTQ-5.3-runtime.part-04`), verifies SHA-256 and extracts it into `app/src/main/assets/htq/`. The official ZIP is unchanged. Design and internal QA records are not bundled. No child records, secrets, or signing keys belong here.

Public means anyone can download the committed materials. Verify the rights to redistribute third-party audio before committing runtime parts. No release APK publication is enabled. Source-rights, font and device testing gates remain.

Toolchain: JDK 17, Gradle 8.9, AGP 8.7.2, Kotlin 2.1.21, Android SDK 35. Native technical foundation only; full screens and interaction engines belong to phases 6.2 and 6.3. CI must pass before 6.1 is closed.
