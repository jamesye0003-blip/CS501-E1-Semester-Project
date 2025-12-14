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

fun buildEditorRoute(parentId: String? = null, editId: String? = null, fromBottomNav: Boolean = false): String =
    "editor?parent=${parentId ?: ""}&editId=${editId ?: ""}&fromBottomNav=$fromBottomNav"
