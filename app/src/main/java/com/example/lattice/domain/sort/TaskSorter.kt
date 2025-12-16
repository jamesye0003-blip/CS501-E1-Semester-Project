package com.example.lattice.domain.sort

import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.toTimePoint

/**
 * 任务排序工具 / Task sorting utilities
 * 
 * 提供根据不同标准对任务列表进行排序的函数
 * 
 * Provides functions for sorting task lists according to different criteria.
 */

/**
 * 根据指定的排序顺序递归地按层级对任务进行排序：同一层级的任务一起排序，保持层级结构。
 * 
 * Sort tasks by layer recursively according to the specified sort order.
 * Tasks at the same layer are sorted together, maintaining hierarchical structure.
 * 
 * @param tasks 当前层级要排序的任务列表 / List of tasks to sort at the current layer
 * @param allTasks 所有任务的完整列表（供将来使用）/ Complete list of all tasks (for potential future use)
 * @param sortOrder 要应用的排序策略 / The sorting strategy to apply
 * @return 排序后的任务列表 / Sorted list of tasks
 */
fun sortTasksByLayer(
    tasks: List<Task>,
    @Suppress("UNUSED_PARAMETER") allTasks: List<Task>,
    sortOrder: TaskSortOrder
): List<Task> {
    // allTasks is kept for potential future use in sorting logic (e.g., sorting by parent task properties)
    if (tasks.isEmpty()) return emptyList()
    
    // Sort tasks at current layer
    return tasks.sortedWith { t1, t2 ->
        compareTasks(t1, t2, sortOrder)
    }
}

/**
 * 根据指定的排序顺序比较两个任务：如果 t1 < t2 返回负数，t1 > t2 返回正数，相等返回零。
 * 
 * Compare two tasks according to the specified sort order.
 * Returns negative if t1 < t2, positive if t1 > t2, zero if equal.
 * 
 * @param t1 要比较的第一个任务 / First task to compare
 * @param t2 要比较的第二个任务 / Second task to compare
 * @param sortOrder 要应用的排序策略 / The sorting strategy to apply
 * @return 比较结果 / Comparison result
 */
fun compareTasks(
    t1: Task,
    t2: Task,
    sortOrder: TaskSortOrder
): Int {
    return when (sortOrder) {
        TaskSortOrder.Title -> {
            // Sort by title (dictionary order, A before Z)
            val titleCompare = t1.title.compareTo(t2.title, ignoreCase = true)
            if (titleCompare != 0) titleCompare
            else 0 // If titles are equal, maintain original order
        }
        TaskSortOrder.Priority -> {
            // Sort by priority: High > Medium > Low > None
            val priorityOrder = mapOf(
                Priority.High to 0,
                Priority.Medium to 1,
                Priority.Low to 2,
                Priority.None to 3
            )
            val priorityCompare = (priorityOrder[t1.priority] ?: 3).compareTo(priorityOrder[t2.priority] ?: 3)
            if (priorityCompare != 0) priorityCompare
            else {
                // Same priority, compare by time
                compareByTime(t1, t2)
            }
        }
        TaskSortOrder.Time -> {
            // Sort by time: tasks with time before tasks without time
            // Same time, compare by priority
            // Same time and priority, compare by title
            val timeCompare = compareByTime(t1, t2)
            if (timeCompare != 0) timeCompare
            else {
                // Same time, compare by priority
                val priorityOrder = mapOf(
                    Priority.High to 0,
                    Priority.Medium to 1,
                    Priority.Low to 2,
                    Priority.None to 3
                )
                val priorityCompare = (priorityOrder[t1.priority] ?: 3).compareTo(priorityOrder[t2.priority] ?: 3)
                if (priorityCompare != 0) priorityCompare
                else {
                    // Same time and priority, compare by title
                    t1.title.compareTo(t2.title, ignoreCase = true)
                }
            }
        }
    }
}

/**
 * 按时间比较两个任务 / Compare two tasks by time
 * 
 * 有时间信息的任务排在无时间信息的任务之前。
 * 如果 t1 < t2 返回负数，t1 > t2 返回正数，相等返回零。
 * 
 * Tasks with time come before tasks without time.
 * Returns negative if t1 < t2, positive if t1 > t2, zero if equal.
 * 
 * @param t1 要比较的第一个任务 / First task to compare
 * @param t2 要比较的第二个任务 / Second task to compare
 * @return 比较结果 / Comparison result
 */
fun compareByTime(t1: Task, t2: Task): Int {
    val time1 = t1.toTimePoint()
    val time2 = t2.toTimePoint()
    
    // Tasks without time come after tasks with time
    if (time1 == null && time2 == null) return 0
    if (time1 == null) return 1 // t1 has no time, t2 has time -> t1 > t2
    if (time2 == null) return -1 // t1 has time, t2 has no time -> t1 < t2
    
    // Both have time, compare by date first
    val dateCompare = time1.date.compareTo(time2.date)
    if (dateCompare != 0) return dateCompare
    
    // Same date, compare by time if both have specific time
    if (time1.time != null && time2.time != null) {
        return time1.time.compareTo(time2.time)
    }
    
    // One or both don't have specific time, but same date
    if (time1.time != null) return -1 // t1 has time, t2 doesn't -> t1 < t2
    if (time2.time != null) return 1 // t1 doesn't have time, t2 has -> t1 > t2
    
    return 0 // Both have same date but no specific time
}

