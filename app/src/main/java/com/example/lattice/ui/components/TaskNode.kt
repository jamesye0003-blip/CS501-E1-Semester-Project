package com.example.lattice.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import com.example.lattice.domain.model.toTimePoint
import com.example.lattice.ui.theme.PriorityHigh
import com.example.lattice.ui.theme.PriorityLow
import com.example.lattice.ui.theme.PriorityMedium
import com.example.lattice.ui.theme.PriorityNone
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TaskNode(
    task: Task,
    tasks: List<Task>,
    showCompleted: Boolean,
    hideDescription: Boolean = false,
    onToggleDone: (String) -> Unit,
    onAddSub: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    depth: Int = 0 // 新增：用于控制递归层级样式
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showMaxDepthWarning by remember { mutableStateOf(false) }

    // 过滤子任务
    val children = remember(tasks, task.id, showCompleted) {
        tasks.filter { it.parentId == task.id && it.done == showCompleted }
    }

    // 新增：子任务折叠/展开状态（默认展开）
    var childrenExpanded by rememberSaveable(task.id) { mutableStateOf(true) }
    
    // 检查是否达到最大层级（5层，depth从0开始，所以depth >= 4表示第5层）
    val isMaxDepth = depth >= 4

    // 为 clickable（旧 Indication 兼容版本）准备的交互对象
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    val canToggleChildren = children.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. 任务卡片主体
        val surfaceModifier = Modifier
            .fillMaxWidth()
            .alpha(if (task.done) 0.5f else 1f)
            .let { base ->
                if (canToggleChildren) {
                    // 关键：使用带 indication 参数的 clickable 重载，避免 PlatformRipple 崩溃
                    base.clickable(
                        interactionSource = interactionSource,
                        indication = indication
                    ) {
                        childrenExpanded = !childrenExpanded
                    }
                } else {
                    base
                }
            }

        Surface(
            modifier = surfaceModifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (depth == 0) 2.dp else 0.dp, // 根任务有阴影，子任务扁平
            shadowElevation = if (depth == 0) 1.dp else 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min) // 允许子元素填满高度
            ) {
                // Priority Indicator Strip (左侧彩色竖条)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(priorityColor(task.priority))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.Top // 内容顶部对齐，适应长文本
                ) {
                    // Checkbox
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggleDone(task.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = priorityColor(task.priority),
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp) // 微调位置
                    )

                    Spacer(Modifier.width(12.dp))

                    // Text Content
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Title
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null
                        )

                        // Description
                        if (!hideDescription && task.description.isNotBlank()) {
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Time & Meta info
                        val timeText = remember(task.dueAt, task.hasSpecificTime, task.sourceTimeZoneId) {
                            task.toTimePoint()?.let { formatTimePointForList(it) }
                        }
                        if (!timeText.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Action Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp) // 稍微缩小按钮占用空间
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (!showCompleted) {
                                DropdownMenuItem(
                                    text = { Text("Add Subtask") },
                                    leadingIcon = { Icon(Icons.Default.SubdirectoryArrowRight, null) },
                                    onClick = {
                                        menuExpanded = false
                                        if (isMaxDepth) {
                                            showMaxDepthWarning = true
                                        } else {
                                            onAddSub(task.id)
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuExpanded = false; onEdit(task.id) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuExpanded = false; onDelete(task.id) }
                            )
                        }
                    }
                }
            }
        }

        // 2. 递归渲染子任务（支持折叠）
        if (children.isNotEmpty() && childrenExpanded) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧缩进 + 视觉引导线
                Box(
                    modifier = Modifier
                        .width(24.dp) // 缩进宽度
                        .height(IntrinsicSize.Max),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // 灰色竖线，贯穿子任务区域
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                // 子任务列表
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.height(8.dp)) // 增加一点顶部间距
                    children.forEach { child ->
                        TaskNode(
                            task = child,
                            tasks = tasks,
                            showCompleted = showCompleted,
                            hideDescription = hideDescription,
                            onToggleDone = onToggleDone,
                            onAddSub = onAddSub,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            depth = depth + 1 // 传递层级
                        )
                    }
                }
            }
        }
        
        // Max depth warning dialog
        if (showMaxDepthWarning) {
            AlertDialog(
                onDismissRequest = { showMaxDepthWarning = false },
                title = { Text("Maximum Depth Reached") },
                text = { Text("Cannot add subtask beyond 5 layers. Please consider reorganizing your task structure.") },
                confirmButton = {
                    TextButton(onClick = { showMaxDepthWarning = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.High -> PriorityHigh
    Priority.Medium -> PriorityMedium
    Priority.Low -> PriorityLow
    Priority.None -> PriorityNone
}

private val LOCAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun formatTimePointForList(timePoint: TimePoint): String {
    val systemZone: ZoneId = ZoneId.systemDefault()
    val zoneLabel = timePoint.zoneId.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val zoneSuffix = if (timePoint.zoneId == systemZone) null else zoneLabel

    if (timePoint.time == null) {
        val nowInStored = ZonedDateTime.now(timePoint.zoneId)
        val label = when (timePoint.date) {
            nowInStored.toLocalDate() -> "Today"
            nowInStored.plusDays(1).toLocalDate() -> "Tomorrow"
            nowInStored.minusDays(1).toLocalDate() -> "Yesterday"
            else -> timePoint.date.toString()
        }
        return listOfNotNull(label, zoneSuffix).joinToString(" ")
    }

    val eventInStoredZone = ZonedDateTime.of(timePoint.date, timePoint.time, timePoint.zoneId)
    val eventInLocalZone = eventInStoredZone.withZoneSameInstant(systemZone)

    val nowLocal = ZonedDateTime.now(systemZone)
    val label = when (eventInLocalZone.toLocalDate()) {
        nowLocal.toLocalDate() -> "Today"
        nowLocal.plusDays(1).toLocalDate() -> "Tomorrow"
        nowLocal.minusDays(1).toLocalDate() -> "Yesterday"
        else -> eventInLocalZone.toLocalDate().toString()
    }

    val timePart = eventInLocalZone.toLocalTime().format(LOCAL_TIME_FORMATTER)
    return listOfNotNull(label, timePart, zoneSuffix).joinToString(" ")
}
