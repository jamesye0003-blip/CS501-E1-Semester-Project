package com.example.lattice.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * 统一的 DataStore 定义，避免同一文件名被多个实例同时打开。
 * Unified DataStore definition to avoid multiple active instances for the same file.
 */
val Context.authDataStore by preferencesDataStore(name = "auth")

