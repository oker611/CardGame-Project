# CardGame Architecture

## Current State (2026-06)

### Decoupled Modules

```
GameActivity (1363 lines)
  ├── PropBarController (future) — prop bar enable/disable/toggle
  ├── CardRenderDelegate (future) — card rendering logic
  └── GameViewModel (future) — state management

BluetoothGateway (1776 lines → facade)
  ├── RoomController (121) — player slots, names, device mapping
  ├── ReliabilityManager (143) — heartbeat, ACK retry, timeout
  ├── ReconnectionHandler (110) — reconnect listener, validation
  └── NetworkGameBridge (330) — game message routing

MonteCarloAIDecisionStrategy (1418 lines, -413)
  ├── PatternAnalyzer (218) — pattern detection, strategy classification
  ├── CandidateScorer (124) — adaptive factor scoring
  ├── CandidateGenerator (288) — play candidate generation
  ├── PhaseManager (614) — game phase management
  └── OpponentHandSampler (101) — hand sampling
```

### Test Coverage: 92 tests, 0 failures

### CI: GitHub Actions (build + test on push/PR)

## Migration Path to Jetpack Compose

1. Extract `CardRenderDelegate` from GameActivity (pure rendering)
2. Extract `PropBarDelegate` from GameActivity (pure UI state)
3. Create `GameViewModel` with StateFlow
4. Replace `GameActivity` with `GameScreen` composable
5. Replace `RoomLobbyActivity` with `RoomLobbyScreen` composable
6. Remove legacy Activity code

## Message Queue Migration (BluetoothGateway)

Current: Synchronized `sendLock` for all channel operations
Target: `BlockingQueue<OutgoingMessage>` with dedicated sender thread

```java
// Target architecture
BlockingQueue<OutgoingMessage> sendQueue = new LinkedBlockingQueue<>();
// Sender thread: sendQueue.take() → writer.flush()
// Producer: sendQueue.put(message) — non-blocking
```
