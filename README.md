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
- Graceful shutdown and batching support

## Example Configuration

```json
{
  "server": "localhost:1883",
  "username": "user",
  "password": "secret",
  "filters": [
    {
      "metric": "mqtt.value",
      "topics": [
        "sensors/+/temp",
        "devices/#",
        "home/lights/#>/state/value"
      ],
      "labelPatterns": [
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

- JVM 17+
- MQTT broker (e.g., Mosquitto)
- OpenTelemetry-compatible backend (e.g., Prometheus, OTLP Collector)

### Run the Service

```bash
export CONFIG='{"server": "...", ...}'  # Or load from file and export
java -jar mqtt-monitoring.jar
```

Logs will show connection, subscriptions, received values, and metric exports.

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
