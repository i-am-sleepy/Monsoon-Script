package com.monsoonscript.keyboard.voice

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * Google voice diagnostics (G5): Gboard dictates on the owner's phone but the keyboard's Google voice did nothing, so this page shows what the phone has
 * (speech services, the default one, permissions), runs one recognition with a chosen service and prints every event and error code, and copies it all as a report.
 */
class VoiceDiagActivity : Activity() {

    private lateinit var ui: SettingsUi
    private lateinit var content: LinearLayout
    private lateinit var logView: TextView
    private val log = StringBuilder()
    private val main = Handler(Looper.getMainLooper())
    private var services: List<ResolveInfo> = emptyList()
    private var lang = VoiceLang.ENGLISH
    private var choice = 0 // 0 = automatic (what the keyboard uses), 1 = the phone's default, 2.. = each speech service found
    private var rec: SpeechRecognizer? = null
    private var t0 = 0L
    private var rmsMax = -100f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SettingsUi(this)
        val page = ui.page(getString(R.string.voice_diag_title)) { finish() }
        content = page.content
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(48, 16, 48, 32)
            setTextIsSelectable(true)
        }
        setContentView(page.root)
        build()
    }

    override fun onDestroy() {
        stopTest()
        super.onDestroy()
    }

    private fun gcomp(): ComponentName? =
        services.firstOrNull { it.serviceInfo.packageName == GOOGLE_APP }?.let { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }

    private fun choiceLabel(): String = when (choice) {
        0 -> "automatic (" + (gcomp()?.let { "Google app" } ?: "phone default") + ")"
        1 -> "phone default"
        else -> services.getOrNull(choice - 2)?.serviceInfo?.let { it.packageName + "/" + it.name.substringAfterLast('.') } ?: "?"
    }

    private fun report(): String {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val def = try {
            Settings.Secure.getString(contentResolver, "voice_recognition_service") ?: "(not set)"
        } catch (e: Exception) {
            "(unreadable)"
        }
        val googleVersion = try {
            packageManager.getPackageInfo(GOOGLE_APP, 0).versionName
        } catch (e: Exception) {
            "not installed or not visible"
        }
        return buildString {
            append("Monsoon Script voice diagnostics\n")
            append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Microphone permission: ${if (mic) "granted" else "NOT granted"}\n")
            append("isRecognitionAvailable: ${SpeechRecognizer.isRecognitionAvailable(this@VoiceDiagActivity)}\n")
            append("Default speech service: $def\n")
            append("Google app ($GOOGLE_APP): $googleVersion\n")
            append("Speech services found: ${services.size}\n")
            for (s in services) append("  ${s.serviceInfo.packageName}/${s.serviceInfo.name}\n")
        }
    }

    private fun build() {
        services = packageManager.queryIntentServices(Intent("android.speech.RecognitionService"), 0)
        content.removeAllViews()
        with(ui) {
            content.add(note(report()))
            content.add(buttonRow("Language: " + (if (lang == VoiceLang.ENGLISH) "English (en-US)" else "Bangla (bn-BD)"), "Tap to switch") {
                lang = if (lang == VoiceLang.ENGLISH) VoiceLang.BANGLA else VoiceLang.ENGLISH
                build()
            })
            content.add(buttonRow("Speech service: " + choiceLabel(), "Tap to try the next one") {
                choice = (choice + 1) % (services.size + 2)
                build()
            })
            content.add(buttonRow("Start test", "Press it, wait for \"ready for speech\" below, then say a sentence") { startTest() })
            content.add(buttonRow("Copy the report", "Then paste it in the chat") { copyReport() })
        }
        content.addView(logView)
        logView.text = log
    }

    private fun line(text: String) {
        log.append("%5d ms  %s\n".format(System.currentTimeMillis() - t0, text))
        logView.text = log
    }

    private fun copyReport() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("voice report", report() + "\nTest log (" + lang.tag + ", " + choiceLabel() + "):\n" + log))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun stopTest() {
        main.removeCallbacks(timeout)
        rec?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (e: Exception) {
            }
        }
        rec = null
    }

    private val timeout = Runnable {
        line("nothing more happened for 12 s: giving up")
        stopTest()
    }

    private fun startTest() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
            return
        }
        stopTest()
        log.setLength(0)
        t0 = System.currentTimeMillis()
        rmsMax = -100f
        val comp: ComponentName? = when (choice) {
            0 -> gcomp()
            1 -> null
            else -> services.getOrNull(choice - 2)?.serviceInfo?.let { ComponentName(it.packageName, it.name) }
        }
        line("test: language ${lang.tag}, service ${choiceLabel()}")
        val r = try {
            if (comp != null) SpeechRecognizer.createSpeechRecognizer(this, comp) else SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            line("createSpeechRecognizer failed: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = line("ready for speech: the microphone is open, speak now")

            override fun onBeginningOfSpeech() = line("beginning of speech")

            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > rmsMax) rmsMax = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() = line("end of speech (loudest level seen: %.1f dB)".format(rmsMax))

            override fun onError(error: Int) {
                line("ERROR $error: " + errorName(error))
                main.removeCallbacks(timeout)
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                line("RESULT: " + (list?.joinToString(" | ") ?: "(none)"))
                main.removeCallbacks(timeout)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                line("partial: " + (partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = line("event $eventType")
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        try {
            r.startListening(i)
            line("startListening called")
        } catch (e: Exception) {
            line("startListening failed: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        main.postDelayed(timeout, 12_000L)
    }

    private fun errorName(code: Int) = when (code) {
        1 -> "network timeout"
        2 -> "network"
        3 -> "audio"
        4 -> "server"
        5 -> "client"
        6 -> "no speech heard (timeout)"
        7 -> "no match"
        8 -> "recognizer busy"
        9 -> "insufficient permissions"
        10 -> "too many requests"
        11 -> "server disconnected"
        12 -> "language not supported"
        13 -> "language unavailable"
        else -> "other"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            build()
            startTest()
        } else {
            Toast.makeText(this, R.string.voice_perm_denied, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REQUEST = 73
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}
