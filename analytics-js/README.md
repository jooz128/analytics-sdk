# @d1414k/analytics-js

A JavaScript analytics library for event tracking and user identification.

## Installation

Install the package using npm:

```sh
npm install @d1414k/analytics-js
```

Or using yarn:

```sh
yarn add @d1414k/analytics-js
```

## Usage

### Importing the Library

In a React project, import `Analytics` from `@d1414k/analytics-js`:

```javascript
"use client";
import React, { useEffect } from "react";
import Analytics from "@d1414k/analytics-js";

function AnalyticsTest() {
  useEffect(() => {
    Analytics.init({
      writeKey: "your-write-key",
      endpoint: "http://localhost:8080/v1/batch",
      batchSize: 10,
      flushInterval: 2000,
    });
  }, []);

  return (
    <div className="flex gap-4">
      <button
        onClick={() => {
          Analytics.track("test_event", { name: "John Doe", value: 42 });
        }}
      >
        Track Event
      </button>
      <button
        onClick={() => {
          Analytics.identify("user_123", {
            name: "John Doe",
            email: "john.doe@example.com",
            age: 30,
          });
        }}
      >
        Identify User
      </button>
    </div>
  );
}

export default AnalyticsTest;
```

## API

### `Analytics.init(config)`

Initializes the analytics library.

#### Parameters:

- `config.writeKey` (string, required) – Your analytics API key.
- `config.endpoint` (string, optional) – API endpoint to send data. Default: `http://localhost:8080/v1/batch`
- `config.batchSize` (number, optional) – Number of events to batch before sending. Default: `20`
- `config.flushInterval` (number, optional) – Interval (in milliseconds) to flush events. Default: `30000`
- `config.enablePageTracking` (boolean, optional) – Enables automatic page tracking. Default: `false`

### `Analytics.track(eventName, properties)`

Tracks an event with associated properties.

#### Parameters:

- `eventName` (string, required) – Name of the event.
- `properties` (object, optional) – Key-value pairs of event properties.

#### Example:

```javascript
Analytics.track("button_click", { button: "subscribe", user: "user_123" });
```

### `Analytics.identify(userId, traits)`

Identifies a user with optional traits.

#### Parameters:

- `userId` (string, required) – Unique user ID.
- `traits` (object, optional) – Additional user attributes.

#### Example:

```javascript
Analytics.identify("user_123", {
  name: "John Doe",
  email: "john.doe@example.com",
  age: 30,
});
```

## License

MIT
