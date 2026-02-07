package com.phad.chatapp.ui.profile

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.phad.chatapp.R

data class ProfileUiState(
    val name: String = "Loading...",
    val location: String = "...",
    val email: String = "loading...",
    val phone: String = "loading...",
    val rollNumber: String = "loading...",
    val collegeEmail: String = "loading...",
    val academicGroup: String = "loading...",
    val nssGroup: String = "loading...",
    val topic1: String = "loading...",
    val topic2: String = "loading...",
    val topic3: String = "loading...",
    val userType: String = "Student",
    val events: String = "-/-",
    val classes: String = "-/-",
    val meetings: String = "-/-",
    val isStudent: Boolean = true,
    val Teaching_wing: Boolean = false,
    // Additional student fields from database
    val courseCode: String = "loading...",
    val gmailId: String = "loading...",
    val instituteId: String = "loading...",
    val subjectPreference1: String = "loading...",
    val subjectPreference2: String = "loading...",
    val subjectPreference3: String = "loading...",
    val teachingWingStatus: String = "loading...",
    val profileImageUrl: String = "",
    val wings: List<String> = emptyList(),
    val subjectPreferences: List<String> = emptyList(),
    val classesPerWeek: String = "0",
    val assignedSlots: List<String> = emptyList(), // New field for assigned slots
    // New semester-based statistics fields
    val sem1Hours: String = "0/0",
    val sem2Hours: String = "0/0",
    val eventsAttended: String = "0/0",
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    state: ProfileUiState,
    onLogoutClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onChatClick: () -> Unit,
    onScheduleClick: () -> Unit,
    // New admin-only export button
    onExportAttendanceClick: () -> Unit = {},
    onEventHistoryClick: () -> Unit = {},
    onManageStudentsClick: () -> Unit = {},
    onChangeSubjectsClick: () -> Unit = {},
    onSwitchInterfaceClick: () -> Unit,
    onSem1HoursClick: () -> Unit,
    onSem2HoursClick: () -> Unit,
    onFaqsClick: () -> Unit = {},
    onSubjectPreferenceClick: () -> Unit = {},
    onUpdateClassesPerWeek: (String) -> Unit = {},
    currentInterface: String,
    teachingWing: Boolean
) {
    // Menu state
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClassesDialog by remember { mutableStateOf(false) }
    
    // Determine user type
    val isAdmin = !state.isStudent
    // Debug logging for ProfileScreen
    Log.d("ProfileScreen", "=== PROFILE SCREEN DEBUG ===")
    Log.d("ProfileScreen", "Received state: $state")
    Log.d("ProfileScreen", "sem1Hours: ${state.sem1Hours}")
    Log.d("ProfileScreen", "sem2Hours: ${state.sem2Hours}")
    Log.d("ProfileScreen", "eventsAttended: ${state.eventsAttended}")
    Log.d("ProfileScreen", "isStudent: ${state.isStudent}")
    Log.d("ProfileScreen", "🔍 SWITCH BUTTON DEBUG:")
    Log.d("ProfileScreen", "teachingWing parameter: $teachingWing")
    Log.d("ProfileScreen", "currentInterface: $currentInterface")
    Log.d("ProfileScreen", "isAdmin: $isAdmin")
    Log.d("ProfileScreen", "Switch button should show: ${teachingWing}")
    Log.d("ProfileScreen", "=== PROFILE SCREEN DEBUG COMPLETE ===")
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = Color(0xff0d0302)
    val surfaceColor = Color.White
    val onSurfaceColor = Color.Black
    val onSurfaceVariantColor = onSurfaceColor.copy(alpha = 0.6f)
    val onBackgroundColor = Color.White

    Surface(
        modifier = modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        val pullRefreshState = rememberPullToRefreshState()

        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                onRefreshClick()
            }
        }
        
        // Synch logic: if state.isRefreshing is false, allow stop. 
        // Note: We don't force startRefresh from state here usually, 
        // we just ensure it stops when state says so.
        LaunchedEffect(state.isRefreshing) {
            if (!state.isRefreshing) {
                pullRefreshState.endRefresh()
            }
        }

        // Comprehensive insets handling to avoid conflicts with MainActivity
        val navigationBars = WindowInsets.navigationBars.asPaddingValues()
        
        // Calculate the actual space needed for the floating navigation bar
        // The floating nav has: 56dp height + 20dp bottom margin + extra safety space
        val floatingNavHeight = 56.dp + 20.dp + 60.dp // nav height + bottom margin + safety buffer
        
        // Use the larger of system navigation bar height or our calculated floating nav height
        // Add fallback values for devices where insets might not work properly
        val effectiveBottomSpace = maxOf(
            navigationBars.calculateBottomPadding().takeIf { it > 0.dp } ?: 80.dp,
            floatingNavHeight
        )
        
        // Ensure we have enough white background to cover the entire area above the nav
        val whiteBackgroundHeight = effectiveBottomSpace + 80.dp // extra space for content safety
        
        val scrollState = rememberScrollState()

        // Disable overscroll effect
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalOverscrollConfiguration provides null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {

                Image(
                    painter = painterResource(id = R.drawable.vector271),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painter = painterResource(id = R.drawable.vector272),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 50.dp, end = 50.dp, top = 20.dp, bottom = 100.dp)
                        .clip(RoundedCornerShape(150.dp))
                )


                // Profile Image (centered between header and content) - For both Admin and Student users

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Top Section with background images and stats

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .graphicsLayer {
                            translationY = scrollState.value.toFloat()
                        }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vector271),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painter = painterResource(id = R.drawable.vector272),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 50.dp, end = 50.dp, top = 20.dp, bottom = 100.dp)
                            .clip(RoundedCornerShape(150.dp))
                    )

                    // Header with logo and icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (currentInterface == "NSS") R.drawable.nss_logo_main else R.drawable.logo
                            ),
                            contentDescription = if (currentInterface == "NSS") "NSS Logo" else "Teaching Wing Logo",
                            colorFilter = if (currentInterface == "NSS") null else null,
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        try {
                                            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                            val version = pInfo.versionName
                                            val verCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                pInfo.longVersionCode
                                            } else {
                                                @Suppress("DEPRECATION")
                                                pInfo.versionCode.toLong()
                                            }
                                            Toast.makeText(context, "Version: $version ($verCode)", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("ProfileScreen", "Error getting version info", e)
                                        }
                                    }
                                ),
                            contentScale = ContentScale.Fit
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Standalone Switch User icon (to the left of menu)
                            if (teachingWing) {
                                IconButton(
                                    onClick = onSwitchInterfaceClick,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_group),
                                        contentDescription = "Switch User",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            // Hamburger menu button (single button for all options)
                            Box {
                                ProfileMenuButton(
                                    onMenuClick = { showMenu = true },
                                    modifier = Modifier.size(40.dp)
                                )
                                
                                // Profile menu dropdown
                                ProfileMenu(
                                    expanded = showMenu,
                                    onDismiss = { showMenu = false },
                                    onSwitchUser = onSwitchInterfaceClick,
                                    onRefresh = onRefreshClick,
                                    onExportAttendance = onExportAttendanceClick,
                                    onEventHistory = onEventHistoryClick,
                                    onManageStudents = onManageStudentsClick,
                                    onChangeSubjects = onChangeSubjectsClick,
                                    onFaqs = onFaqsClick,
                                    onLogout = { showLogoutDialog = true },
                                    isTeachingWing = teachingWing,
                                    isAdmin = isAdmin,
                                    currentInterface = currentInterface
                                )
                            }
                        }
                    }

                    // Profile Image (centered between header and content) - For both Admin and Student users
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 150.dp,
                                bottom = 30.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // White background circle for better contrast
                        Box(
                            modifier = Modifier
                                .size(if (!state.isStudent) 160.dp else 140.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(4.dp, Color(0xFFFFCC00), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProfileImage(
                                imageUrl = state.profileImageUrl,
                                size = if (!state.isStudent) 150.dp else 130.dp,
                                borderColor = Color(0xFFFFCC00)
                            )
                        }
                    }

                    // Stats Section - Only show for Students, not for Admin
                    if (state.isStudent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClickableStatItem(
                                label = "Hours\nSem-1", 
                                value = "${state.sem1Hours.split("/")[0]}", 
                                color = onBackgroundColor,
                                onClick = onSem1HoursClick
                            )
                            StatItem(label = "Events", value = state.eventsAttended, size = 48.sp, color = onBackgroundColor)
                            ClickableStatItem(
                                label = "Hours\nSem-2", 
                                value = "${state.sem2Hours.split("/")[0]}", 
                                color = onBackgroundColor,
                                onClick = onSem2HoursClick
                            )
                        }
                    }
                }

                // Bottom Section with profile details and logout - ROBUST LAYOUT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        //.weight(1f) // Removed weight to allow scrolling in parent Column
                        .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .background(surfaceColor)
                        // Add touch consumer to prevent click-through to buttons behind
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 80.dp) // Reduced bottom padding since logout button is now inside content
                ) {
                    // Name section with refresh button removed (since we have pull to refresh)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.name,
                            color = onSurfaceColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        

                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.isStudent) {
                        // Student Information Section - No headers, just the information
                        // First row: Roll Number
                        LabeledInfoItem(label = "Roll Number", value = state.rollNumber, color = onSurfaceColor)

                        Spacer(modifier = Modifier.height(16.dp))


                        // Third row: Institute ID
                        LabeledInfoItem(label = "Institute ID", value = state.instituteId, color = onSurfaceColor)

                        // Wings or Subject Preferences based on Interface
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (currentInterface == "Teaching Wing") {
                            // Assigned Slots Section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Assigned Slot(s)", 
                                    color = onSurfaceColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (state.assignedSlots.isNotEmpty()) {
                                    state.assignedSlots.forEach { slot ->
                                        Text(
                                            text = slot, 
                                            color = onSurfaceColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "None",
                                        color = onSurfaceColor.copy(alpha = 0.5f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Subject Preference Section for TTW users
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSubjectPreferenceClick() }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Subject Preference", 
                                            color = onSurfaceColor.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (state.subjectPreferences.isEmpty()) {
                                            Text(
                                                text = "Tap to set preferences", 
                                                color = onSurfaceColor,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Preferences",
                                        tint = onSurfaceColor
                                    )
                                }
                                
                                // Display Preference List
                                if (state.subjectPreferences.isNotEmpty()) {

                                    state.subjectPreferences.forEachIndexed { index, subject ->
                                        Text(
                                            text = "${index + 1}. $subject",
                                            color = onSurfaceColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Classes Per Week Section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClassesDialog = true }
                                    .padding(vertical = 4.dp)
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Number of classes per week", 
                                            color = onSurfaceColor.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = state.classesPerWeek,
                                            color = onSurfaceColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Classes Per Week",
                                        tint = onSurfaceColor
                                    )
                                }
                            }
                        } else {
                            // Standard Wing Section for NSS
                            if (state.wings.isNotEmpty()) {
                                LabeledInfoItem(
                                    label = "Wing", 
                                    value = state.wings.joinToString("\n"), 
                                    color = onSurfaceColor
                                )
                            }
                        }
                        
                        // Add bottom spacing to ensure content is not cut off
                        Spacer(modifier = Modifier.height(32.dp))
                    } else { // Admin
                        // Admin Information Section - No headers, just the information
                        // Roll Number
                        LabeledInfoItem(label = "Roll Number", value = state.rollNumber, color = onSurfaceColor)

                        Spacer(modifier = Modifier.height(16.dp))

                        // College Email only
                        LabeledInfoItem(label = "Institute ID", value = state.instituteId, color = onSurfaceColor)

                        // Wings
                        if (state.wings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LabeledInfoItem(
                                label = "Wing", 
                                value = state.wings.joinToString("\n"), 
                                color = onSurfaceColor
                            )
                        }
                        
                        // Admin actions are now in the hamburger menu

                        // Add bottom spacing to ensure content is not cut off
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Remove the absolutely positioned logout button since it's now inside the content
            }

            // White background extension to ensure no black space at bottom
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(whiteBackgroundHeight) // Standard height since logout button is now inside content
//                    .background(Color.White)
//            )
        }
            
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                contentColor = Color(0xFF2196F3)
            )
        }
    }
    }
    

    // Classes Per Week Dialog
    if (showClassesDialog) {
        var classesInput by remember { mutableStateOf(state.classesPerWeek) }
        AlertDialog(
            onDismissRequest = { showClassesDialog = false },
            containerColor = Color(0xff1E1E1E), // Dark container
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    text = "Weekly Classes",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFCC00) // TTW Yellow accent
                )
            },
            text = {
                Column {
                    Text(
                        text = "How many classes do you want to conduct in a week in the government school?",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = classesInput,
                        onValueChange = { classesInput = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFFFFCC00),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color(0xFFFFCC00)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClassesDialog = false
                        onUpdateClassesPerWeek(classesInput)
                    }
                ) {
                    Text("Save", color = Color(0xFFFFCC00), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClassesDialog = false }
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { 
                Text(
                    text = "Logout",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text("Are you sure you want to logout?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }
                ) {
                    Text(
                        text = "Logout",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String, size: androidx.compose.ui.unit.TextUnit = 32.sp, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = size, fontWeight = FontWeight.Bold)
        Text(text = label, color = color.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
fun ClickableStatItem(
    label: String, 
    value: String, 
    color: Color, 
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.TextUnit = 32.sp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = value, 
            color = color, 
            fontSize = size, 
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label, 
            color = color.copy(alpha = 0.7f), 
            fontSize = 12.sp
        )
    }
}

@Composable
fun ProfileDetailItem(label: String, value: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = value,
            color = color.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LabeledInfoItem(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .padding(end = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = label,
            color = color.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            softWrap = true
        )
    }
}


@Composable
fun CircularProfileImage(
    imageUrl: String,
    size: androidx.compose.ui.unit.Dp,
    borderColor: Color
) {
    // Convert Google Drive URL to direct image URL if needed
    fun convertGoogleDriveUrl(url: String): String {
        return if (url.contains("drive.google.com/file/d/")) {
            val fileIdPattern = Regex("drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)")
            val matchResult = fileIdPattern.find(url)
            if (matchResult != null) {
                val fileId = matchResult.groupValues[1]
                "https://drive.google.com/uc?export=view&id=$fileId"
            } else {
                url
            }
        } else {
            url
        }
    }

    val processedImageUrl = if (imageUrl.isNotEmpty()) convertGoogleDriveUrl(imageUrl) else ""

    android.util.Log.d("CircularProfileImage", "Original URL: '$imageUrl'")
    android.util.Log.d("CircularProfileImage", "Processed URL: '$processedImageUrl'")

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White) // White background for better contrast
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (processedImageUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(processedImageUrl)
                    .crossfade(true)
                    .listener(
                        onStart = { android.util.Log.d("CircularProfileImage", "Image loading started for: $processedImageUrl") },
                        onSuccess = { _, _ -> android.util.Log.d("CircularProfileImage", "Image loaded successfully") },
                        onError = { _, result -> android.util.Log.e("CircularProfileImage", "Image loading failed: ${result.throwable}") }
                    )
                    .build(),
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size - 4.dp) // Slightly smaller to show the border and background
                    .clip(CircleShape),
                error = painterResource(id = R.drawable.ic_person), // Use person icon instead of robot
                placeholder = painterResource(id = R.drawable.ic_person) // Use person icon instead of robot
            )
        } else {
            // Default profile icon
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Default Profile",
                tint = Color.Gray,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

@Composable
fun LogoutButton(
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    
//    Box(
//        modifier = modifier
//            .clip(CircleShape)
//            .background(Color.Red.copy(alpha = 0.1f))
//            .clickable { showLogoutDialog = true }
//            .padding(8.dp)
//    ) {
//        Image(
//            painter = painterResource(id = R.drawable.logout),
//            contentDescription = "Logout",
//            modifier = Modifier.size(24.dp),
//            colorFilter = ColorFilter.tint(Color.Red)
//        )
//    }

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,   // more rounded here
                    bottomEnd = 8.dp,
                    bottomStart = 8.dp
                )
            )
            .background(Color.Red.copy(alpha = 0.1f))
            .clickable { showLogoutDialog = true }
            .padding(8.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logout),
            contentDescription = "Logout",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(Color.Red)
        )
    }


    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Logout",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to logout?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }
                ) {
                    Text(
                        text = "Logout",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RefreshButton(
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp,
                    bottomStart = 8.dp
                )
            )
            .background(Color(0xFF2196F3).copy(alpha = 0.1f))
            .clickable { onRefreshClick() }
            .padding(8.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_refresh),
            contentDescription = "Refresh",
            tint = Color(0xFF2196F3),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileMenuButton(
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onMenuClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_more_vert),
            contentDescription = "Menu",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ProfileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSwitchUser: () -> Unit,
    onRefresh: () -> Unit,
    onExportAttendance: () -> Unit,
    onEventHistory: () -> Unit,
    onManageStudents: () -> Unit,
    onChangeSubjects: () -> Unit,
    onFaqs: () -> Unit,
    onLogout: () -> Unit,
    isTeachingWing: Boolean,
    isAdmin: Boolean,
    currentInterface: String,
    modifier: Modifier = Modifier
    ) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.width(224.dp) // Fixed width like in the reference design
    ) {
        // Note: Switch User moved to header as a standalone icon

        // Export Attendance Matrix - only for admins in NSS interface
        if (isAdmin && currentInterface == "NSS") {
            DropdownMenuItem(
                text = { Text("Export Attendance Matrix", color = Color(0xFF444343)) },
                onClick = {
                    onExportAttendance()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = null,
                        tint = Color(0xFF444343)
                    )
                }
            )
        }


        // Event History - only for admins in NSS interface
        if (isAdmin && currentInterface == "NSS") {
            DropdownMenuItem(
                text = { Text("Event History", color = Color(0xFF444343)) },
                onClick = {
                    onEventHistory()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_calendar),
                        contentDescription = null,
                        tint = Color(0xFF444343)
                    )
                }
            )
        }

        // Manage Students - only for Teaching Wing interface
        if (isAdmin && currentInterface == "Teaching Wing") {
            DropdownMenuItem(
                text = { Text("Manage Students", color = Color(0xFF444343)) },
                onClick = {
                    onManageStudents()
                    onDismiss()
                },
                leadingIcon = {
                     Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF444343)
                    )
                }
            )
        }

        // Change Subjects - only for Teaching Wing interface
        if (isAdmin && currentInterface == "Teaching Wing") {
            DropdownMenuItem(
                text = { Text("Change Subjects", color = Color(0xFF444343)) },
                onClick = {
                    onChangeSubjects()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_book),
                        contentDescription = null,
                        tint = Color(0xFF444343)
                    )
                }
            )
        }

        // FAQs/Help - available for all users
        DropdownMenuItem(
            text = { Text("Help & Support", color = Color(0xFF444343)) },
            onClick = {
                onFaqs()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_faq),
                    contentDescription = null,
                    tint = Color(0xFF444343)
                )
            }
        )

        // Logout - available for all users
        DropdownMenuItem(
            text = { Text("Logout", color = Color(0xFF444343)) },
            onClick = {
                onLogout()
                onDismiss()
            },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_logout),
                        contentDescription = null,
                        tint = Color(0xFF444343)
                    )
                }
        )
    }
}

@Composable
private fun ProfilePreview() {
    MaterialTheme {
        ProfileScreen(
            state = ProfileUiState(name = "Loading...", location = "N/A"),
            onLogoutClick = {},
            onRefreshClick = {},
            onChatClick = {},
            onScheduleClick = {},
            onSwitchInterfaceClick = {},
            onSem1HoursClick = {},
            onSem2HoursClick = {},
            onFaqsClick = {},
            currentInterface = "Teaching Wing",
            teachingWing = true
        )
    }
}