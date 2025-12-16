package com.example.lattice.domain.sort

/**
 * 任务排序选项 / Task sorting order options
 * 
 * 定义任务列表可用的排序策略。
 * 
 * Defines the available sorting strategies for task lists.
 */
enum class TaskSortOrder {
    Title,    // Sort by task title alphabetically
    Priority, // Sort by priority level (High > Medium > Low > None)
    Time      // Sort by due date and time
}

