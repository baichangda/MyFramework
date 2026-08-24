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
| M10+ | Retained messages and QoS 1/2 delivery semantics | Pending |

The current module supports MQTT 3.1.1 connection establishment, PING, DISCONNECT, Keep Alive, Client ID takeover, in-memory session resumption, exact and wildcard subscriptions, and non-retained QoS 0 live publishing. Retained messages, offline delivery and QoS 1/2 publishing remain unsupported until their milestones are complete.

## Architecture

- `MqttServer` owns the Netty server lifecycle.
- `MqttChannelInitializer` installs the MQTT codec and one `MqttConnection` per channel.
- `MqttConnection` is the single entry point for packets and connection-level state.
- `MqttBroker` is the single owner of cross-connection client and session state.
- `MqttSession` holds state that can survive a persistent client disconnect.

Online packet flow is `Netty -> MqttConnection -> MqttBroker`. Future subscription, publish and QoS milestones should preserve this boundary.

## Verification

```shell
gradle :App-Mqtt-Server:test
```
