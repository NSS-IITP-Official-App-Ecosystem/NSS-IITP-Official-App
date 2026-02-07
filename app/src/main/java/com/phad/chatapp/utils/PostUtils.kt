package com.phad.chatapp.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.view.View
import java.util.regex.Pattern

object PostUtils {
    
    /**
     * Detects if the given text contains any URLs
     */
    fun containsUrl(text: String): Boolean {
        val urlPattern = Pattern.compile(
            "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)" +
            "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*" +
            "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
            Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
        )
        return urlPattern.matcher(text).find()
    }
    
    /**
     * Creates a spannable string with clickable links
     */
    fun makeLinksClickable(text: String, onLinkClick: (String) -> Unit): SpannableString {
        val spannable = SpannableString(text)
        Linkify.addLinks(spannable, Linkify.WEB_URLS)
        
        val spans = spannable.getSpans(0, spannable.length, android.text.style.URLSpan::class.java)
        for (span in spans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val url = span.url
            
            spannable.removeSpan(span)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onLinkClick(url)
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        return spannable
    }
    
    /**
     * Truncates text and adds "Read more..." if it exceeds maxLines
     */
    fun truncateText(text: String, maxChars: Int): Pair<String, Boolean> {
        return if (text.length > maxChars) {
            Pair(text.substring(0, maxChars) + "...", true)
        } else {
            Pair(text, false)
        }
    }
}
