package com.example.lattice.ui.navigation

sealed class Route(val route: String) {
    data object Login : Route("login")
    data object Register : Route("register")
    data object Main : Route("main")

    // Main graph destinations
    data object Home : Route("home")
    data object Calendar : Route("calendar")
    data object Profile : Route("profile")

    companion object {
        const val EDITOR_ROUTE = "editor?parent={parent}&editId={editId}&fromBottomNav={fromBottomNav}"
    }
}

/**
 * EDITOR_ROUTE has four different entry points:
 * 1. Add a subtask from TaskListScreen (Add Subtask, parentId != null, editId == null, fromBottomNav == false)
 * 2. Edit a task from TaskListScreen or CalendarScreen (Edit, parentId == null, editId != null, fromBottomNav == false)
 * 3. Create a new task from CalendarScreen (New, parentId == null, editId == null, fromBottomNav == false)
 * 4. Create a new task from the bottom navigation bar (New, parentId == null, editId == null, fromBottomNav == true)
 * 
 * This route uses optional parameters parent, editId, and fromBottomNav
 * to distinguish the source and initial state, which are parsed in NavGraph/EditorScreen.
 */
fun buildEditorRoute(parentId: String? = null, editId: String? = null, fromBottomNav: Boolean = false): String =
    "editor?parent=${parentId ?: ""}&editId=${editId ?: ""}&fromBottomNav=$fromBottomNav"
