package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R

/**
 * Adapter for displaying student data in a RecyclerView
 */
class StudentAdapter(private val students: List<Map<String, Any>>) : 
    RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {
    
    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.text_name)
        val textRollNumber: TextView = view.findViewById(R.id.text_roll_number)
        val textEmail: TextView = view.findViewById(R.id.text_email)
        val textDetails: TextView = view.findViewById(R.id.text_details)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        
        // Get student details with null safety - field names match CSV columns
        val name = student["name"] as? String ?: "Unknown"
        val rollNumber = student["roll_no"] as? String ?: "Unknown"
        val email = student["gmail_id"] as? String ?: student["institute_id"] as? String ?: "Unknown"
        
        // Set text views
        holder.textName.text = name
        holder.textRollNumber.text = rollNumber
        holder.textEmail.text = email
        
        // Create additional details string
        val detailsBuilder = StringBuilder()
        
        // Add subject preferences
        val pref1 = student["subjec_prefrence_1"] as? String
        val pref2 = student["sub_preference2"] as? String
        val pref3 = student["sub_preference_3"] as? String
        
        if (!pref1.isNullOrEmpty() || !pref2.isNullOrEmpty() || !pref3.isNullOrEmpty()) {
            detailsBuilder.append("Preferences: ")
            if (!pref1.isNullOrEmpty()) detailsBuilder.append("1. $pref1 ")
            if (!pref2.isNullOrEmpty()) detailsBuilder.append("2. $pref2 ")
            if (!pref3.isNullOrEmpty()) detailsBuilder.append("3. $pref3")
            detailsBuilder.append("\n")
        }
        
        // Add NSS group
        val nssGroup = student["nss_gro"] as? String
        if (!nssGroup.isNullOrEmpty()) {
            detailsBuilder.append("NSS Group: $nssGroup\n")
        }
        
        // Add interview score
        val interviewScore = student["interview_score"] as? String
        if (!interviewScore.isNullOrEmpty()) {
            detailsBuilder.append("Interview Score: $interviewScore\n")
        }
        
        // Add academic group
        val academicGroup = student["academic_grp"] as? String
        if (!academicGroup.isNullOrEmpty()) {
            detailsBuilder.append("Academic Group: $academicGroup\n")
        }
        
        // Add selection status
        val selected = student["selected"] as? String
        if (!selected.isNullOrEmpty()) {
            detailsBuilder.append("Selected: $selected\n")
        }
        
        // Add mobile number
        val mobileNumber = student["mobile_no"] as? String
        if (!mobileNumber.isNullOrEmpty()) {
            detailsBuilder.append("Mobile: $mobileNumber\n")
        }
        
        // Set details text
        holder.textDetails.text = detailsBuilder.toString()
    }
    
    override fun getItemCount() = students.size
} 