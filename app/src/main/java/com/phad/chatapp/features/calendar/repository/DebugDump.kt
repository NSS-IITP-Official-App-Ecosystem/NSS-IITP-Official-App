// This script is to dump a document from generatedSchedules for examination
// I won't run this but I'll write some code to log the structure of generatedSchedules in CalendarRepository

        // Add this to logging for debug
        /*
        val snapshot = firestore.collection("generatedSchedules").get().await()
        for (document in snapshot.documents) {
            val data = document.data ?: continue
            Log.d("CalendarRepository", "FOUND SCHEDULE DOC ID: ${document.id}")
            Log.d("CalendarRepository", "KEYS: ${data.keys.joinToString()}")
            
            val assignmentsList1 = data["optimizedAssignments"] as? List<Map<String, Any>>
            val assignmentsList2 = data["assignments"] as? List<Map<String, Any>>
            val assignmentsList3 = data["assignment"] as? List<Map<String, Any>>
            Log.d("CalendarRepository", "Sizes: opt=${assignmentsList1?.size}, assign=${assignmentsList2?.size}")
        }
        */
