package com.phad.chatapp.features.scheduling.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun AlgorithmLogDialog(
    logs: List<LogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    
    // Group logs for unified card display
    // Skipped attempts are accumulated and attached to the next successful assignment
    val groupedLogs = remember(logs) {
        val groups = mutableListOf<Any>()
        var currentAttempt: LogEntry? = null
        var currentResult: LogEntry? = null
        val currentSkips = mutableListOf<LogEntry>()
        
        // Accumulate skipped attempts (ATTEMPT + SKIP with no ASSIGNED)
        val pendingSkippedAttempts = mutableListOf<LogGroup>()
        
        fun flushCurrent() {
            if (currentAttempt != null) {
                val group = LogGroup(currentAttempt!!, currentResult, currentSkips.toList())
                if (currentResult != null) {
                    // This is an assigned attempt - attach all pending skipped attempts
                    group.precedingSkippedAttempts.addAll(pendingSkippedAttempts)
                    pendingSkippedAttempts.clear()
                    groups.add(group)
                } else if (currentSkips.isNotEmpty()) {
                    // This is a skipped attempt - add to pending
                    pendingSkippedAttempts.add(group)
                }
            }
            currentAttempt = null
            currentResult = null
            currentSkips.clear()
        }
        
        for (log in logs) {
            when (log.type) {
                LogType.ATTEMPT -> {
                    flushCurrent()
                    currentAttempt = log
                }
                LogType.ASSIGNED -> currentResult = log
                LogType.SKIP_SAME_DAY, LogType.SKIP_NO_PREF, LogType.SKIP_ADJACENCY, LogType.SKIP_OTHER -> {
                    currentSkips.add(log)
                }
                else -> {
                    flushCurrent()
                    // If there are pending skipped attempts at a round/iteration boundary,
                    // add them as standalone items at the end
                    if (pendingSkippedAttempts.isNotEmpty()) {
                        groups.add(FailedBatch(pendingSkippedAttempts.toList()))
                        pendingSkippedAttempts.clear()
                    }
                    if (log.type == LogType.ITERATION_START || log.type == LogType.ROUND_START) {
                        groups.add(log) // Only add visible separators
                    }
                }
            }
        }
        flushCurrent()
        // Add any remaining pending skipped attempts at the end
        if (pendingSkippedAttempts.isNotEmpty()) {
            groups.add(FailedBatch(pendingSkippedAttempts.toList()))
            pendingSkippedAttempts.clear()
        }
        groups
    }

    LaunchedEffect(groupedLogs.size) {
        if (groupedLogs.isNotEmpty()) {
            listState.scrollToItem(groupedLogs.size - 1)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
                    .padding(start = 4.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Assignment Log",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
            
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs yet", color = Color.Gray)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(groupedLogs) { item ->
                            when (item) {
                                is LogEntry -> {
                                    // Only show separators at top level
                                    if (item.type == LogType.ITERATION_START || item.type == LogType.ROUND_START) {
                                        TopLevelLogItem(item)
                                    }
                                }
                                is LogGroup -> PremiumLogCard(item)
                                is FailedBatch -> FailedBatchCard(item)
                            }
                        }
                    }

                    // Custom Scrollbar
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val visibleItems = layoutInfo.visibleItemsInfo.size
                    
                    if (visibleItems < totalItems) {
                        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                        val thumbHeight = (viewportHeight * visibleItems / totalItems).coerceAtLeast(viewportHeight * 0.1f)
                        val maxScrollOffset = maxOf(0, totalItems - visibleItems).toFloat()
                        val currentScrollOffset = listState.firstVisibleItemIndex.toFloat() + (listState.firstVisibleItemScrollOffset.toFloat() / (layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1))
                        
                        val thumbOffset = if (maxScrollOffset > 0) {
                            (currentScrollOffset / maxScrollOffset) * (viewportHeight - thumbHeight)
                        } else 0f
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp)
                                .width(6.dp)
                                .fillMaxHeight()
                                .drawWithContent {
                                    drawContent()
                                    drawRoundRect(
                                        color = Color(0xFFFFCC00).copy(alpha = 0.5f),
                                        topLeft = Offset(0f, thumbOffset),
                                        size = Size(4.dp.toPx(), thumbHeight),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

class LogGroup(
    val attempt: LogEntry,
    val result: LogEntry?,
    val skips: List<LogEntry>,
    val precedingSkippedAttempts: MutableList<LogGroup> = mutableListOf()
)

@Composable
fun TopLevelLogItem(log: LogEntry) {
    when (log.type) {
        LogType.ITERATION_START -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha=0.3f))
                Text(
                    text = " NEW RUN ",
                    color = Color(0xFFFFCC00),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha=0.3f))
            }
        }
        LogType.ROUND_START -> {
            val roundNum = log.message.filter { it.isDigit() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF0097A7),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(roundNum, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Round $roundNum",
                    color = Color(0xFF80DEEA),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        else -> {
            // Do not render anything for other log types at top level
            // This prevents empty space in LazyColumn due to spacedBy
        } 
    }
}

@Composable
fun PremiumLogCard(group: LogGroup) {
    // State for expanding skipped slots
    var skipsExpanded by remember { mutableStateOf(false) }
    
    // Parse Attempt Log
    val parts = group.attempt.message.split("|").map { it.trim() }
    val school = parts.getOrNull(0) ?: ""
    val day = parts.getOrNull(1) ?: ""
    val timeAndInfo = parts.getOrNull(2) ?: ""
    val time = timeAndInfo.substringBefore("(").trim()
    val tfvInfo = timeAndInfo.substringAfter("(", "").substringBefore(",").trim()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), // Neutral Dark
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1 (35% space)
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Line 1: School and Day
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // School Chip
                        Surface(
                            color = Color(0xFFFFCC00),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = school,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.Black
                            )
                        }
                        
                        // Day Chip
                        Surface(
                            color = Color(0xFFFFCC00),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = day,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.Black
                            )
                        }
                    }
                    
                    // Line 2: Time
                    Surface(
                        color = Color(0xFFFFCC00),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = time,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.Black
                        )
                    }
                    
                    // Line 3: TFV
                    Text(
                        text = tfvInfo,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp, 
                            color = Color.Gray.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Column 2 (65% space)
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (group.result != null) {
                        // Assigned
                        val cleanResult = group.result.message.removePrefix("✓ ")
                        val fullString = cleanResult
                        
                        // Check if this is a manual assignment
                        val isManual = fullString.contains("[MANUAL]")
                        
                        val namePartRaw = fullString.substringBefore("|").trim().removePrefix("[MANUAL]").trim()
                        val subjectPartRaw = fullString.substringAfter("|", "").trim()
                        
                        // Parse Name and Roll: "Name (Roll)" -> Name, Roll
                        val rollStart = namePartRaw.lastIndexOf('(')
                        val rollEnd = namePartRaw.lastIndexOf(')')
                        val name = if (rollStart > 0) namePartRaw.substring(0, rollStart).trim() else namePartRaw
                        val roll = if (rollStart > 0 && rollEnd > rollStart) namePartRaw.substring(rollStart + 1, rollEnd).trim() else ""

                        // Parse Subject and Pref: "Biology (1st Pref)" -> Biology, #1
                        val prefStart = subjectPartRaw.lastIndexOf('(')
                        val subjectName = if (prefStart > 0) subjectPartRaw.substring(0, prefStart).trim() else subjectPartRaw
                        val prefRaw = if (prefStart > 0) subjectPartRaw.substring(prefStart + 1).substringBefore(")") else ""
                        val prefDigit = prefRaw.filter { it.isDigit() }
                        val prefFormatted = if (prefDigit.isNotEmpty()) "(#$prefDigit)" else ""
                        
                        val line2Text = "$roll   $subjectName $prefFormatted".trim()
                        
                        // Line 1: Name
                        Text(
                             text = name,
                             color = Color.White,
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             maxLines = 1,
                             overflow = TextOverflow.Ellipsis
                        )
                        
                    // Line 2: Roll Subject (#n)
                    Text(
                         text = line2Text,
                         color = Color.LightGray,
                         style = MaterialTheme.typography.bodyMedium,
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis
                    )
                    
                    // Line 3: Score
                    // Extract Score from full string if available, otherwise it might need to be passed in differently
                    // Currently the message format is: "✓ Name (Roll) | Subject (Pref)"
                    // We need to either update the log message format in ViewModel OR check if score is available.
                    // Let's check ScheduleGenerationViewModel log format first.
                    // It is: addLog(LogType.ASSIGNED, "✓ ${volunteer.name} (${volunteer.rollNo}) | ${subjectToAssign.subjectName} ($prefLabel Pref) [Score=${volunteer.interviewScore}]")
                    // Wait, I need to UPDATE the log message format in ViewModel first to include the score!
                    
                    // Parsing score from the new format I will implement: "... | Subject (Pref) | Score=n"
                    val scorePartRaw = fullString.substringAfterLast("|", "").trim()
                    val scoreText = if (scorePartRaw.startsWith("Score=")) scorePartRaw.removePrefix("Score=") else ""
                    
                    if (scoreText.isNotEmpty() || isManual) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (scoreText.isNotEmpty()) {
                                Text(
                                    text = "Score=$scoreText",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp, 
                                        color = Color.Gray.copy(alpha = 0.7f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            if (isManual) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2196F3) // Blue color
                                ) {
                                    Text(
                                        text = "MANUAL",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                        
                        // Show skipped slots indicator if there are preceding skipped attempts
                        if (group.precedingSkippedAttempts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2D2D2D))
                                    .clickable { skipsExpanded = !skipsExpanded }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${group.precedingSkippedAttempts.size} slot${if (group.precedingSkippedAttempts.size > 1) "s" else ""} skipped",
                                    color = Color(0xFFFF9800),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (skipsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (skipsExpanded) "Collapse" else "Expand",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else if (group.skips.isNotEmpty()) {
                        val firstSkip = group.skips.first().message.removePrefix("Skipped: ")
                         Text(
                             text = "Skipped",
                             color = Color.Red, 
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold
                        )
                        Text(
                             text = firstSkip,
                             color = Color.Red,
                             style = MaterialTheme.typography.bodyMedium,
                             maxLines = 1,
                             overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Expandable skipped slots section
            AnimatedVisibility(
                visible = skipsExpanded && group.result != null && group.precedingSkippedAttempts.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.precedingSkippedAttempts.forEach { skippedAttempt ->
                        // Skipped slot row
                        SkippedSlotRow(skippedAttempt = skippedAttempt)
                    }
                        }
                    }
                }
        }
    }


@Composable
fun FailedBatchCard(batch: FailedBatch) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                     Text(
                        text = "Auto Assignment Failed",
                        color = Color.Red,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${batch.failures.size} slots skipped",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.Gray
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    batch.failures.forEach { failure ->
                        SkippedSlotRow(skippedAttempt = failure)
                    }
                }
            }
        }
    }
}

@Composable
fun SkippedSlotRow(skippedAttempt: LogGroup) {
    // Parse skipped attempt info
    val parts = skippedAttempt.attempt.message.split("|").map { it.trim() }
    val skipSchool = parts.getOrNull(0) ?: ""
    val skipDay = parts.getOrNull(1) ?: ""
    val skipTimeAndInfo = parts.getOrNull(2) ?: ""
    val skipTime = skipTimeAndInfo.substringBefore("(").trim()
    
    val skipLog = skippedAttempt.skips.firstOrNull()
    val skipReason = skipLog?.message?.removePrefix("Skipped: ") ?: "Unknown reason"
    val skipType = skipLog?.type
    
    val tfvInfoRaw = skipTimeAndInfo.substringAfter("(", "").substringBefore(",").trim()
    val tfvValue = tfvInfoRaw.filter { it.isDigit() }
    val tfvDisplay = if (tfvValue.isNotEmpty()) " • TFV=$tfvValue" else ""
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Skip reason indicator
        Surface(
            color = when (skipType) {
                LogType.SKIP_SAME_DAY -> Color(0xFFFF9800) // Orange
                LogType.SKIP_NO_PREF -> Color(0xFFFFEB3B) // Yellow
                LogType.SKIP_ADJACENCY -> Color(0xFFF44336) // Red
                else -> Color.Gray
            },
            shape = CircleShape,
            modifier = Modifier.size(8.dp)
        ) {}
        
        // Slot info
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$skipSchool • $skipDay • $skipTime$tfvDisplay",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = skipReason,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class FailedBatch(val failures: List<LogGroup>)
