# App-Mqtt-Server

Netty-based MQTT broker for MyFramework. The implementation follows small, independently tested milestones.

## Implementation status

| Milestone | Scope | Status |
| --- | --- | --- |
| M1 | Module scaffold, configuration binding, Spring lifecycle and test foundation | Complete |
| M2 | TCP server and Netty MQTT codec pipeline | Complete |
| M3 | CONNECT and CONNACK | Complete |
| M4 | PING, DISCONNECT and Keep Alive | Pending |
| M5+ | MQTT 3.1.1 sessions, subscriptions, routing and QoS semantics | Pending |

The current module accepts MQTT 3.1.1 CONNECT packets and returns CONNACK. Packets after CONNECT remain unsupported until their milestones are complete.

## Verification

```shell
gradle :App-Mqtt-Server:test
```
