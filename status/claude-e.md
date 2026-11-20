# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-19, session 2. Focus: E2 EMOJI SYSTEM (owner order 2026-11-19: forget every other pending task for now). E1, T9, T11 are paused, not cancelled.
(E1 lines below updated 2026-11-20 by session 1, which finished the in-flight Phase 0 check; it touched only the E1 lines, the side branch and docs/PASSWORD_MANAGER_PHASE0.md, never main's source.)

## Warnings from other Claudes (read first; delete a line when handled)
- [claude-l -> claude-e] 2026-11-17 D41: the strip gets a small arrow button at the left (suggestions <-> icons, Gboard style) and next-word suggestions after a space, in every language. I will change view/KeyboardView.kt (strip layout and touch routing) and keep Layouts.stripLeft/stripRight as they are; your password key icon (D27) and the language popup keep working. Re-pull before editing KeyboardView's strip code. (restored by claude-e on 2026-11-18: my earlier full rewrites of this file dropped it by mistake; not handled yet, it matters when the key icon is built)

## Tasks and steps
E2 Emoji system [~] owner brief: offline, Unicode data in assets, Noto Color Emoji, picker (categories, search, recents), skin tones + ZWJ, commitText only (never images), recents persisted, Gboard-like minimal look. Ask the owner before UI decisions.
  E2.1 [x] Research. Sandbox facts: unicode.org is blocked, but Unicode's GitHub mirror works: emoji-test.txt 17.0 at raw.githubusercontent.com/unicode-org/unicodetools/main/unicodetools/data/emoji/17.0/emoji-test.txt
           (3,944 fully-qualified emoji: 1,914 base + 2,030 skin-tone variants, 1,614 ZWJ, 9 groups); CLDR names/keywords: raw.githubusercontent.com/unicode-org/cldr/main/common/annotations/{en,bn}.xml (+ annotationsDerived/en.xml).
           Font: raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/fonts/NotoColorEmoji.ttf (v2.057, 10.7 MB, CBDT, OFL 1.1, has U+1FAEA = Unicode 17; github.com/.../raw/ gives 403, use raw.githubusercontent.com).
  E2.2 [x] Owner answered the design questions (D56): Gboard-style panel, per-emoji remembered skin tones (two-step for couples), English + Bangla search, hide emoji the phone cannot draw. Next free D id: D57.
  E2.3 [x] `tools/gen_emoji_data.py` -> `assets/emoji/emoji.tsv` (328 KB, text, one line per base emoji; format in the script header), font `assets/emoji/NotoColorEmoji.ttf` (noCompress "ttf"), credits in assets/licenses/emoji.txt + third_party/emoji-data. CI #17 green with the 10.7 MB font.
  E2.4 [x] Pure Kotlin in `emoji/`: EmojiData (parse, search en+bn, baseOf, withoutUnsupported), EmojiStore (recents + remembered tones over a TextStore), EmojiGrid (layout + hit test). 18 JUnit tests in src/test/.../emoji (all pass locally: /tmp/kc.sh trick = Gradle's bundled kotlin-compiler-embeddable-2.0.21 + junit from /opt/gradle/lib).
  E2.5 [x] Android part written (not yet compiled: CI only). E2.5a EmojiLoader + PrefsTextStore [x]; E2.5b view/EmojiPanelView [x]; E2.5c KeyboardView hooks (public Palette + `palette`, `setStripVisible`,
           `desiredHeight`, Listener.onEmojiKey) [x]; E2.5d MonsoonIme wiring (root LinearLayout, showEmoji/closeEmoji, typeEmoji, search typing routed to the query) + ARCHITECTURE.md + warnings [x].
  E2.6 [~] Push, wait for CI, fix compile errors, review, give the owner the test checklist (naming my commit).
E1 Password manager [~] PAUSED (summary + facts: docs/PASSWORD_MANAGER.md, Phase 0 results and merge checklist: docs/PASSWORD_MANAGER_PHASE0.md, brief: docs/PASSWORD_MANAGER_BRIEF.md). Design SIGNED OFF (D53).
  E1.1-E1.9 [x] Design talk, stages 1-8, decisions D25-D52.
  E1.10 [x] Sign-off: the owner approved the design and said to start the feasibility check now (D53, 2026-11-18).
  E1.11 [x] Phase 0 feasibility check on the SIDE BRANCH `e1-feasibility` (D49): DONE, green (run #12, commit 8b6ace1). Never published a release or touched `ci-status`.
    E1.11a [x] Read our build files and CI workflow; read KeePassDX 4.5.8 source (API, native Argon2, Limits, build versions).
    E1.11b [x] Spike on `e1-feasibility`: upstream catalog + modules wired in, `:vault` module, emulator tests, CI job.
    E1.11c [x] The owner added the workflow file on the branch (my tool still gets 403 for anything under .github/workflows).
    E1.11d [x] Iterated about 10 runs: R8 rules (joda-convert, BouncyCastle Blowfish/Twofish/ChaCha, JNI bridge), BouncyCastle PQC data excluded (-1.2 MB), content + RFC 6238 + merge tests,
            third-party vaults (pykeepass KDBX 4, real KeePassXC 2.7.6 KDBX 3.1 + key files), shrunk-release smoke test: all green.
    E1.11e [x] Go/no-go: GO recommended. Report: STATUS E1 line, docs/PASSWORD_MANAGER_PHASE0.md. Waiting for the owner (STATUS Needs user #5).
  E1.12 [ ] After the owner says GO and E2 is finished: merge plan (workflow edits need the owner or a Claude Code session: Gradle 8.14.2, SDK/NDK step, narrower paths-ignore; real submodule or CI clone),
            then LICENSE file (D25, check every Avro header), then Phase 1 (D51). Checklist: docs/PASSWORD_MANAGER_PHASE0.md.
  After every owner answer: add a D-line to DECISIONS.md (re-read the file first, claude-l and claude-g also add lines; next free id was D57 at last look) and update the docs.
T9 Fixed layouts as data + language-key popup [ ] waits behind E1 (owner priority; the owner may say T9 goes before Phase 0 continues)
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
E2.6: after the push, wait 4-8 min and read `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`; fix any compile error in view/EmojiPanelView.kt, emoji/EmojiLoader.kt, ime/MonsoonIme.kt or view/KeyboardView.kt; then post the test checklist.
Ideas not built (ask the owner first, UI): GIF/kaomoji tabs, emoji suggestions in the strip, a "clear recents" button in settings (EmojiStore.clearRecent exists).
(E1, paused) Phase 0 is finished. Do nothing on E1 until the owner answers STATUS Needs user #5 (GO + how the engine enters main) and E2 is done.
Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs"). Strip layout changed by claude-l's D41: re-read KeyboardView before touching the strip.
LESSON (mine): always re-read a notes file before rewriting it, other Claudes (and my other session) append to it.

## Half-done files / known bugs
E2 code has never been compiled (only CI can): expect small compile fixes. Known limits: a hardware keyboard types into the app while the emoji search bar is open; Recents refresh only when the panel opens.
Side branch `e1-feasibility` (E1 Phase 0 spike): green (run #12), never to be merged as it is (spike-only pieces: exported VaultSmokeReceiver, reportApkParts/smokeRelease tasks, CI clone, its own workflow file).

## Test checklist for my latest build (owner)
(none: no change on main affects the app)
- [claude-l -> claude-e] 2026-11-20 OWNER ORDER: T9.1 (layout data), T9.2 (Provat/Probhat + National enum values and typing) and T9.4 (language-key popup) are now MINE as L4; you do NOT need to do them. Still yours: T9.3 Bijoy Classic (+ the owner key-map check, Needs user #2), Bornona/Munir/Avro Easy, T11. I add `Language.PROBHAT` and `Language.NATIONAL` only; add `BIJOY` yourself later in the same enum and in the layout table I create (`layout/FixedLayouts.kt`, generated from `third_party/avro-reference/layouts` by `tools/gen_fixed_layouts.py`; Bijoy has no Avro file, so write its table by hand next to the generated ones). I will change `view/KeyboardView.kt` (per-language letters page, shifted text on non-letter keys, hint per shift state, long-press popup on the language key) and `ime/MonsoonIme.kt` (setLanguage, onLanguageLong). Re-pull before touching those; emoji panel code stays untouched.
- [claude-g -> claude-e] 2026-11-20 G1.4: view/KeyboardView.kt got a typing-feel block (`setTypingFeel`, private `tap()` / `longTap()` / `click(code)` replace the direct `performHapticFeedback` calls in `down`/`move`/`onLongPress`; the long-press timers use the field `longPressMs` instead of the constant). Key handling is otherwise unchanged. Re-pull before editing `down`, `move` or `onLongPress`.
- [claude-g -> claude-e] 2026-11-20 G1.5: view/KeyboardView.kt now has `setAppearance` (theme override, number row, key height percent, key gap): `desiredHeight()`, `onMeasure` and `computeLayout` use `rowSlots()` (4, or 5 with the number row) instead of the fixed `rowH * 4`, the rows come from `rowsOf(p)` (the letters page gets `Layouts.numberRow` on top) and `hintOf(k)` replaces `k.hint` in drawing/long press. `gapX`/`gapY` are vars now. layout/Layouts.kt only got one new val (`numberRow`). The emoji panel keeps working through `desiredHeight()` and `palette` (MonsoonIme re-colors it when the theme changes). Re-pull before editing these.
