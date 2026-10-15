# MQTT Monitoring

A lightweight Scala service for monitoring MQTT topics and exporting numeric values (including JSON extraction) to OpenTelemetry metrics.

## Features

- Connects to MQTT v3 brokers
- Subscribes to user-defined topic patterns (with support for `+`, `#`, and custom JSON selectors like `#>`)
- Parses and extracts numeric values from plain text or JSON payloads
- Applies regex-based value mappings and label extraction
- Supports OpenTelemetry gauge metric exporting
- Optional array support for JSON lists
- Configurable via a single JSON environment variable (`CONFIG`)
- Graceful shutdown

## Example Configuration

```json
{
  "server": "localhost:1883",
  "username": "user",
  "password": "secret",
  "filters": [
    {
      "metricName": "mqtt.value",
      "topics": [
        "sensors/+/temp",
        "devices/#",
        "home/lights/#>/state/value"
      ],
      "labelMatchers": [
        "sensors/(?<device>[^/]+)/(?<type>[^/]+)",
        "devices/(?<id>[^/]+)"
      ],
      "valueMappings": {
        "on": "1",
        "off": "0",
        "yes": "1",
        "no": "0",
        "val=(.*)": "$1"
      },
      "enableArrays": true
    }
  ]
}
```

## Getting Started

### Prerequisites

- JVM 21+
- MQTT broker (e.g., Mosquitto)
- OpenTelemetry-compatible backend (e.g., Prometheus, OTLP Collector)

### Run the Service

```bash
export CONFIG='{"server": "...", ...}'  # Or load from file and export
java -jar mqtt-monitoring-<version>.sh.bat  # executable fat jar (also runs directly on Unix)
```

A Docker image is published to `ghcr.io/lhns/mqtt-monitoring`.

Logs will show connection, subscriptions, received values, and metric exports.

### Configuration Options

Top level: `server`, `username`, `password`, `maxCardinality` (optional), `exportInterval` (optional, e.g. `"30s"`), `filters`.

Per filter (see below for how filters are chosen):

| Option | Default | Description |
| --- | --- | --- |
| `metricName` | `mqtt.value` | Name of the gauge metric |
| `topics` | required | Topic patterns to match |
| `discard` | `false` | Drop matching messages |
| `topicLabel` | `true` | Add the topic as `mqtt.topic` attribute |
| `labels` | `{}` | Static labels |
| `labelMatchers` | `[]` | Regexes with named groups, matched against the topic, that become labels |
| `valueMappings` | `{}` | Regex to value mappings for non-numeric payloads |
| `enableArrays` | `false` | Extract values from JSON arrays |
| `ignoreWarnings` | `false` | Suppress warnings for unmapped payloads |

### JSON field selection (`#>`)

A plain pattern such as `home/+/sensor` exports every field of a JSON payload. A pattern with a `#>` segment selects fields instead: the part before `#>` matches the MQTT topic, the part after it (with `+` and `#`) matches the field path.

- Selected fields must be numbers, booleans (exported as 1/0) or strings with a matching `valueMappings` entry.
- Selecting an object yields nothing; name its leaf fields or use `+`/`#`.
- `#` directly before `#>` matches any number of topic levels (`home/#/#>/temperature`); elsewhere `#` must be last.
- If a plain and a `#>` pattern of the same filter both match a topic, the plain one wins and everything is exported.
- Filters are tried in order. A filter with a matching plain pattern takes the whole message, and later filters never see it. A filter that only matches through `#>` patterns takes just the fields it selects (also when it is `discard`); later filters see the remaining fields.

Examples for the payload `{"temperature": 21.5, "humidity": 40, "battery": {"level": 80, "low": false}, "readings": [1, 2]}` on topic `home/livingroom/sensor`:

| Pattern | Exported fields |
| --- | --- |
| `home/+/sensor` | all fields |
| `home/+/sensor/#>/temperature` | `temperature` |
| `home/+/sensor/#>/battery/+` | `battery/level`, `battery/low` |
| `home/+/sensor/#>/+` | `temperature`, `humidity` |
| `home/+/sensor/#>/#` | all fields |
| `home/+/sensor/#>/readings/+` | `readings/0`, `readings/1` (only with `enableArrays`) |
| `home/+/sensor/#>/battery` | nothing (it is an object) |

For example, to export `temperature` and `pressure` as separate metrics, drop `battery` and keep everything else:

```json
"filters": [
  {"metricName": "temperature", "topics": ["home/+/sensor/#>/temperature"]},
  {"metricName": "pressure", "topics": ["home/+/sensor/#>/pressure"]},
  {"discard": true, "topics": ["home/+/sensor/#>/battery/#"]},
  {"topics": ["home/+/sensor/#>/#"]}
]
```

## OpenTelemetry Integration

This service uses the OpenTelemetry Java SDK to send gauge metrics. You can configure the export pipeline via standard OTel environment variables (e.g., `OTEL_EXPORTER_OTLP_ENDPOINT`).

## Development

### Build

```bash
sbt assembly
```

### Test

```bash
sbt test
```

## License

This project uses the Apache 2.0 License. See the file called LICENSE.
