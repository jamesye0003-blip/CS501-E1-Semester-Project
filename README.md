# Lattice: A Hierarchical Schedule Manager

## Project Overview

_Lattice_ is a hierarchical schedule/task manager that lets users quickly capture events by voice, organize them into nested sub-tasks, and manage schedules clearly with local persistence and an intuitive Compose UI.

Primary use cases
- Create, edit, delete, and query tasks (CRUD).
- Mark tasks as done or not done; completed items can be hidden from the main list.
- Build task trees with unlimited nesting (e.g., goal A split into sub-tasks B/C/D); expand/collapse levels.
- Use speech-to-text to draft a task quickly (e.g., "tomorrow 9 am review algorithms, three parts: derivation, practice, summary").

## Screenshots

### Authentication Screens

**Login Screen**
![Login Screen](plots/login%20screenshot.png)
- Clean and modern login interface with Material 3 design
- Username and password authentication
- Support for offline login using locally stored credentials
- Quick navigation to registration screen

**Register Screen**
![Register Screen](plots/register%20screenshot.png)
- Simple registration form for new users
- Automatic login after successful registration
- Firebase Authentication integration for secure account creation

### Task Management Screens

**Task List Screen**
![Task List with Different Sorts](plots/task%20list%20screenshots%20with%20different%20sorts.png)
- Hierarchical task display with expand/collapse functionality
- Multiple sorting options: by Title, Priority, or Time
- Visual indicators for task priority and completion status
- Quick actions: add subtask, edit, delete, toggle done

**Task Filter**
![Task Filter](plots/task%20filter%20screenshot.png)
- Filter tasks by date: Today, This Week, This Month, or All
- Filter applies to entire task hierarchy
- Easy access through navigation drawer

**Editor Screen**
![Editor Screen](plots/editor%20screenshot.png)
- Comprehensive task editor with all fields
- Priority selection (None, Low, Medium, High)
- Date and time scheduling with timezone support
- File and image attachment support
- Clean Material 3 form design

**Editor with Speech-to-Text**
![Editor with Speech-to-Text](plots/editor%20screenshot%20with%20speech.png)
- Voice input for quick task creation
- Microphone button for title and description fields
- Real-time transcription from Google Cloud Speech-to-Text API
- Seamless integration with form fields

### Additional Features

**Calendar View**
![Calendar Screen](plots/calendar%20screenshot.png)
- Monthly calendar view of tasks
- Tasks organized by their due dates
- Quick task creation and editing from calendar
- Visual date indicators for scheduled tasks

**User Profile**
![User Profile Screen](plots/user%20profile%20screenshot.png)
- Today's overview: To-Do and Completed task counts
- Lifetime statistics: Total tasks and completion rate
- Quality metrics: On-time rate and postpone rate
- Daily Review: One-click postpone for unfinished tasks
- Manual sync trigger
- Dark mode toggle

## Technology Stack

- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM-C (Model-View-ViewModel-Coordinator) with Clean Architecture
- **Language**: Kotlin
- **Local Database**: Room Database (SQLite)
- **Local Storage**: DataStore (Preferences)
- **Cloud Services**: 
  - Firebase Authentication
  - Firebase Firestore (for task synchronization)
- **External APIs**: Google Cloud Speech-to-Text API
- **Navigation**: Jetpack Navigation Compose
- **Reactive Programming**: Kotlin Coroutines & Flow
- **Dependency Injection**: Manual (constructor injection)
- **Build System**: Gradle with Kotlin DSL

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

### Completed Features (Recent Updates)
- **Cloud Synchronization**: Bidirectional sync with Firebase Firestore using incremental pull and batch push
- **Calendar View**: Calendar screen to view tasks organized by date
- **User Profile**: Statistics dashboard with today's overview, lifetime stats, and completion rates
- **Task Filtering & Sorting**: Filter tasks by date (Today, This Week, This Month, All) and sort by Title, Priority, or Time
- **Task Attachments**: Support for file and image attachments in tasks
- **Daily Review**: Postpone unfinished today's tasks to tomorrow with one click
- **Manual Sync**: Trigger synchronization with Firebase manually from profile screen

### Future Enhancements
- Long-press FAB for Speech-to-Text
- More advanced statistics and analytics
- Task templates and recurring tasks
- More UI customization options

## Architecture

Lattice follows **Clean Architecture** principles with **MVVM-C** (Model-View-ViewModel-Coordinator/Navigation) pattern, ensuring clear separation of concerns and maintainability.

### Architecture Overview

![MVVM Architecture](plots/MVVM%20architecture.png)

### Project Structure

```
app/src/main/java/com/example/lattice
├── MainActivity.kt                    # Single Activity, Compose setup
├── data/                              # Data Layer - Implementations
│   ├── DefaultAuthRepository.kt      # Auth repository implementation
│   ├── DefaultTaskRepository.kt      # Task repository implementation
│   ├── local/                         # Local data sources
│   │   ├── datastore/
│   │   │   ├── AuthDataStore.kt      # Authentication state storage
│   │   │   └── SyncCursorStore.kt    # Sync cursor management
│   │   └── room/                      # Room database
│   │       ├── dao/
│   │       │   ├── TaskDao.kt
│   │       │   └── UserDao.kt
│   │       ├── db/
│   │       │   ├── AppDatabase.kt
│   │       │   └── Converters.kt
│   │       ├── entity/
│   │       │   ├── TaskEntity.kt
│   │       │   └── UserEntity.kt
│   │       └── mapper/
│   │           └── TaskMapper.kt
│   ├── remote/                        # Remote data sources
│   │   └── firebase/
│   │       └── FirebaseTaskSyncManager.kt  # Firestore sync manager
│   └── speech/
│       └── GoogleSpeechToTextService.kt     # Google STT API integration
├── domain/                            # Domain Layer - Business Logic
│   ├── model/                         # Domain models
│   │   ├── Attachment.kt
│   │   ├── AuthState.kt
│   │   ├── Task.kt
│   │   ├── TimePointExtensions.kt
│   │   └── User.kt
│   ├── repository/                    # Repository interfaces
│   │   ├── AuthRepository.kt
│   │   └── TaskRepository.kt
│   ├── service/                       # Service interfaces
│   │   └── SpeechToTextService.kt
│   ├── sort/                          # Sorting utilities
│   │   ├── TaskSorter.kt
│   │   └── TaskSortOrder.kt
│   └── time/                          # Time utilities
│       ├── TimeConverter.kt
│       ├── TimePointUtils.kt
│       └── TimeZoneData.kt
├── ui/                                # UI Layer - Presentation
│   ├── components/                    # Reusable UI components
│   │   ├── AttachmentBottomSheet.kt
│   │   ├── ScheduleBottomSheet.kt
│   │   ├── TaskListCard.kt
│   │   ├── TaskNode.kt
│   │   └── UploadOptionsBottomSheet.kt
│   ├── navigation/                    # Navigation
│   │   ├── NavGraph.kt                # Navigation graph
│   │   └── Routes.kt                  # Route definitions
│   ├── screens/                        # Feature screens
│   │   ├── CalendarScreen.kt
│   │   ├── EditorScreen.kt
│   │   ├── LoginScreen.kt
│   │   ├── RegisterScreen.kt
│   │   ├── TaskListScreen.kt
│   │   └── UserProfileScreen.kt
│   └── theme/                         # Material 3 theme
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── util/
│   └── Extensions.kt                  # Utility extensions
└── viewModel/                         # ViewModel Layer
    ├── AuthViewModel.kt              # Authentication state management
    ├── EditorViewModel.kt             # Speech-to-text state management
    └── TaskViewModel.kt               # Task state management
```

### Architecture Layers

#### 1. **UI Layer** (Presentation)
- **Screens**: 6 main screens (Login, Register, TaskList, Calendar, Editor, Profile)
- **Components**: Reusable Compose components
- **Navigation**: Jetpack Navigation Compose with NavGraph
- **Theme**: Material 3 theming with dark mode support

#### 2. **ViewModel Layer** (State Management)
- **AuthViewModel**: Manages authentication state and operations
- **TaskViewModel**: Manages task list state and CRUD operations
- **EditorViewModel**: Manages speech-to-text UI state

#### 3. **Domain Layer** (Business Logic)
- **Models**: Pure Kotlin data classes (Task, User, AuthState)
- **Interfaces**: Repository and service contracts
- **Utilities**: Business logic utilities (sorting, time conversion, filtering)

#### 4. **Data Layer** (Data Sources)
- **Repositories**: Implementations of domain interfaces
- **Local**: Room database + DataStore for persistence
- **Remote**: Firebase Authentication + Firestore for cloud sync
- **External**: Google Cloud Speech-to-Text API

### ViewModel Sequence Diagrams

#### AuthViewModel Flow
![AuthViewModel Sequence Diagram](plots/AuthViewModel%20Sequence%20Diagram.png)

#### TaskViewModel Flow
![TaskViewModel Sequence Diagram](plots/TaskViewModel%20Sequence%20Diagram.png)

#### EditorViewModel Flow
![EditorViewModel Sequence Diagram](plots/EditorViewModel%20Sequence%20Diagram.png)

### Key Design Principles

- **Dependency Inversion**: ViewModels depend on domain interfaces, not implementations
- **Single Source of Truth**: Room database is the source of truth, Firestore for sync
- **Reactive Data Flow**: StateFlow/Flow for reactive UI updates
- **Separation of Concerns**: Clear boundaries between UI, business logic, and data
- **Testability**: Domain layer and ViewModels can be tested in isolation

## Permissions

Lattice requires the following permissions to function properly:

### Required Permissions

- **`RECORD_AUDIO`** (Microphone Permission)
  - **Purpose**: Enables speech-to-text functionality
  - **When Requested**: When user clicks the microphone icon in the EditorScreen
  - **Usage**: Records audio input and sends it to Google Cloud Speech-to-Text API for transcription
  - **Note**: This is a runtime permission. The app will request it when needed.

- **`INTERNET`** (Network Access)
  - **Purpose**: Required for cloud synchronization and API calls
  - **Usage**: 
    - Firebase Authentication (login/register)
    - Firebase Firestore (task synchronization)
    - Google Cloud Speech-to-Text API (voice transcription)
  - **Note**: App can work offline for local operations, but sync and speech-to-text require internet.

- **`CAMERA`** (Camera Permission)
  - **Purpose**: Allows users to take photos as task attachments
  - **When Requested**: When user selects "Take Photo" option in attachment upload
  - **Usage**: Captures images directly from camera to attach to tasks
  - **Note**: This is a runtime permission. The app will request it when needed.

### Optional Hardware Features

- **Microphone**: Not required (app works without it, but speech-to-text won't be available)
- **Camera**: Not required (app works without it, but photo capture won't be available)

### Permission Handling

- All permissions are requested at runtime when needed
- Users can deny permissions; the app will gracefully handle the denial
- If microphone permission is denied, speech-to-text feature will show an error message
- If camera permission is denied, photo capture will show an error message
- Internet permission is automatically granted and cannot be denied by users

## User Guide

### Quick Start

1. **First Launch**
   - Open the app
   - If not logged in, you'll see the Login screen
   - Tap "No account? Register here" to create a new account

2. **Registration**
   - Enter a username and password
   - Tap "Register" to create your account
   - You'll be automatically logged in after successful registration

3. **Login**
   - Enter your username and password
   - Tap "Login" to access the app
   - The app supports offline login using locally stored credentials

### Main Features

#### Task Management

**Creating Tasks**
- Tap the "New" button (bottom navigation bar) to create a root task
- Or tap the "+" FAB button on TaskListScreen
- Fill in task details:
  - **Title**: Task name (required)
  - **Description**: Additional details (optional)
  - **Priority**: None, Low, Medium, High
  - **Schedule**: Set date and optional time
  - **Attachments**: Add files or images
- Tap "Save" to create the task

**Creating Subtasks**
- Open a task in TaskListScreen
- Tap "Add Subtask" from the task menu
- Fill in subtask details and save
- Subtasks are automatically linked to their parent task

**Editing Tasks**
- Tap any task in TaskListScreen or CalendarScreen
- Modify task details in EditorScreen
- Tap "Update" to save changes

**Deleting Tasks**
- Tap the task menu (three dots) in TaskListScreen
- Select "Delete"
- **Note**: Deleting a parent task will cascade delete all its subtasks

**Completing Tasks**
- Tap the checkbox next to a task to mark it as done
- Completed tasks can be hidden from the main list using the settings drawer

#### Task Organization

**Filtering Tasks**
- Use the filter drawer (hamburger menu) in TaskListScreen
- Options: Today, This Week, This Month, All
- Filter applies to the entire task hierarchy

**Sorting Tasks**
- Use the sort options in the settings drawer
- Options: Title (alphabetical), Priority, Time (by due date)
- Sorting maintains the hierarchical structure

**Task Hierarchy**
- Tasks can have unlimited nesting levels
- Expand/collapse task groups to navigate the hierarchy
- Parent tasks show completion status based on children

#### Speech-to-Text

**Using Voice Input**
1. Open EditorScreen (create or edit a task)
2. Tap the microphone icon next to Title or Description field
3. Grant microphone permission if prompted
4. Speak clearly for 5 seconds (default duration)
5. The transcribed text will automatically fill the selected field

**Tips**
- Speak clearly and at a moderate pace
- Works best in quiet environments
- Supports multiple languages (default: en-US)
- If transcription fails, check your internet connection

#### Calendar View

**Viewing Tasks by Date**
- Tap the "Calendar" tab in bottom navigation
- Tasks are organized by their due dates
- Tap a date to see tasks scheduled for that day
- Tap a task to edit it
- Tap "Add Task" to create a new task

#### User Profile

**Viewing Statistics**
- Tap the "Profile" tab in bottom navigation
- View today's overview (To-Do, Completed)
- View lifetime statistics (Total Tasks, Completion Rate)
- View completion quality (On-time Rate, Postpone Rate)

**Daily Review**
- Tap "Daily Review" in Profile screen
- Confirm to postpone all unfinished today's tasks to tomorrow
- Useful for end-of-day task management

**Manual Sync**
- Tap "Sync Now" in Profile screen
- Manually trigger synchronization with Firebase
- Useful when automatic sync hasn't occurred

**Settings**
- Toggle Dark Mode on/off
- Theme preference is saved locally

### Offline Usage

- **Local Operations**: All task CRUD operations work offline
- **Authentication**: Offline login supported using local credentials
- **Sync**: Changes are queued locally and synced when internet is available
- **Speech-to-Text**: Requires internet connection

## Data Synchronization

Lattice implements a robust bidirectional synchronization system between local storage (Room Database) and Firebase Firestore, ensuring data consistency across devices.

### Sync Architecture

The synchronization system uses a **cursor-based incremental pull** and **batch push** mechanism:

1. **Incremental Pull**: Only fetches tasks updated since the last sync cursor
2. **Batch Push**: Sends multiple local changes in efficient batches
3. **Conflict Resolution**: Uses Last-Write-Wins (LWW) strategy with timestamp comparison

### Sync Mechanism

#### Pull Phase (Remote → Local)

1. **Cursor-Based Query**
   - Retrieves the last sync cursor timestamp from local storage
   - Queries Firestore for tasks with `updatedAt > cursor`
   - Includes a 2-minute clock skew safety window to handle time differences

2. **Conflict Detection**
   - Compares local and remote task versions
   - Detects conflicts when:
     - Local task is dirty (has unsaved changes) AND
     - Remote task was updated after last sync

3. **Conflict Resolution**
   - **If local is clean**: Remote version wins if it has a newer timestamp
   - **If local is dirty**: 
     - If remote hasn't changed since last sync: Keep local changes
     - If remote has changed: Remote wins if `remoteUpdatedAt >= localUpdatedAt`
   - **Result**: Remote version overwrites local, marked as SYNCED

4. **Update Cursor**
   - After successful pull, updates the sync cursor to the maximum `updatedAt` timestamp
   - Ensures next sync only fetches newer changes

#### Push Phase (Local → Remote)

1. **Dirty Task Detection**
   - Identifies tasks with `syncStatus != SYNCED` and `isDeleted == false`
   - These are tasks that have been created, updated, or modified locally

2. **Batch Processing**
   - Groups dirty tasks into batches (max 450 per batch, Firestore limit is 500)
   - Processes each batch in a single Firestore transaction

3. **Upload to Firestore**
   - Maps local task entities to Firestore document format
   - Updates `updatedAt` timestamp to current time
   - Uses Firestore batch write for atomic operations

4. **Mark as Synced**
   - After successful upload, marks tasks as `SYNCED` locally
   - Updates `lastSyncedAt` timestamp

### Sync Triggers

**Automatic Sync**
- Triggered when user logs in (first observation of tasks)
- Triggered when tasks are first observed for a user
- Runs in background using coroutines

**Manual Sync**
- User can trigger sync from Profile screen
- Tap "Sync Now" button
- Shows sync status (success/failure)

**Best-Effort Push**
- When tasks are saved locally, app attempts to push immediately
- If push fails (e.g., no internet), tasks remain marked as dirty
- Will be synced in next automatic or manual sync

### Sync Status

Tasks have the following sync statuses:

- **`SYNCED`**: Task is synchronized with remote
- **`CREATED`**: New task, not yet synced
- **`UPDATED`**: Task was modified locally, not yet synced
- **`DELETED`**: Task was deleted locally, tombstone not yet synced

### Offline Support

**Local-First Architecture**
- Room database is the single source of truth
- All operations work offline
- Changes are queued locally with appropriate sync status

**Sync Queue**
- Dirty tasks are automatically synced when:
  - Internet connection is available
  - User logs in
  - User manually triggers sync

**Data Consistency**
- Sync uses mutex locks to prevent concurrent sync operations
- Ensures thread-safe synchronization
- Handles network failures gracefully

### Sync Limitations

- **Attachment Sync**: Attachment file paths are synced, but actual files are stored locally
- **Large Batches**: Very large task lists (>450 tasks) require multiple batch operations
- **Network Dependency**: Sync requires active internet connection
- **Clock Skew**: 2-minute safety window handles minor time differences between devices

### Troubleshooting Sync

**If sync fails:**
1. Check internet connection
2. Verify Firebase configuration (`google-services.json`)
3. Check if user is properly authenticated
4. Try manual sync from Profile screen
5. Check Logcat for sync error messages

**If data conflicts occur:**
- Last-Write-Wins strategy ensures consistency
- Most recent change (by timestamp) wins
- Local dirty changes may be overwritten by remote if remote is newer

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
