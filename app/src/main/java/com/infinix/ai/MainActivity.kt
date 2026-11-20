package com.infinix.ai

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val api = "https://pmvpswrsorbhbroqqndd.supabase.co/functions/v1/infinix"
    private val reqVoice = 101

    private lateinit var log: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var langBtn: Button
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lang = "en-US"
    private var pin = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        pin = prefs().getString("pin", "") ?: ""

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#05080F"))
        root.setPadding(dp(12), dp(12), dp(12), dp(12))

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "INFINIX AI"
        title.setTextColor(Color.parseColor("#35E0FF"))
        title.textSize = 20f
        title.letterSpacing = 0.12f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        langBtn = Button(this)
        langBtn.text = "EN"
        langBtn.setOnClickListener {
            lang = if (lang == "bn-BD") "en-US" else "bn-BD"
            langBtn.text = if (lang == "bn-BD") "বাং" else "EN"
            addMsg(if (lang == "bn-BD") "Voice: Bangla" else "Voice: English", 2)
        }
        header.addView(langBtn)
        val pinBtn = Button(this)
        pinBtn.text = "PIN"
        pinBtn.setOnClickListener { askPin() }
        header.addView(pinBtn)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // chat log
        scroll = ScrollView(this)
        log = LinearLayout(this)
        log.orientation = LinearLayout.VERTICAL
        log.setPadding(0, dp(8), 0, dp(8))
        scroll.addView(log, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // bottom bar
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val mic = Button(this)
        mic.text = "🎤"
        mic.textSize = 22f
        mic.setOnClickListener { listen() }
        bar.addView(mic, LinearLayout.LayoutParams(dp(64), dp(56)))
        input = EditText(this)
        input.hint = "Likho ba mic chap..."
        input.setHintTextColor(Color.parseColor("#7D93B5"))
        input.setTextColor(Color.parseColor("#E6F1FF"))
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, _, _ -> send(input.text.toString()); true }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sendBtn = Button(this)
        sendBtn.text = "➤"
        sendBtn.setOnClickListener { send(input.text.toString()) }
        bar.addView(sendBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        addMsg("INFINIX AI ready. Mic chap ba likho.", 2)
        if (pin.isEmpty()) askPin()
    }

    private fun prefs() = getSharedPreferences("infinix", MODE_PRIVATE)

    // kind: 0 = me, 1 = ai, 2 = system note
    private fun addMsg(text: String, kind: Int): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#E6F1FF"))
        tv.textSize = 16f
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(16).toFloat()
        when (kind) {
            0 -> bg.setColor(Color.parseColor("#12305A"))
            1 -> bg.setColor(Color.parseColor("#0C1322"))
            else -> {
                bg.setColor(Color.TRANSPARENT)
                tv.setTextColor(Color.parseColor("#7D93B5"))
                tv.textSize = 13f
            }
        }
        tv.background = bg
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(5), 0, dp(5))
        lp.gravity = when (kind) {
            0 -> Gravity.END
            1 -> Gravity.START
            else -> Gravity.CENTER_HORIZONTAL
        }
        log.addView(tv, lp)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        return tv
    }

    private fun askPin() {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        e.hint = "PIN"
        AlertDialog.Builder(this)
            .setTitle("INFINIX AI PIN")
            .setView(e)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                pin = e.text.toString().trim()
                prefs().edit().putString("pin", pin).apply()
            }
            .show()
    }

    // ---------- voice in ----------
    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo...")
        try {
            startActivityForResult(i, reqVoice)
        } catch (e: ActivityNotFoundException) {
            addMsg("Ei phone-e voice input app nei. Google app update koro.", 2)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVoice && resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) send(t)
        }
    }

    // ---------- voice out ----------
    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
    }

    private fun speak(t: String) {
        if (!ttsReady) return
        val bangla = t.any { it in '\u0980'..'\u09FF' }
        val loc = if (bangla) Locale("bn", "BD") else Locale.US
        val r = tts?.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "infinix")
    }

    // ---------- Spotify ----------
    private fun spotifyQuery(msg: String): String? {
        val t = msg.lowercase(Locale.ROOT)
        val hasSpotify = Regex("spotify|স্পটিফাই").containsMatchIn(t)
        val wantsPlay = Regex("\\b(play|chalao|chalau|chala|bajao|baja)\\b|বাজাও|চালাও|প্লে").containsMatchIn(t)
        val music = Regex("\\b(song|songs|gaan|gan|music)\\b|গান|সং").containsMatchIn(t)
        if (!hasSpotify && !(wantsPlay && music)) return null
        var q = t.replace(Regex("[-–—,.!?]"), " ")
        q = q.replace(Regex("স্পটিফাই|বাজাও|চালাও|প্লে|গান|সং|হেই|ইনফিনিক্স|একটা|আমাকে|দাও|করো"), " ")
        q = q.replace(Regex("\\b(hey|hello)\\s+(infinix|infinity)(\\s+ai)?\\b"), " ")
        q = q.replace(Regex("\\b(infinix|infinity)(\\s+ai)?\\b"), " ")
        q = q.replace(Regex("\\bhey\\s+jarvis\\b"), " ")
        q = q.replace(Regex("\\bspotify(\\s+(e|te|er))?\\b"), " ")
        q = q.replace(Regex("\\b(open|kholo|khulo|khol|play|please|plz|chalao|chalau|chala|bajao|baja|song|songs|gaan|gan|music|kono|ekta|ekti|amake|koro|kor|dao)\\b"), " ")
        return q.replace(Regex("\\s+"), " ").trim()
    }

    private fun playSpotify(q: String) {
        if (q.isEmpty()) {
            val li = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (li != null) {
                startActivity(li)
                addMsg("Spotify khulchi.", 1)
            } else {
                addMsg("Ei phone-e Spotify install nei.", 1)
            }
            return
        }
        val msg = "Spotify te \"$q\" chalachchi."
        addMsg(msg, 1)
        speak("Playing $q on Spotify")
        try {
            val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            i.setPackage("com.spotify.music")
            i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            i.putExtra(SearchManager.QUERY, q)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(q)))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            } catch (e2: Exception) {
                addMsg("Spotify kholte parlam na. Spotify install ache to?", 2)
            }
        }
    }

    // ---------- open any app ----------
    private val aliases = mapOf(
        "জিমেইল" to "gmail", "জিমেল" to "gmail", "ইউটিউব" to "youtube",
        "হোয়াটসঅ্যাপ" to "whatsapp", "হোয়াটসএপ" to "whatsapp", "ফেসবুক" to "facebook",
        "ক্রোম" to "chrome", "ক্লড" to "claude", "ক্লাউড" to "claude",
        "টেলিগ্রাম" to "telegram", "ক্যামেরা" to "camera", "সেটিংস" to "settings",
        "মেসেঞ্জার" to "messenger", "ইনস্টাগ্রাম" to "instagram", "গুগল" to "google",
        "ম্যাপস" to "maps", "ক্যালেন্ডার" to "calendar", "স্পটিফাই" to "spotify"
    )

    private fun appQuery(msg: String): String? {
        val t = msg.lowercase(Locale.ROOT)
        val wantsOpen = Regex("\\b(open|launch|start|kholo|khulo|khol)\\b|খোলো|খুলো|ওপেন").containsMatchIn(t)
        if (!wantsOpen) return null
        var q = t.replace(Regex("[-–—,.!?]"), " ")
        for ((k, v) in aliases) q = q.replace(k, " $v ")
        q = q.replace(Regex("হাই|হেই|ইনফিনিক্সি|ইনফিনিক্স|ইনফিনিটি|আই|ওপেন|খোলো|খুলো"), " ")
        q = q.replace(Regex("\\b(hey|hi|hello)\\s+(infinix|infinity)(\\s+ai)?\\b"), " ")
        q = q.replace(Regex("\\b(infinix|infinity)(\\s+ai)?\\b"), " ")
        q = q.replace(Regex("\\b(open|launch|start|kholo|khulo|khol|app|the|please|plz|koro|kor|dao|amar|amake)\\b"), " ")
        return q.replace(Regex("\\s+"), " ").trim()
    }

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\u0980-\u09FF]"), "")

    private fun launchApp(q: String): String? {
        if (q.isEmpty()) return null
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val want = norm(q)
        if (want.isEmpty()) return null
        var best: ResolveInfo? = null
        var bestScore = 0
        var bestLabel = ""
        for (ri in apps) {
            val labelRaw = ri.loadLabel(pm).toString()
            val label = norm(labelRaw)
            if (label.isEmpty()) continue
            val score = when {
                label == want -> 100
                label.startsWith(want) || (label.length >= 3 && want.startsWith(label)) -> 80
                label.contains(want) || (label.length >= 3 && want.contains(label)) -> 60
                else -> 0
            }
            if (score > bestScore) {
                best = ri
                bestScore = score
                bestLabel = labelRaw
            }
        }
        val b = best ?: return null
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(b.activityInfo.packageName, b.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(i)
            bestLabel
        } catch (e: Exception) {
            null
        }
    }

    // ---------- brain ----------
    private fun send(msg0: String) {
        val msg = msg0.trim()
        if (msg.isEmpty()) return
        input.setText("")
        addMsg(msg, 0)

        val sq = spotifyQuery(msg)
        if (sq != null) {
            playSpotify(sq)
            return
        }

        val aq = appQuery(msg)
        if (aq != null) {
            val opened = launchApp(aq)
            if (opened != null) {
                addMsg("$opened khulchi.", 1)
                speak("Opening $opened")
            } else {
                addMsg("\"$aq\" naam-er app khuje pai nai.", 1)
            }
            return
        }

        val wait = addMsg("...", 1)
        val usePin = pin
        thread {
            var reply = ""
            var unauthorized = false
            try {
                val c = URL(api).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.setRequestProperty("Content-Type", "application/json")
                c.connectTimeout = 20000
                c.readTimeout = 45000
                c.doOutput = true
                val body = JSONObject().put("message", msg).put("pin", usePin).toString()
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                unauthorized = code == 401
                reply = try {
                    JSONObject(text).optString("reply", "")
                } catch (e: Exception) {
                    ""
                }
                if (reply.isEmpty()) reply = "Server error (HTTP $code): " + text.take(200)
            } catch (e: Exception) {
                reply = "Error: " + e.javaClass.simpleName + ": " + (e.message ?: "")
            }
            runOnUiThread {
                wait.text = reply
                if (unauthorized) {
                    askPin()
                } else {
                    speak(reply)
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
