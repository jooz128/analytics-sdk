# Analytics Kotlin SDK

A lightweight and extensible Kotlin SDK for sending analytics events from Android applications. Inspired by Segment's analytics SDKs, this library allows you to easily track user activity and behavior in your mobile applications.

---

## 📦 Installation

Add the following to your `build.gradle`:

```groovy
dependencies {
    implementation 'com.d1414k.analytics:kotlin:1.0.0'
}
```

---

## 🚀 Getting Started

### Initialization

Initialize the Analytics client with your write key and configuration options:

```kotlin
val analytics = Analytics("1", applicationContext) {
    endpoint = "http://192.168.29.129:8080/v1/batch"
    flushAt = 1
    flushInterval = 10000
    trackApplicationLifecycleEvents = true
    enableDebugLogs = true
}
```

### Identify

Identify a user and associate them with traits:

```kotlin
val traits: Map<String, Any> = mapOf(
    "email" to "deepak@gmail.com"
)
analytics.identify("1234", traits)
```

### Track

Track events with optional properties:

```kotlin
val productDetails: Map<String, Any> = mapOf(
    "productId" to 123,
    "productName" to "Striped trousers"
)
analytics.track("View Product", productDetails)
```

---

## ⚙️ Configuration Options

| Option                            | Type    | Default                         | Description                                                                                         |
| --------------------------------- | ------- | ------------------------------- | --------------------------------------------------------------------------------------------------- |
| `endpoint`                        | String  | http\://localhost:8080/v1/batch | Your custom backend endpoint to receive batched analytics data                                      |
| `flushAt`                         | Int     | 20                              | Number of events before flush                                                                       |
| `flushInterval`                   | Int     | 30000                           | Interval (in ms) to automatically flush events                                                      |
| `trackApplicationLifecycleEvents` | Boolean | false                           | Automatically track app lifecycle events (App Installed, App opened, App Updated, App backgrounded) |
| `enableDebugLogs`                 | Boolean | false                           | Enables verbose logging for debugging                                                               |

---

## 📤 Flushing Events

Events are automatically batched and flushed based on your configuration. You can also manually flush:

```kotlin
analytics.flush()
```

---

## 🧪 Testing / Development

Run unit tests using:

```bash
./gradlew test
```

---

## 📄 License

MIT License

---
