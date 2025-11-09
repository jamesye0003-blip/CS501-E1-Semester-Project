package com.example.lattice.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String? = null
)
