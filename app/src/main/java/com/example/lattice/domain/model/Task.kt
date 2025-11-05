package com.example.lattice.domain.model

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    val parentId: String? = null
)
