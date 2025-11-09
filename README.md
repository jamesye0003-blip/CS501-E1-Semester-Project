# Lattice: A Hierarchical Schedule Manager

## Project Overview

_Lattice_ is a hierarchical schedule/task manager that lets users quickly capture events by voice, organize them into nested sub-tasks, and manage schedules clearly with local persistence and an intuitive Compose UI.

Primary use cases
- Create, edit, delete, and query tasks (CRUD).
- Mark tasks as done or not done; completed items can be hidden from the main list.
- Build task trees with unlimited nesting (e.g., goal A split into sub-tasks B/C/D); expand/collapse levels.
- Use speech-to-text to draft a task quickly (e.g., “tomorrow 9 am review algorithms, three parts: derivation, practice, summary”).

## Team Members
Yuwei Ye, Xian Gong

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

