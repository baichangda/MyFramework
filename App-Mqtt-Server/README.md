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
| M15+ | Authentication, authorization and remaining broker features | Pending |

The current module supports MQTT 3.1.1 connection establishment, PING, DISCONNECT, Keep Alive, Client ID takeover, in-memory session resumption, exact and wildcard subscription management, QoS 0/1/2 publishing, persistent-session redelivery, retained messages and Will Message. Authentication, authorization and later features stay unsupported until their milestones are complete.

## Architecture

- `MqttServer` owns the Netty server lifecycle.
- `MqttChannelInitializer` installs the MQTT codec and one `MqttConnection` per channel.
- `MqttConnection` is the single entry point for packets and connection-level state.
- `MqttBroker` is the single owner of cross-connection client and session state.
- `MqttSession` holds subscriptions, packet identifiers and QoS 1/2 protocol state that can survive a persistent client disconnect.
- `MqttWillMessage` belongs to one connection and is routed by `MqttBroker` only after an abnormal disconnect.
- `MqttRetainedMessageStore` abstracts broker-level retained persistence; SQLite is the default implementation and memory is available as an alternative.

Online packet flow is `Netty -> MqttConnection -> MqttBroker`. Future subscription, publish and QoS milestones should preserve this boundary.

## Retained persistence

Persistence configuration is grouped first by purpose and then by implementation:

```yaml
mqtt:
  server:
    persistence:
      retained-message:
        type: sqlite
        sqlite:
          database-path: data/mqtt-retained.db
```

Set `type: memory` to select the in-memory implementation; its state does not survive a broker restart.

## Verification

```shell
gradle :App-Mqtt-Server:test
```
