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
- D20 2026-11-17 Four Claude accounts work in parallel, named claude-l, claude-e, claude-g, claude-s. The owner's pasted command starts
  with "You are claude-X"; each Claude has its own tasks (owner tag on every STATUS.md task line) and its own notes file `status/claude-X.md`
  (steps numbered with progress, next step, half-done files, "Warnings" from others). Initial split (owner approved): claude-l = T8b + T8a fixes,
  claude-e = T9 then T11, claude-g = T10, claude-s = T4 + T6. New tasks use ids with the Claude's letter (L1, E1, G1, S1) so ids never clash.
- D21 2026-11-17 When another Claude's task is unfinished but nothing is broken, a newly started Claude goes ahead with the owner's request
  and leaves a Warning in the other Claude's notes file for anything it changed in their area (owner chose).
- D22 2026-11-17 The only work a Claude takes over from another is a broken build (fix it first, whoever caused it). Unfinished tasks are never
  taken over; they wait for their owner (owner chose).
- D23 2026-11-17 Suggestion strip while typing a Bangla word: suggestions fill the whole strip and the icons (globe, language, emoji, clipboard,
  mic) hide until the word ends, then come back (owner chose). Space commits the underlined word; tapping a suggestion commits it plus a space.
- D24 2026-11-17 Colon in Avro: ':' typed right after letters is visarga (ঃ) when a letter follows ("du:kho" -> দুঃখ); a colon that ends the
  word (before space/punctuation/end) stays a plain ':' (owner chose "smart").
- D25 2026-11-17 License (E1): the whole app is GPL-3.0 because the password manager reuses KeePassDX code (owner chose). The MPL-2.0 Avro files
  keep their own header. Settles STATUS Needs user #3. The LICENSE file and headers are added only after the E1 feasibility check passes; if E1
  drops KeePassDX, a new D-line decides the license again.
- D26 2026-11-17 Password manager engine (E1): build on KeePassDX's `:database` + `:crypto` modules behind a thin interface of our own (open, search,
  read/write entry, save, lock), AFTER a feasibility check: they build in our CI, open a test .kdbx and work without KeePassDX's `:app`. Fallback if
  the check fails: KeePassJava2 (Apache-2.0). "Companion only" (rely on the separate KeePassDX app) was not chosen. (owner chose)
- D27 2026-11-17 Password picker (E1) is opened from a key icon in the top strip, always visible like the other strip icons (hidden while a Bangla word
  is being composed, D23). Exact slot in the strip is decided when it is built (owner chose).
- D28 2026-11-17 Build order (E1): the vault engine + keyboard picker first; the Android Autofill service with the "save this login?" prompt and the
  inline suggestions in the strip come afterwards (owner chose).
- D29 2026-11-17 Picker panel (E1) replaces the key rows at the same height; the top strip stays, with the key icon highlighted and a way back to the
  keys. Search and other typing inside the panel use the keyboard's own keys; what is typed there never reaches the app (owner chose).
- D30 2026-11-17 Full vault screens (E1) are our own screens in the keyboard's visual style (colors, shapes, accent, light/dark), so the keyboard
  panel and the app feel like one product. Not KeePassDX's screens, not the stock Material look (owner chose).
- D31 2026-11-17 Adding an entry (E1): finished design = a mini form in the keyboard panel (title, username, password with generator, Save; "More fields"
  opens the app's add screen with the typed values carried over). The first version may use the app's add screen alone as a stopgap while the panel's
  typing mode is built (owner chose).
- D32 2026-11-17 Picker first view (E1): "Matches for this app" (entries that store this app's id), then "Recent"; folders one tap away. In browsers the
  website is not visible to the keyboard, so there it shows Recent + search (owner chose).
- D33 2026-11-17 Vault screens in the app (E1): one list screen (search on top, folders as rows, "+" button, a menu for settings and the generator);
  no bottom tabs, no side drawer (owner chose).
- D34 2026-11-17 Reaching the vault (E1): a "Vault" card on the app's main screen plus a long-press shortcut on the launcher icon; no second
  home-screen icon. The keyboard's expand button opens it too (owner chose).
- D35 2026-11-17 Vault file location (E1): the owner picks it with Android's file picker, both when creating a vault and when opening one (existing .kdbx
  from KeePassDX/KeePassXC open in place, no migration). Cloud sync = any synced folder (Drive, Nextcloud, Syncthing), no network code in the app.
  Private app storage is not offered for now (owner chose).
- D36 2026-11-17 Saving (E1): automatic after every change, written safely (temporary file, then swap), with the last few versions kept as backups.
  A file that changed elsewhere (synced from another device) is never overwritten silently: detect, then merge or ask (design in stage 7/8).
  No manual Save button (owner chose).
- D37 2026-11-17 Unlocking from the keyboard (E1): a locked panel shows an Unlock button that opens a brief see-through unlock screen over the current
  app (fingerprint or master password), then returns to the picker. The master password is not typed in the keyboard panel (owner chose).
- D38 2026-11-17 Auto-lock (E1): the vault locks when the screen turns off and after 5 minutes without use. A Lock button is always available. It does
  NOT lock just because the keyboard closed. The timeout becomes a setting later (owner chose).
- D39 2026-11-17 Fingerprint (E1): a convenience, never the root. The master password is needed for the first unlock and again after a phone restart, after
  3 days, or when the phone's fingerprints change. Fingerprint opens a copy of the unlock secret wrapped by a hardware-backed Android Keystore key that needs a
  fingerprint check. Each rule must be confirmed enforceable before we rely on it (owner chose).
- D40 2026-11-17 Tapping an entry in the picker (E1): shows chips Username / Password / Code; tapping a chip types it; typing a password into a field that is
  not a password field asks first. One-tap fill is not the default (maybe an optional setting later) (owner chose).
- D41 2026-11-17 Prediction data (L2, claude-l): source is WM Keyboard Data (wasi-master/wmkeyboard-data), offline only, no neural models (owner chose):
  - Banglish is MERGED into English mode: English mode suggests English and Banglish words together (no separate Banglish language entry).
  - Banglish pair lists (`bn_rom_bigrams/trigrams`) are NOT used: they come from the data owner's private chats and have no license. Only the Banglish word
    list (`bn_rom_v2`, CC BY-SA 4.0 parts) is used; Banglish next-word pairs are learned from the owner's own typing. NOTICE must say the word counts
    may include counts from the data owner's own chats.
  - Pair lists used: only `en_bigrams/trigrams` and `bn_bigrams/trigrams` (Leipzig CC BY 4.0, Tatoeba blend). NEVER `*_v2/_v3` (OpenSubtitles, non-commercial).
  - Strip is Gboard style, in every language: suggestions (also next-word ones, after a space) show whenever there are any; a small arrow button at the
    left swaps the strip to the icons and back (one new button). This extends D23 (icons hidden while suggestions show) from "a Bangla word is open" to
    all suggestions; the arrow is the way back to the icons. With no suggestions (empty field, password) the strip is the icon row as today, without the arrow.
  - Size tier "Standard" (about 6 MB in the APK): 150k English words, 150k Bangla words, all Banglish words, all pair lists.
- D42 2026-11-17 Prediction (L2, claude-l), more owner answers:
  - Bangla form: predicted and learned Bangla words are committed in the precomposed form (য় = U+09DF, ড় = U+09DC, ঢ় = U+09DD) exactly like Avro. The packer converts
    the dataset's two-code-point form; lookups normalize the typed text the same way.
  - Learning is ON by default, local only (never leaves the phone). Never learns in password, e-mail or URL fields, nor where the app sets
    "no personalized learning" or "no suggestions". Settings (claude-g) get a "Learn from my typing" switch and a "Clear learned words" button.
  - Offensive words from the dataset's block lists are removed from the packed data (never suggested). A word the owner types often can still be learned.
- D43 2026-11-17 Clipboard and exposure rules (E1): copied secrets are cleared from the clipboard after 30 seconds, only if the clipboard still holds our copy
  (timeout becomes a setting). A copied secret is never saved in the keyboard's own clipboard history. Copies are marked sensitive where Android supports it
  (Android 13+, to verify). Vault and unlock screens block screenshots and the recents preview. Passwords are shown masked, revealed only on tap, and never
  shown in the picker. The vault code makes no network calls and has no telemetry (a CI check; D10's Offline mode stays independent). The owner agreed to
  these recommendations and may revisit them.
- D44 2026-11-17 Vault process (E1): the vault engine runs in its own Android process; the keyboard talks to it through a bound service. Reason: the typing
  process stays light and the opened database and keys live outside it. Revisit after the feasibility check shows real memory/size numbers. The owner agreed
  to the recommendation and may revisit it.
