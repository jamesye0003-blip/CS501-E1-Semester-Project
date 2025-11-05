package com.example.lattice.ui.navigation

sealed class Route(val route: String) {
    data object Login : Route("login")
    data object Main : Route("main")
    data object Home : Route("home")
    // 可带 parent（父任务 id），为空表示创建根任务
    companion object {
        const val EDITOR_ROUTE = "editor?parent={parent}"
    }
}

fun buildEditorRoute(parentId: String?): String =
    "editor?parent=${parentId ?: ""}"
