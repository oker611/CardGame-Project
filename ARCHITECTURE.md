# CardGame Architecture

## Current State (2026-06)

### Design Patterns Applied

| Pattern | Location | Purpose |
|---------|----------|---------|
| Strategy | `AIDecisionStrategy` → Greedy / MonteCarlo / Adaptive / Aggressive / Defensive / Normal | AI difficulty layers |
| Observer | `IEventBus` + `GameEventListener` → CardPlayed / PlayerPassed / TurnChanged / GameOver | Decouple UI / AI / Bluetooth |
| Factory | `AIStrategyFactory` | Centralized AI strategy creation with DI wiring |
| Adapter | `BluetoothEventRelay`, `AdaptiveAIDecisionStrategy` | Event↔Bluetooth bridge, MonteCarlo wrapper |
| Builder | `RuleConfig.Builder` | Immutable rule configuration |
| Singleton | `EventBus` (via Holder pattern, also implements `IEventBus`) | Thread-safe event dispatch |

### Interface Inventory

| Interface | Package | Implementations | Purpose |
|-----------|---------|-----------------|---------|
| `AIDecisionStrategy` | ai | 7 classes | AI strategy abstraction |
| `GameStateAccess` | engine | `GameEngine` | Decouple AIEventListener from GameEngine |
| `ILLMAnalyzer` | llm | `LLMAnalyzer` | LLM analysis abstraction |
| `IVivoLLMClient` | llm | `VivoLLMClient` | HTTP client abstraction |
| `IPatternRecognizer` | rule | `PatternRecognizer` | Card pattern recognition |
| `IPlayValidator` | rule | `PlayValidator` | Play validation rules |
| `IPhaseManager` | ai | `PhaseManager` | Game phase management |
| `ICandidateGenerator` | ai | `CandidateGenerator` | Play candidate generation |
| `IMonteCarloSimulator` | ai | `MonteCarloSimulator` | Monte Carlo evaluation |
| `IOpponentHandSampler` | ai | `OpponentHandSampler` | Opponent hand sampling |
| `IDealManager` | engine | `DealManager` | Card dealing lifecycle |
| `ITurnManager` | engine | `TurnManager` | Turn rotation |
| `ISettlementManager` | engine | `SettlementManager` | Game over detection |
| `IEventBus` | event | `EventBus` | Event dispatch |

### Dependency Injection

```
CardGameApplication (instance Service Locator, not static)
  └── GameEngine(IDealManager, ITurnManager, ISettlementManager, IEventBus)
        └── TurnManager(IEventBus)

AIStrategyFactory (static factory)
  ├── GreedyAIDecisionStrategy(RuleConfig, IPatternRecognizer, IPlayValidator)
  ├── MonteCarloAIDecisionStrategy(RuleEngine, ICandidateGenerator, ...)
  └── AdaptiveAIDecisionStrategy(MonteCarloAIDecisionStrategy)

GameController
  ├── HumanStyleAnalyzer(ILLMAnalyzer) — per-instance ExecutorService
  └── OpponentStyleAnalyzer(ILLMAnalyzer)
```

### Exception Handling

- All `catch (Exception)` in production code eliminated
- `NetworkGameBridge`: `catch (JsonSyntaxException)` for Gson parsing
- UI Activities: `catch (ActivityNotFoundException)` for intent launching
- `HumanStyleAnalyzer`: `catch (IOException)` for LLM failure, fallback to local analysis
- No swallowed exceptions — all catch blocks notify or log
- `BluetoothReceiver`: explicit `JsonSyntaxException` + `IOException` + `RuntimeException`

### Code Quality

- 0 `System.out.println` calls in production
- 0 reflection usage
- 0 generic `catch (Exception)` in business logic
- 0 static mutable state via `CardGameApplication` (converted to instance fields)
- 0 static global executor pools (`HumanStyleAnalyzer` now per-instance)
- `AIPlayer.java` removed (324 lines of unused legacy code)
- `Thread.sleep` instances annotated with purpose comments

### Test Coverage: 122 tests, 0 failures

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
