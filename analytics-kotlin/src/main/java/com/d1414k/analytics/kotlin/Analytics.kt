package com.d1414k.analytics.kotlin

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.concurrent.thread
import androidx.lifecycle.ProcessLifecycleOwner

enum class EventType {
    INVALID, IDENTIFY, TRACK
}

data class Event(
    val anonymousId: String,
    val userId: String?,
    val messageId: String,
    val type: EventType,
    val context: Map<String, Any> = emptyMap(),
    val originalTimestamp: String,
    val event: String? = null,
    val properties: Map<String, Any>? = null,
    val traits: Map<String, Any>? = null,
    val writeKey: String
)

class Analytics(val writeKey: String, val context: Context, block: Analytics.() -> Unit) {
    private val gson: Gson = GsonBuilder().create()
    private val queue = mutableListOf<Event>()
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("AnalyticsPrefs", Context.MODE_PRIVATE)
    private var userId: String? = getUserId()
    private val anonymousId: String = getAnonymousId()
    private val queueFile = File(context.filesDir, "analytics_queue.json")
    private val timer = Timer()
    private var eventContext: Map<String, Any>? = null

    // configurations
    var endpoint: String = "http://localhost:8080/v1/batch"
    var flushAt: Int = 20
    var flushInterval: Long = 30000
    var trackApplicationLifecycleEvents = false
    var trackDeepLinks = false
    var enableDebugLogs = false

    init {
        if (writeKey.isEmpty()) throw IllegalArgumentException("Write key cannot be empty")
        requireNotNull(context) { "Context must not be null." }
        this.block()
        setEventContext()
        restoreQueue()
        startFlushTimer()
        if (trackApplicationLifecycleEvents) {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            lifecycle.addObserver(LifecycleObserver(this, context))
        }
    }

    fun identify(userId: String, traits: Map<String, Any> = emptyMap()) {
        this.userId = userId
        saveUserId(userId)
        val event = getDefaultEvent().copy(type = EventType.IDENTIFY, traits = traits)
        addInQueue(event)
    }

    fun track(eventName: String, properties: Map<String, Any> = emptyMap()) {
        val event = getDefaultEvent().copy(
            type = EventType.TRACK,
            event = eventName,
            properties = properties
        )
        addInQueue(event)
    }

    private fun getAnonymousId(): String {
        val storedId = sharedPreferences.getString("anonymous_id", null)
        return storedId ?: UUID.randomUUID().toString().also {
            sharedPreferences.edit { putString("anonymous_id", it) }
        }
    }

    private fun getUserId(): String? {
        return sharedPreferences.getString("user_id", null)
    }

    private fun saveUserId(userId: String) {
        sharedPreferences.edit { putString("user_id", userId) }
    }

    private fun getDefaultEvent(): Event {
        return Event(
            anonymousId = anonymousId,
            userId = userId,
            messageId = UUID.randomUUID().toString(),
            type = EventType.INVALID,
            context = eventContext ?: emptyMap(),
            originalTimestamp = Instant.now().toString(),
            writeKey = writeKey
        )
    }

    private fun addInQueue(event: Event) {
        synchronized(queue) {
            queue.add(event)
            saveQueue()
            if (queue.size >= flushAt) {
                flush()
            }
        }
    }

    fun flush() {
        if (queue.isEmpty()) return
        val batch = synchronized(queue) {
            val batchData = queue.toList()
            queue.clear()
            saveQueue()
            batchData
        }

        thread {
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val jsonBody =
                    gson.toJson(mapOf("batch" to batch, "sentAt" to Instant.now().toString()))
                connection.outputStream.use { it.write(jsonBody.toByteArray()) }

                if (connection.responseCode !in 200..299) {
                    if (connection.responseCode == 401 || connection.responseCode == 429 || connection.responseCode >= 500) {
                        throw Exception("Failed to send events: ${connection.responseCode}")
                    } else { // 4xx error. no need to retry
                        Log.e("Analytics", "Failed to send events: ${connection.responseCode}")
                    }
                }
                if (enableDebugLogs) Log.i("Analytics", "event send: $batch")
            } catch (e: Exception) {
                Log.e("Analytics", "Failed to send events: ${e.message}")
                synchronized(queue) {
                    queue.addAll(batch)
                    saveQueue()
                }
            }
        }
    }

    private fun saveQueue() {
        synchronized(queue) {
            queueFile.writeText(gson.toJson(queue))
        }
    }

    private fun restoreQueue() {
        if (queueFile.exists()) {
            val type = object : TypeToken<List<Event>>() {}.type
            queue.addAll(gson.fromJson(queueFile.readText(), type) ?: emptyList())
        }
    }

    private fun startFlushTimer() {
        timer.schedule(object : TimerTask() {
            override fun run() {
                flush()
            }
        }, flushInterval, flushInterval)
    }


    @SuppressLint("HardwareIds")
    private fun setEventContext() {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val version = packageInfo.versionName
        val namespace = packageName
        val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) // TODO why segment has uuid
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val name = Build.DEVICE
        val type = "android"

        val osName = "Android"
        val osVersion = Build.VERSION.RELEASE

        val displayMetrics = context.resources.displayMetrics
        val screenDensity = displayMetrics.density
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork =
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled
        val cellularNetwork =
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE

        val locale = Locale.getDefault()
        val userAgent = System.getProperty("http.agent") ?: "Unknown User-Agent"
        val timeZone = TimeZone.getDefault().id // Example: "Asia/Kolkata"

        eventContext = mapOf(
            "library" to mapOf(
                "name" to "analytics-kotlin",
                "version" to "1.0.0"
            ),
            "app" to mapOf(
                "name" to appName,
                "version" to version,
                "namespace" to namespace,
                "build" to build
            ),
            "device" to mapOf(
                "id" to deviceId,
                "manufacturer" to manufacturer,
                "model" to model,
                "name" to name,
                "type" to type
            ),
            "os" to mapOf(
                "name" to osName,
                "version" to osVersion
            ),
            "screen" to mapOf(
                "density" to screenDensity,
                "height" to screenHeight,
                "width" to screenWidth
            ),
            "network" to mapOf(
                "wifi" to wifiNetwork,
                "bluetooth" to bluetoothEnabled,
                "cellular" to cellularNetwork
            ),
            "locale" to locale,
            "userAgent" to userAgent,
            "timezone" to timeZone
        )
    }
}

fun main(context: Context) {
    val analytics = Analytics("your-write-key", context) {
        endpoint = "http://localhost:8080/v1/batch"
        flushAt = 20
        flushInterval = 30000
    }
    analytics.identify("user123", mapOf("name" to "John Doe"))
    analytics.track("Button Click", mapOf("buttonId" to "signup"))
}
