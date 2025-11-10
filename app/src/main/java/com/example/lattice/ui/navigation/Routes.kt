package com.example.lattice.ui.navigation

sealed class Route(val route: String) {
    data object Login : Route("login")
    data object Main : Route("main")
    data object Home : Route("home")

    companion object {
        const val EDITOR_ROUTE = "editor?parent={parent}&editId={editId}"
    }
}

fun buildEditorRoute(parentId: String? = null, editId: String? = null): String =
    "editor?parent=${parentId ?: ""}&editId=${editId ?: ""}"
