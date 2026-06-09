---
An Android-based card game application featuring AI-powered gameplay and modern game engine architecture.

## 📋 Project Overview

This is a comprehensive Software Engineering Training Project that demonstrates professional development practices through a fully-featured card game application. The project integrates AI capabilities for intelligent opponent behavior and implements a robust game engine architecture.

**Language:** Java (100%)
**Platform:** Android
**Min SDK:** 24 | **Target SDK:** 34

## 🎮 Key Features

- **AI-Powered Gameplay** - LLM integration for intelligent AI opponents
- **Game Engine** - Custom game engine with event-driven architecture
- **Modern Architecture** - Clean separation of concerns with MVC/MVVM patterns
- **Network** - HTTP networking capabilities for multiplayer features
- **Type-Safe Data Models** - DTO pattern for data consistency
- **Event System** - Event-driven programming model

## 📁 Project Structure

```
CardGame-Project/
├── app/                              # Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/cardgame/
│   │   │   │   ├── ai/            # AI and machine learning components
│   │   │   │   ├── controller/    # Game controllers
│   │   │   │   ├── dto/           # Data Transfer Objects
│   │   │   │   ├── engine/        # Game engine core
│   │   │   │   ├── event/         # Event system
│   │   │   │   ├── llm/           # LLM/AI integration
│   │   │   │   ├── model/         # Data models
│   │   │   │   ├── network/       # Network operations
│   │   │   │   ├── rule/          # Game rules and logic
│   │   │   │   ├── ui/            # UI components
│   │   │   │   ├── util/          # Utility classes
│   │   │   │   └── CardGameApplication.java  # App entry point
│   │   │   ├── res/               # Android resources (layouts, strings, drawables)
│   │   │   ├── assets/            # Game assets
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                  # Unit tests
│   │   └── androidTest/           # Android instrumented tests
│   └── build.gradle               # App module configuration
├── docs/                           # Project documentation (UML diagrams, reports, etc.)
├── gradle/                         # Gradle wrapper files
├── build.gradle                    # Root project configuration
├── gradle.properties              # Gradle configuration
├── settings.gradle                 # Project settings
├── gradlew / gradlew.bat           # Gradle wrapper scripts
└── LICENSE                         # Project license
```

## 🏗️ Architecture

### Module Organization

| Module | Purpose |
|--------|---------|
| **ai** | AI decision-making and strategy |
| **controller** | Game flow and user action handling |
| **dto** | Data structures for network/storage |
| **engine** | Core game mechanics and game loop |
| **event** | Event dispatching and handling system |
| **llm** | Large Language Model integration |
| **model** | Domain-specific game objects |
| **network** | HTTP requests and API communication |
| **rule** | Game rules validation and enforcement |
| **ui** | Android UI components and views |
| **util** | Helper functions and utilities |

## 🛠️ Technology Stack

### Dependencies

- **Android Framework** - Core Android libraries
- **AndroidX** - Modern Android support libraries
- **Material Design** - Material UI components
- **Gson** - JSON serialization/deserialization (v2.10.1)
- **OkHttp3** - HTTP client (v4.12.0)
- **JUnit** - Unit testing

### Build Configuration

- **Build System:** Gradle with Android Plugin
- **Compile SDK:** 34
- **Build Tools:** 36.1.0
- **Java Compatibility:** 1.8

## 🚀 Getting Started

### Prerequisites

- Android Studio (Arctic Fox or newer)
- Java 8 or higher
- Android SDK 24 or higher

### Building the Project

1. **Clone the repository**
```bash
git clone https://github.com/oker611/CardGame-Project.git
cd CardGame-Project
```

2. **Configure API Keys**
- Set `DEEPSEEK_API_KEY` in `gradle.properties` or environment variables
- LLM integration requires valid API credentials

3. **Build the application**
```bash
./gradlew build
```

4. **Run on emulator or device**
```bash
./gradlew installDebug
```

## 🧪 Development Workflow

### Running Tests

```bash
# Unit tests
./gradlew test

# Android instrumented tests
./gradlew connectedAndroidTest
```

### Building Release

```bash
./gradlew assembleRelease
```

## ⚙️ Configuration

### Application ID

- **Package:** `com.example.cardgame`

### Gradle Properties

- JVM arguments: `-Xmx2048m -Dfile.encoding=UTF-8`
- AndroidX enabled for modern API compatibility
- R class namespacing enabled for library separation

## 🤖 AI Integration

- API keys should be stored securely and not committed to version control
- Use proper ProGuard rules for production builds (configured in `build.gradle`)
- Validate all network data before processing

## 🤝 Contributing

This is a training project. For contributions or improvements, please follow standard Git workflow practices and maintain code quality standards.

---

**Last Updated:** 2026
**Project Type:** Software Engineering Training
**Status:** Active Development
**Platform:** Active Development
