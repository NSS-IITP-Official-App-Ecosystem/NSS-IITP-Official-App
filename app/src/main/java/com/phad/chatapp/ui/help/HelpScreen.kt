package com.phad.chatapp.ui.help

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phad.chatapp.R
import com.phad.chatapp.utils.HelpConstants

private val PaddingScreenHorizontal = 24.dp
private val PaddingScreenVertical = 16.dp
private val SpacingBtwSections = 20.dp
private val SpacingBtwElements = 16.dp
private val SpacingBtwCards = 12.dp
private val SpacingBtwIconAndText = 8.dp
private val SpacingAfterLoading = 32.dp
private val SpacingBottomNav = 100.dp

private val CornerRadiusCard = 12.dp
private val CornerRadiusButton = 12.dp
private val CornerRadiusIconBg = 8.dp

private val IconSizeLarge = 40.dp
private val IconSizeMedium = 24.dp
private val IconSizeSmall = 20.dp
private val BoxSizeIcon = 48.dp

private val ButtonHeightNormal = 56.dp
private val ButtonHeightLarge = 72.dp

private val CornerRadiusDialog = 16.dp
private val PaddingDialog = 20.dp
private val MaxHeightDialogList = 320.dp
private val SpacingBeforeDialogButtons = 24.dp

@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    openIssuesCount: Int = 0,
    userWings: List<String> = emptyList(),
    userRollNumber: String = "",
    helpUiState: HelpUiState,
    onRetryContacts: () -> Unit,
    onRaiseIssueClick: () -> Unit,
    onResolveIssuesClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Light mode colors
    val backgroundColor = colorResource(id = R.color.ui_white)
    val surfaceColor = colorResource(id = R.color.ui_gray_light)
    val primaryColor = colorResource(id = R.color.ui_blue)
    val onSurfaceColor = colorResource(id = R.color.ui_dark)
    val secondaryTextColor = colorResource(id = R.color.ui_gray_dark)
    val accentColor = colorResource(id = R.color.ui_blue)
    
    val scrollState = rememberScrollState()
    
    var selectedGroupContacts by remember { mutableStateOf<List<com.phad.chatapp.models.ContactPerson>?>(null) }
    var selectedGroupTitle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(top = PaddingScreenVertical)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PaddingScreenHorizontal, vertical = PaddingScreenVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Help & Support",
                color = onSurfaceColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = PaddingScreenHorizontal, vertical = PaddingScreenVertical)
        ) {
            
            Text(
                text = "Emergency Contacts",
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = SpacingBtwElements)
            )

            if (helpUiState.isLoading) {
                Spacer(modifier = Modifier.height(SpacingBtwElements))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(SpacingAfterLoading))
            } else if (helpUiState.error != null) {
                Spacer(modifier = Modifier.height(SpacingBtwElements))
                Text(
                    text = helpUiState.error,
                    color = colorResource(id = R.color.error_red),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                TextButton(
                    onClick = onRetryContacts,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Retry", color = primaryColor)
                }
                Spacer(modifier = Modifier.height(SpacingBtwElements))
            } else {
                Spacer(modifier = Modifier.height(SpacingBtwCards))
                helpUiState.contactGroups.forEach { group ->
                    val filteredBySelf = group.contacts.filter { it.rollNumber != userRollNumber }
                    val contactsToShow = if (group.filterByWing) {
                        filteredBySelf.filter { contact ->
                            userWings.any { wing -> contact.role.equals(wing, ignoreCase = true) }
                        }
                    } else {
                        filteredBySelf
                    }
                    
                    if (contactsToShow.isNotEmpty()) {

                    val iconVector = when (group.icon) {
                        "hospital" -> Icons.Default.LocalHospital
                        "email" -> Icons.Default.Email
                        "code" -> Icons.Default.Code
                        else -> Icons.Default.Phone
                    }
                    val currentIconColor = if (group.icon == "hospital") colorResource(id = R.color.help_ambulance_red) else primaryColor

                    ContactCard(
                        icon = iconVector,
                        title = group.title,
                        detail = "View Contacts",
                        surfaceColor = surfaceColor,
                        onSurfaceColor = onSurfaceColor,
                        secondaryTextColor = secondaryTextColor,
                        iconColor = currentIconColor,
                        onClick = {
                            selectedGroupTitle = group.title
                            selectedGroupContacts = contactsToShow
                        }
                        )
                        Spacer(modifier = Modifier.height(SpacingBtwCards))
                    }
                }
                Spacer(modifier = Modifier.height(SpacingBtwSections))
            }

            Text(
                text = if (isAdmin) "Issue Tracking" else "Issue Reporting",
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = SpacingBtwElements)
            )

            // Raise an Issue Button
            if (!isAdmin) {
                Button(
                    onClick = onRaiseIssueClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeightNormal),
                    shape = RoundedCornerShape(CornerRadiusButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        modifier = Modifier.size(IconSizeMedium)
                    )
                    Spacer(modifier = Modifier.width(SpacingBtwIconAndText))
                    Text(
                        text = "Raise an Issue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Extra space for bottom nav bar
            // Admin: Resolve Issues Banner
            if (isAdmin) {
                Spacer(modifier = Modifier.height(SpacingBtwElements))
                Button(
                    onClick = onResolveIssuesClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeightLarge),
                    shape = RoundedCornerShape(CornerRadiusButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(id = R.color.issue_admin_primary),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Resolve Issues", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "You have $openIssuesCount pending issues",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = "Resolve Issues")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(SpacingBottomNav))
        }
    }
    
    if (selectedGroupContacts != null) {
        ContactListDialog(
            title = selectedGroupTitle,
            contacts = selectedGroupContacts!!,
            onDismiss = { selectedGroupContacts = null }
        )
    }
}

@Composable
fun ContactCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryTextColor: Color,
    iconColor: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(CornerRadiusCard),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingBtwElements),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(BoxSizeIcon)
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(CornerRadiusIconBg)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(IconSizeMedium)
                )
            }
            Spacer(modifier = Modifier.width(SpacingBtwElements))
            Column {
                Text(
                    text = title,
                    color = onSurfaceColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    color = secondaryTextColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ContactListDialog(
    title: String,
    contacts: List<com.phad.chatapp.models.ContactPerson>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val onSurfaceColor = colorResource(id = R.color.ui_dark)
    val secondaryTextColor = colorResource(id = R.color.ui_gray_dark)
    val surfaceColor = colorResource(id = R.color.ui_gray_light)
    val primaryColor = colorResource(id = R.color.ui_blue)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(CornerRadiusDialog),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.ui_white))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingDialog)
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor)
                Spacer(modifier = Modifier.height(SpacingBtwElements))

                LazyColumn(
                    modifier = Modifier.heightIn(max = MaxHeightDialogList), // Approx 4 entries
                    verticalArrangement = Arrangement.spacedBy(SpacingBtwIconAndText)
                ) {
                    if (contacts.isEmpty()) {
                        item {
                            Text(
                                text = "No contacts available for your assigned wings.",
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(vertical = SpacingBtwIconAndText)
                            )
                        }
                    } else {
                        items(contacts) { contact ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(CornerRadiusCard),
                                colors = CardDefaults.cardColors(containerColor = surfaceColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SpacingBtwElements),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            color = onSurfaceColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(SpacingBtwIconAndText),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (contact.phone != null) {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier
                                                    .size(IconSizeLarge)
                                                    .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(CornerRadiusIconBg))
                                            ) {
                                                Icon(Icons.Default.Phone, contentDescription = "Call", tint = primaryColor, modifier = Modifier.size(IconSizeSmall))
                                            }
                                        }
                                        if (contact.email != null) {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("mailto:${contact.email}")
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier
                                                    .size(IconSizeLarge)
                                                    .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(CornerRadiusIconBg))
                                            ) {
                                                Icon(Icons.Default.Email, contentDescription = "Email", tint = primaryColor, modifier = Modifier.size(IconSizeSmall))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SpacingBeforeDialogButtons))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = primaryColor)
                    }
                }
            }
        }
    }
}
