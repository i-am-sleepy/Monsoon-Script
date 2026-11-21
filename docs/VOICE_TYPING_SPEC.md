# Voice typing (G5, claude-g): spec and design record

Owner brief (2026-11-21): voice typing with two methods. (1) Google's own online recognizer, the way Gboard and Ridmik do it. (2) An optional offline local model:
English = NVIDIA Parakeet (the small one, about 90-100 MB), Bangla = `Sanjidh090/moonshine-base-bn` (Hugging Face). No language detection: the language follows the active layout
(English layout = English, Avro / Probhat / National = Bangla). The local model is an OPTIONAL download so people who skip it lose no storage. Priority: best accuracy balanced with speed.
Decisions are in DECISIONS.md D66. Status and steps: `status/claude-g.md`.

## What the owner sees
- The mic icon already reserved in the strip (D13) becomes the voice key.
- HOLD the mic: records while held; on release the text is transcribed and typed at the cursor. Sliding off the mic before release cancels.
- TAP the mic: a panel replaces the keys at the same height (like the emoji panel, D56). It shows an editable text box and a big mic button. Tapping the mic records; recording stops on a pause;
  tapping the mic again adds more text to the box. Tapping the box brings the normal keys back below it, so the text can be fixed with the keyboard's own keys (what is typed there never reaches
  the app until Insert). Insert types the text at the cursor, closes the panel and brings the keys back.
- Settings > Voice: engine per language (English, Bangla), default Google; a local model can be chosen only after it is installed. Download / import / delete buttons for each local model;
  Wi-Fi-only switch (default on); hidden "Voice test" page: one recording through every installed engine, shows the text and the time of each, side by side.

## Engines
- Google (default): Android `SpeechRecognizer` (the system's recognition service, normally Google's). No INTERNET permission is needed for it. Locales: English (en-US) and Bangla (bn-BD). Partial results fill the panel box live.
  Android 11+ needs a `<queries>` entry for `android.speech.RecognitionService` or `isRecognitionAvailable` can lie. If no recognition service exists, say so in the panel.
  The audio goes to Google, so D10's Offline mode switches this engine off, and a one-time notice is shown before the first use.
- Local (later, optional): offline, non-streaming (text appears per phrase, found with a voice-activity detector). Runs in its own Android process (D44 style) and unloads when idle so typing stays light.
  The runtime (sherpa-onnx or ONNX Runtime, arm64 only) is in the APK (owner chose; its real size is measured in CI and reported). Model files are downloaded in the app or imported from a file,
  SHA-256 pinned, kept in app-private storage; the INTERNET permission is added only for this download (Offline mode blocks it).

## Permissions
RECORD_AUDIO (runtime): asked on the first mic use through a see-through screen (an IME cannot show the dialog itself, same idea as D37). INTERNET: only with the local download, one APK, no offline-only flavor.

## Local model facts (found 2026-11-21, NOT tested by us)
- Bangla: moonshine-base-bn, 61.5M params, MIT, only a 245 MB fp32 safetensors file (HF Transformers, no ONNX), its own BanglaBERT WordPiece tokenizer (stock Moonshine / sherpa-onnx decoding will not work as is),
  authors decode with 5 beams + repetition penalty. Authors' own test set (Lipi-Ghor-bn-882-SSTT): WER 21.54%, CER 10.79% (paper arXiv 2607.09598). Needs a CI job: export to ONNX, shrink (int8 or fp16),
  write the detokenizer, check real decoding. Go/no-go before anything ships. Bangla output must be normalized to the same form Avro commits (precomposed, D42).
- English: closest match = NVIDIA Parakeet TDT-CTC 110M, int8 sherpa-onnx build from the k2-fsa `asr-models` release (archive about 104 MB, CC-BY-4.0, adds punctuation and capitals). Owner to confirm it is the one he meant.
- Credits: both models listed on the licenses screen (Parakeet CC-BY-4.0 attribution needed).

## Build order (owner chose)
G5.3 Google voice + mic key (hold) + panel (tap) + RECORD_AUDIO screen + engine setting skeleton. G5.4 Settings > Voice and the Voice test page. G5.5 local model download/import. G5.6 local engines:
Bangla CI test first (go/no-go), then English Parakeet, then the Bangla engine if the test passes.

## Open
Exact Parakeet file; Bangla go/no-go; runtime APK size; whether the Google service on the owner's phones supports Bangla (bn-BD) and an offline pack (test on device).
