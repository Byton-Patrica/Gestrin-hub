# Gestrin Hub

Gestrin Hub project source is licensed under Apache-2.0. Third-party libraries retain their own licenses.

The APK contains the Hub application and its normal application libraries only. It does not contain third-party runner APKs, emulation cores, container images, guest operating systems, firmware, encryption keys, games, or AI model weights. User-requested runner downloads come from the corresponding upstream release channels and are installed as separate applications through Android's system installer. Components internal to those runners are managed within the runners.

Application dependencies include AndroidX / Jetpack Compose / Room / DocumentFile (Apache-2.0), Kotlin and kotlinx.coroutines (Apache-2.0), and Coil (Apache-2.0). Gradle reports the exact dependency graph with `:app:dependencies`. Consult upstream notices for transitive dependencies. No code from Winlator, Kirikiroid2, PPSSPP, Vita3K or Eden is linked into this application.

Runner names identify independent projects; Gestrin Hub is not affiliated with or endorsed by their authors or console vendors. Runner licenses are documented in docs/02-upstream-research.md and must be reviewed separately if any future release redistributes their code or binaries.

The locally produced preview uses a development signing certificate. Public distribution requires an owner-controlled signing identity and completed release validation.
