# DECISIONS (one line each, newest last. Do not re-debate; add a new line to overturn one)

- D1 2026-11-16 Stack: Kotlin + classic Android Views with a custom Canvas-drawn KeyboardView. No Compose in the IME, no
  third-party libraries beyond AndroidX core. Why: lowest latency and memory in the IME process. (Stack choice was delegated to Claude.)
- D2 2026-11-16 Workflow: everyone commits straight to `main`; tasks only in STATUS.md; repo is public.
- D3 2026-11-16 App name "Monsoon Script". Package id `com.monsoonscript.keyboard` (routine choice, changeable before release).
- D4 2026-11-16 minSdk 26 (Android 8.0), chosen so the Oppo A3s backup phone is covered.
- D5 2026-11-16 Avro: port the phonetic engine from the uploaded MPL-2.0 Avro Keyboard source into Kotlin, credited. Ported
  files stay MPL-2.0; the rest of the app is licensed separately (license choice pending, STATUS Needs user #3).
- D6 2026-11-16 Ridmik is a feature reference only: reimplement, no code or assets used, no decompiling.
- D7 2026-11-16 First milestone v0.1 = bare English keyboard + hardware keyboard pass-through (user's biggest complaint with Ridmik).
- D8 2026-11-16 Reference data lives in `third_party/avro-reference/` (engine sources, layout XML with images stripped, docs).
  Avro's `Database.db3` is exported to `data/avro-dictionary.tsv` + `data/avro-suffixes.tsv` (159k words, 749 suffixes); the .db3 itself is not copied.
- D9 2026-11-16 Signing: one fixed release key kept in GitHub Secrets (not in the repo), so builds update in place.
- D10 2026-11-16 Internet: the INTERNET permission is allowed, but the owner must be able to turn networking off completely.
  Required: a master "Offline mode" setting that hard-blocks every network code path (no exceptions, no silent calls).
  Typed text must never be sent anywhere. Open: also build an offline-only flavor without the permission (STATUS Needs user #4).
- D11 2026-11-16 Key style: rounded keys with small gaps, Gboard-like (owner chose).
- D12 2026-11-16 Colors: follow the phone's light/dark mode (owner chose). Custom themes come in v0.5.
- D13 2026-11-16 Top strip above the keys: suggestion area in the middle plus always-visible shortcut icons (language/IME globe, emoji, clipboard, mic) (owner chose).
- D14 2026-11-16 Numbers: no number row by default; long-press top-row letters for digits (hint shown on the key); an optional full number row is a later setting (owner chose).
- D15 2026-11-16 Language switching: a language key (EN / বাং) beside the space bar AND swipe left/right on the space bar. Each must be
  switchable on/off in settings (default on). The strip globe stays for other keyboards (tap = next, hold = picker).
- D16 2026-11-16 Avro Phonetic: live Bangla composing text (underlined) with suggestions in the strip; space/punctuation accepts the
  first suggestion, tapping another picks it. Optional later setting: typed Latin word first (like Ridmik "show typed word").
- D17 2026-11-16 Fixed Bangla layouts: Provat, Bijoy Classic, National (Jatiya); Bornona, Munir Optima, Avro Easy later.
  Bijoy Classic is not in Avro's pack: build it from the standard chart and have the owner verify it.
- D18 2026-11-16 Digits in Bangla mode: English digits typed by default, Bangla digits shown as hints and typed on long-press.
- D19 2026-11-16 Language key placement (overturns the "beside the space bar" part of D15): the language key (EN / বাং) sits in the top
  strip right next to the globe (strip: globe, language key, emoji, clipboard ... mic). The bottom row stays as is. Tap = toggle English <->
  last-used Bangla layout; long-press = small popup to pick Avro / Provat / Bijoy / National (owner chose). Space-bar swipe (D15) stays.
