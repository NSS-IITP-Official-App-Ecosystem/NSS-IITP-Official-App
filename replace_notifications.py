import codecs, re
import os

files = [
    r'e:\ADR\Samaveda\NSS\temp_210326\NSS-App\app\src\main\java\com\phad\chatapp\fragments\NssHomeFragment.kt',
    r'e:\ADR\Samaveda\NSS\temp_210326\NSS-App\app\src\main\java\com\phad\chatapp\fragments\HomeFragment.kt'
]

replacement = '''    private fun sendUpdateNotification(update: Update) {
        android.util.Log.d(TAG, "Preparing update notification for: ${update.id}")
        try {
            val title = update.title ?: "New Update"
            val contentChunk = update.content ?: ""
            val message = "$title: ${contentChunk.take(100)}${if (contentChunk.length > 100) "..." else ""}"
            val fullMessage = if (update.documentUrl != null || !update.documentUrls.isNullOrEmpty()) "$message [Contains document]" else message
            
            val targetType = when (update.updateType) {
                1 -> "ttw"
                2 -> "nss"
                3 -> "all"
                else -> "all"
            }
            
            val ndata = hashMapOf<String, Any>(
                "title" to title,
                "body" to fullMessage,
                "targetRole" to "all",
                "targetWing" to "all",
                "targetType" to targetType,
                "type" to "UPDATE_NOTIFICATION",
                "creatorId" to (update.authorId ?: "Admin"),
                "isRead" to false,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("app_notifications").add(ndata)
                .addOnSuccessListener { android.util.Log.d(TAG, "Successfully created app_notification") }
                .addOnFailureListener { e -> android.util.Log.e(TAG, "Failed creating app_notification", e) }
                
        } catch (e: Exception) { 
            android.util.Log.e(TAG, "Error: ${e.message}", e) 
        }
    }'''

pattern = re.compile(r'    private fun sendUpdateNotification\(update: Update\).*?    private fun sendNotificationToUsers\(.*?\} else \{.*?\n    \}', re.DOTALL)

for fpath in files:
    with codecs.open(fpath, 'r', 'utf-8') as f:
        content = f.read()
    
    new_content = pattern.sub(replacement, content)
    
    if new_content != content:
        with codecs.open(fpath, 'w', 'utf-8') as f:
            f.write(new_content)
        print(f"Updated {fpath}")
    else:
        print(f"No changes made to {fpath}")
