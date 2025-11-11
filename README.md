# Lattice: A Hierarchical Schedule Manager

## Project Overview

_Lattice_ is a hierarchical schedule/task manager that lets users quickly capture events by voice, organize them into nested sub-tasks, and manage schedules clearly with local persistence and an intuitive Compose UI.

Primary use cases
- Create, edit, delete, and query tasks (CRUD).
- Mark tasks as done or not done; completed items can be hidden from the main list.
- Build task trees with unlimited nesting (e.g., goal A split into sub-tasks B/C/D); expand/collapse levels.
- Use speech-to-text to draft a task quickly (e.g., “tomorrow 9 am review algorithms, three parts: derivation, practice, summary”).

## Build & Run

1. **Clone & Sync**
   - `git clone https://github.com/<your-org>/CS501-E1-Semester-Project.git`
   - Open the root project in Android Studio **Giraffe (2022.3.1+)** or newer. Let Gradle sync complete.
2. **Command-line build**
   - From the project root run `./gradlew assembleDebug` to produce a debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
3. **Run on device/emulator**
   - In Android Studio, select a device (API 28+) and press **Run**.
   - Or install the debug APK manually: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. **Data reset**
   - Clear app storage (Settings → Apps → Lattice → Storage) to wipe the DataStore-backed task/auth data during testing.

## Current Features

- **Authentication gate**: Simple login screen persists a token via DataStore and toggles navigation start destinations.
- **Hierarchical task management**: Create root tasks or sub-tasks, edit details, and delete cascades.
- **Task metadata**: Assign priority and optional time points; UI badges highlight status.
- **Completion control**: Toggle done state and auto-hide finished tasks to reduce clutter.
- **Persistent storage**: All tasks and auth state survive restarts through Jetpack DataStore serialization.
- **Compose-first UI**: Material 3 styling with responsive layout between task list and editor flows.

## Architecture

```
app/src/main/java/com/example/lattice
├── MainActivity.kt
├── data/
│   ├── AuthRepository.kt
│   └── TaskRepository.kt
├── domain/
│   └── model/
│       ├── AuthState.kt
│       ├── Task.kt
│       └── User.kt
├── ui/
│   ├── components/
│   │   └── TaskNode.kt
│   ├── navigation/
│   │   ├── NavGraph.kt
│   │   └── Routes.kt
│   ├── screens/
│   │   ├── EditorScreen.kt
│   │   ├── LoginScreen.kt
│   │   └── TaskListScreen.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── util/
│   └── Extensions.kt
└── viewModel/
    ├── AuthViewModel.kt
    └── TaskViewModel.kt
```

This mixed architecture keeps responsibilities separated while staying practical for a Compose-first app:
- **data** contains repositories and platform integrations (DataStore for auth/tasks persistence).
- **domain/model** holds pure Kotlin models shared across layers.
- **viewModel** exposes UI state and business logic to the composables.
- **ui** is feature-focused: reusable components, navigation graph, feature screens, and theme definitions.
- **MainActivity** wires everything together with a single-activity Compose setup (auth-aware navigation, Scaffold shell).

## Team Members
Yuwei Ye, Xian Gong