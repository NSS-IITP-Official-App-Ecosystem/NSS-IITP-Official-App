package com.phad.chatapp.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude

data class User(
    @DocumentId
    var id: String = "",
    
    var name: String = "",
    var email: String = "",
    
    @PropertyName("roll_number")
    var rollNumber: String = "",
    
    @PropertyName("user_type")
    var userType: String = "",
    
    var description: String = "",
    
    @PropertyName("contact_number")
    var contactNumber: String = "",
    
    // Use String type for year to handle different data types from Firestore
    @PropertyName("year")
    var yearString: String = "0",
    
    @PropertyName("profile_image_url")
    var profileImageUrl: String? = null,
    
    @PropertyName("reference_image_url")
    var referenceImageUrl: String? = null
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", "", "", "", "", "0", null, null)
    
    // Computed property to get year as Int safely
    @get:Exclude
    val year: Int
        get() = yearString.toIntOrNull() ?: 0
    
    // Setter for year that handles both String and Int
    @Exclude
    fun setYear(value: Any?) {
        yearString = when (value) {
            is Int -> value.toString()
            is Long -> value.toString()
            is Double -> value.toInt().toString()
            is String -> value
            null -> "0"
            else -> "0"
        }
    }
    
    /**
     * Check if the user has a reference image for face recognition
     * @return true if the user has a reference image, false otherwise
     */
    @Exclude
    fun hasReferenceImage(): Boolean {
        return !referenceImageUrl.isNullOrEmpty()
    }
} 