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

@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    openIssuesCount: Int = 0,
    userWings: List<String> = emptyList(),
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
            .padding(top = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            
            Text(
                text = "Emergency Contacts",
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Ambulance Contact
            ContactCard(
                icon = Icons.Default.LocalHospital,
                title = "Ambulance",
                detail = HelpConstants.HELP_AMBULANCE_NUMBER,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                secondaryTextColor = secondaryTextColor,
                iconColor = Color(0xFFE53935),
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${HelpConstants.HELP_AMBULANCE_NUMBER}"))
                    context.startActivity(intent)
                }
            )

            if (helpUiState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(32.dp))
            } else if (helpUiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = helpUiState.error,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                TextButton(
                    onClick = onRetryContacts,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Retry", color = primaryColor)
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                helpUiState.contactGroups.forEach { group ->
                    val contactsToShow = if (group.filterByWing) {
                        group.contacts.filter { contact ->
                            userWings.any { wing -> contact.role.equals(wing, ignoreCase = true) }
                        }
                    } else {
                        group.contacts
                    }

                    ContactCard(
                        icon = Icons.Default.Phone,
                        title = group.title,
                        detail = "View Contacts",
                        surfaceColor = surfaceColor,
                        onSurfaceColor = onSurfaceColor,
                        secondaryTextColor = secondaryTextColor,
                        iconColor = primaryColor,
                        onClick = {
                            selectedGroupTitle = group.title
                            selectedGroupContacts = contactsToShow
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                text = "Issue Reporting",
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Raise an Issue Button
            Button(
                onClick = onRaiseIssueClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ReportProblem,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Raise an Issue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Extra space for bottom nav bar
            // Admin: Resolve Issues Banner
            if (isAdmin) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onResolveIssuesClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(12.dp),
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
            
            Spacer(modifier = Modifier.height(100.dp))
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.ui_white))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor)
                Spacer(modifier = Modifier.height(16.dp))

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (contacts.isEmpty()) {
                        Text(
                            text = "No contacts available for your assigned wings.",
                            fontSize = 14.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        contacts.forEach { contact ->
                            ContactCard(
                                icon = Icons.Default.Phone,
                                title = contact.name,
                                detail = "${contact.role} \n${contact.phone}",
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                secondaryTextColor = secondaryTextColor,
                                iconColor = primaryColor,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
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
