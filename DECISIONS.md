# DECISIONS (one line each, newest last. Do not re-debate; add a new line to overturn one)

- D1 2026-11-16 Stack: Kotlin + classic Android Views with a custom Canvas-drawn KeyboardView. No Compose in the IME, no
  third-party libraries beyond AndroidX core. Why: lowest latency and memory in the IME process. (Stack choice was delegated to Claude.)
- D2 2026-11-16 Workflow: everyone commits straight to `main`; tasks only in STATUS.md; repo is public.
- D3 2026-11-16 App name "Monsoon Script". Package id `com.monsoonscript.keyboard` (routine choice, changeable before release).
- D4 2026-11-16 minSdk 26 (Android 8.0), chosen so the Oppo A3s backup phone is covered.
- D5 2026-11-16 Avro: port the phonetic engine from the uploaded MPL-2.0 Avro Keyboard source into Kotlin, credited. Ported
  files stay MPL-2.0; the rest of the app is licensed separately (license choice pending, STATUS Needs user #5).
- D6 2026-11-16 Ridmik is a feature reference only: reimplement, no code or assets used, no decompiling.
- D7 2026-11-16 First milestone v0.1 = bare English keyboard + hardware keyboard pass-through (user's biggest complaint with Ridmik).
- D8 2026-11-16 Reference data lives in `third_party/avro-reference/` (engine sources, layout XML with images stripped, docs).
  Not copied: Avro's 5.4 MB `Database.db3` suggestion dictionary; fetch it from the user's Avro zip or OmicronLab's repo when T-suggestions starts.
