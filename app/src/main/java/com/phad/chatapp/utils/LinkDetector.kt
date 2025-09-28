package com.phad.chatapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

/**
 * Utility class for detecting and making clickable links in text
 */
object LinkDetector {
    private const val TAG = "LinkDetector"
    
    // Email pattern
    private val EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    )
    
    // Phone number patterns
    private val PHONE_PATTERN = Pattern.compile(
        "\\b(?:(?:\\+91|91)?[6-9]\\d{9}|\\+91[6-9]\\d{9})\\b"
    )
    
    // WhatsApp link pattern
    private val WHATSAPP_PATTERN = Pattern.compile(
        "\\b(?:(?:\\+91|91)?[6-9]\\d{9}|\\+91[6-9]\\d{9})\\b"
    )
    
    // Manual link pattern: \link(url)
    private val MANUAL_LINK_PATTERN = Pattern.compile(
        "\\\\link\\(([^)]+)\\)"
    )
    
    /**
     * Process text to remove manual link delimiters for display
     */
    fun processTextForDisplay(text: String): String {
        return text.replace(Regex("\\\\link\\(([^)]+)\\)"), "$1")
    }
    
    /**
     * Create clickable text with email, phone number, and manual links
     */
    @Composable
    fun ClickableTextWithLinks(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle = TextStyle.Default,
        linkColor: Color = Color(0xFF2196F3),
        onLinkClick: (String, String) -> Unit = { _, _ -> }
    ) {
        val context = LocalContext.current
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            
            // Find all matches
            val matches = mutableListOf<Match>()
            
            // Find email matches
            val emailMatcher = EMAIL_PATTERN.matcher(text)
            while (emailMatcher.find()) {
                matches.add(Match(emailMatcher.start(), emailMatcher.end(), emailMatcher.group(), "email"))
            }
            
            // Find phone number matches
            val phoneMatcher = PHONE_PATTERN.matcher(text)
            while (phoneMatcher.find()) {
                matches.add(Match(phoneMatcher.start(), phoneMatcher.end(), phoneMatcher.group(), "phone"))
            }
            
            // Find manual link matches: \link(url)
            val linkMatcher = MANUAL_LINK_PATTERN.matcher(text)
            while (linkMatcher.find()) {
                val url = linkMatcher.group(1) ?: "" // url
                matches.add(Match(linkMatcher.start(), linkMatcher.end(), url, "manual_link"))
            }
            
            // Sort matches by start position
            matches.sortBy { it.start }
            
            // Build annotated string
            matches.forEach { match ->
                // Add text before the match
                if (match.start > lastIndex) {
                    append(text.substring(lastIndex, match.start))
                }
                
                // Add the clickable link
                withStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    pushStringAnnotation(
                        tag = match.type,
                        annotation = match.text
                    )
                    // For manual links, show the URL without the delimiters
                    if (match.type == "manual_link") {
                        append(match.text)
                    } else {
                        append(match.text)
                    }
                    pop()
                }
                
                lastIndex = match.end
            }
            
            // Add remaining text
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
        
        ClickableText(
            text = annotatedString,
            modifier = modifier,
            style = style,
            onClick = { offset ->
                annotatedString.getStringAnnotations(
                    tag = "email",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    openEmail(context, annotation.item)
                    onLinkClick("email", annotation.item)
                }
                
                annotatedString.getStringAnnotations(
                    tag = "phone",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    openWhatsApp(context, annotation.item)
                    onLinkClick("phone", annotation.item)
                }
                
                annotatedString.getStringAnnotations(
                    tag = "manual_link",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    openUrl(context, annotation.item)
                    onLinkClick("manual_link", annotation.item)
                }
            }
        )
    }
    
    /**
     * Open email app with the specified email address
     */
    private fun openEmail(context: Context, email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened email app for: $email")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open email app for: $email", e)
        }
    }
    
    /**
     * Open WhatsApp with the specified phone number
     */
    private fun openWhatsApp(context: Context, phoneNumber: String) {
        try {
            val cleanNumber = cleanPhoneNumber(phoneNumber)
            val whatsappUrl = "https://wa.me/$cleanNumber"
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(whatsappUrl)
                setPackage("com.whatsapp")
            }
            
            // Try WhatsApp first, fallback to browser
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "Opened WhatsApp for: $cleanNumber")
            } else {
                // Fallback to browser
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                context.startActivity(browserIntent)
                Log.d(TAG, "Opened browser for WhatsApp: $cleanNumber")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WhatsApp for: $phoneNumber", e)
        }
    }
    
    /**
     * Open URL in browser
     */
    private fun openUrl(context: Context, url: String) {
        try {
            val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "https://$url"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(cleanUrl)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened URL: $cleanUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
        }
    }
    
    /**
     * Clean and format phone number for WhatsApp
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except +
        var cleaned = phoneNumber.replace(Regex("[^\\d+]"), "")
        
        // Handle different formats
        when {
            cleaned.startsWith("+91") -> {
                // Already has country code
                return cleaned
            }
            cleaned.startsWith("91") && cleaned.length == 12 -> {
                // Has country code without +
                return "+$cleaned"
            }
            cleaned.length == 10 -> {
                // 10-digit number, add +91
                return "+91$cleaned"
            }
            else -> {
                // Return as is, WhatsApp will handle it
                return cleaned
            }
        }
    }
    
    /**
     * Data class to represent a match
     */
    private data class Match(
        val start: Int,
        val end: Int,
        val text: String,
        val type: String
    )
}
