package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R

/**
 * Adapter for displaying Admin2 (coordinators) users from TWApp
 */
class Admin2Adapter(private val coordinators: List<Map<String, Any>>) :
    RecyclerView.Adapter<Admin2Adapter.Admin2ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Admin2ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin, parent, false)
        return Admin2ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: Admin2ViewHolder, position: Int) {
        holder.bind(coordinators[position])
    }
    
    override fun getItemCount(): Int = coordinators.size
    
    class Admin2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.text_name)
        private val textRollNumber: TextView = itemView.findViewById(R.id.text_roll_number)
        private val textDesignation: TextView = itemView.findViewById(R.id.text_designation)
        private val textCommittee: TextView = itemView.findViewById(R.id.text_committee)
        private val textEmail: TextView = itemView.findViewById(R.id.text_email)
        private val textMobile: TextView = itemView.findViewById(R.id.text_mobile)
        private val textAdminName: TextView = itemView.findViewById(R.id.text_admin_name)
        private val textAdminDescription: TextView = itemView.findViewById(R.id.text_admin_description)
        
        fun bind(coordinator: Map<String, Any>) {
            // Extract data safely
            val name = coordinator["name"] as? String ?: "Unknown"
            val rollNumber = coordinator["rollNumber"] as? String ?: "Unknown"
            val designation = "Coordinator" // Fixed designation for coordinators
            val committee = coordinator["committee"] as? String ?: "Unknown"
            val email = coordinator["email"] as? String ?: "No email"
            val phone = coordinator["phone"] as? String ?: "No phone"
            
            // Set data to views
            textName.text = name
            textRollNumber.text = "Roll Number: $rollNumber"
            textDesignation.text = "Designation: $designation"
            textCommittee.text = "Committee: $committee"
            textEmail.text = "Email: $email"
            textMobile.text = "Phone: $phone"
            
            // Set summary views
            textAdminName.text = name
            textAdminDescription.text = "$designation - $committee"
        }
    }
}
