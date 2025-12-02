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

## Feature list with status

### Implemented Features

- **Authentication gate**: Simple login screen persists a token via DataStore and toggles navigation start destinations.
- **Hierarchical task management**: Create root tasks or sub-tasks, edit details, and delete cascades.
- **Task metadata**: Assign priority and optional time points; UI badges highlight status.
- **Completion control**: Toggle done state and auto-hide finished tasks to reduce clutter.
- **Persistent storage**: All tasks and auth state survive restarts through Jetpack DataStore serialization.
- **Compose-first UI**: Material 3 styling with responsive layout between task list and editor flows.
- **Speech-To-Text Integration**: Users can use a microphone to record their words and convert them into a sentence as the title and the description of tasks.

### In-progress Features
- Long-press FAB for Speech-to-Text
- Synchronization on Cloud
- More statistics
- More task information
- More settings about the UI

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

## Testing Strategy
- We decouple UI, navigation, and business state so that ViewModels can be unit-tested in isolation and screens can be UI-tested with synthetic data.
- We run manual functional tests on both emulator and physical device (Login → Task List → Editor)
- After each fix, we re-run the full user flow to verify that the bug is gone and that related ViewModel state behaves correctly, to prevent regressions.

## AI Usage Statement

### What tools were used, where, and how
We use ChatGPT on Chrome, asking how to improve code architecture and how to draw a flexible Gantt diagram with different types of calendars.

### Example prompts
You are a seasoned Android full-stack development engineer. Now, I will send you the code of the project I am currently developing. Please think about how to improve the code architecture and optimize the code. Your response should meet the following requirements:
1. When I upload the code to you, you only need to consider the code that has been transmitted so far. Do not consider the code that has not been sent yet. For example, if I only upload the UI-related code, you do not need to consider the untransmitted Data and Domain layer code. If you really need these codes, please let me know.
2. If you think that I need to add some code for the current business code, you should stop analyzing and inform me that additional code is needed. Wait until I complete all the code, then you can start analyzing.

### Analysis of helpfulness and limitations
For improving code architecture, AI is helpful. However, when I ask ChatGPT how to draw a flexible Gantt diagram with different types of calendars, it cannot give a perfect answer. The answer to my question used a variety of dependencies, and I could not understand the meaning of the code. Moreover, the code cannot run on my device, showing a huge number of bugs. Therefore, I gave up developing a calendar screen with a Gantt diagram.

### Understanding
When we have developed a basic app, AI can help us improve it. However, if we hope AI to create a complex app from zero, AI will not be helpful. The chances are that AI provides a skeleton, and then we develop the app based on the skeleton.

## Team Members
Yuwei Ye, Xian Gong
