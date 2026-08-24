# App-Mqtt-Server

Netty-based MQTT broker for MyFramework. The implementation follows small, independently tested milestones.

## Implementation status

| Milestone | Scope | Status |
| --- | --- | --- |
| M1 | Module scaffold, configuration binding, Spring lifecycle and test foundation | Complete |
| M2 | TCP server and Netty MQTT codec pipeline | Complete |
| M3 | CONNECT and CONNACK | Pending |
| M4 | PING, DISCONNECT and Keep Alive | Pending |
| M5+ | MQTT 3.1.1 sessions, subscriptions, routing and QoS semantics | Pending |

The current module accepts TCP connections and decodes MQTT packets. Until M3 is complete, incoming MQTT packets are closed without a protocol response.

## Verification

```shell
gradle :App-Mqtt-Server:test
```
