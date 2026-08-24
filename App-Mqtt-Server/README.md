# App-Mqtt-Server

Netty-based MQTT broker for MyFramework. The implementation follows small, independently tested milestones.

## Implementation status

| Milestone | Scope | Status |
| --- | --- | --- |
| M1 | Module scaffold, configuration binding, Spring lifecycle and test foundation | Complete |
| M2 | TCP server and Netty MQTT codec pipeline | Complete |
| M3 | CONNECT and CONNACK | Complete |
| M4 | PING, DISCONNECT and Keep Alive | Complete |
| M5 | Client ID registry and connection takeover | Complete |
| M6 | In-memory sessions and Clean Session semantics | Complete |
| M7 | Exact-topic SUBSCRIBE/SUBACK and session subscription state | Complete |
| M8 | Non-retained QoS 0 PUBLISH and exact-topic live routing | Complete |
| M9 | `+`/`#` wildcard subscriptions and indexed topic matching | Complete |
| M10 | QoS 0 retained messages and pluggable retained persistence | Complete |
| M11 | QoS 1 PUBLISH/PUBACK, in-flight state and persistent-session redelivery | Complete |
| M12 | QoS 2 PUBREC/PUBREL/PUBCOMP and persistent-session state recovery | Complete |
| M13 | UNSUBSCRIBE/UNSUBACK and persistent-session subscription removal | Complete |
| M14 | Will Message validation, abnormal-disconnect publication, QoS and retain | Complete |
| M15 | Pluggable CONNECT authentication and simple username/password implementation | Complete |
| M16 | Pluggable publish/subscribe authorization and simple topic ACL rules | Complete |
| M17 | Pluggable persistent-session store and SQLite broker-restart recovery | Complete |
| M18+ | Resource limits, operational hardening and remaining broker features | Pending |

The current module supports MQTT 3.1.1 connection establishment, pluggable authentication and topic authorization, PING, DISCONNECT, Keep Alive, Client ID takeover, persistent-session recovery across broker restarts, exact and wildcard subscription management, QoS 0/1/2 publishing, persistent-session redelivery, retained messages and Will Message. Resource limits and later operational features stay unsupported until their milestones are complete.

## Architecture

- `MqttServer` owns the Netty server lifecycle.
- `MqttChannelInitializer` installs the MQTT codec and one `MqttConnection` per channel.
- `MqttConnection` is the single entry point for packets and connection-level state.
- `MqttAuthenticator` verifies CONNECT credentials before a broker session is created.
- `MqttAuthorizer` verifies Will, publish and subscription topics before broker state changes.
- `MqttBroker` is the single owner of cross-connection client and session state.
- `MqttSession` holds subscriptions, packet identifiers and QoS 1/2 protocol state that can survive a persistent client disconnect.
- `MqttSessionStore` persists complete persistent-session snapshots; SQLite is the default and memory is available for tests or ephemeral deployments.
- `MqttWillMessage` belongs to one connection and is routed by `MqttBroker` only after an abnormal disconnect.
- `MqttRetainedMessageStore` abstracts broker-level retained persistence; SQLite is the default implementation and memory is available as an alternative.

Online packet flow is `Netty -> MqttConnection -> MqttBroker`. Future subscription, publish and QoS milestones should preserve this boundary.

## Authentication

Authentication defaults to `anonymous` for backward compatibility. Select the built-in username/password implementation with:

```yaml
mqtt:
  server:
    authentication:
      type: simple
      simple:
        users:
          device-a: ${MQTT_DEVICE_A_PASSWORD}
```

The simple implementation reads users once at startup. Keep passwords in environment-backed configuration rather than committing them to the repository. A custom Spring `MqttAuthenticator` bean can replace the default implementation.

## Topic authorization

Authorization defaults to `allow-all`. The built-in `simple` implementation uses allow rules and denies unmatched operations:

```yaml
mqtt:
  server:
    authorization:
      type: simple
      simple:
        rules:
          - username: device
            client-id: device-a
            publish-topic-filters:
              - devices/device-a/telemetry/#
            subscribe-topic-filters:
              - devices/device-a/commands/#
```

Either identity field can be omitted; specified fields must match. Publish filters match concrete topic names, while subscribe filters define the maximum filter scope a client may request. A custom Spring `MqttAuthorizer` bean can replace the default implementation.

## Retained persistence

Persistence configuration is grouped first by purpose and then by implementation:

```yaml
mqtt:
  server:
    persistence:
      session:
        type: sqlite
        sqlite:
          database-path: data/mqtt-session.db
      retained-message:
        type: sqlite
        sqlite:
          database-path: data/mqtt-retained.db
```

Each persistence purpose selects its implementation independently. Set either purpose's `type` to `memory` for ephemeral state that does not survive a broker restart.

## Verification

```shell
gradle :App-Mqtt-Server:test
```
