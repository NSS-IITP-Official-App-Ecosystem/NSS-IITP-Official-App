package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R

/**
 * Adapter for displaying Admin1 (subcoordinators) users from TWApp
 */
class Admin1Adapter(private val admins: List<Map<String, Any>>) :
    RecyclerView.Adapter<Admin1Adapter.Admin1ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Admin1ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin, parent, false)
        return Admin1ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: Admin1ViewHolder, position: Int) {
        holder.bind(admins[position])
    }
    
    override fun getItemCount(): Int = admins.size
    
    class Admin1ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.text_name)
        private val textRollNumber: TextView = itemView.findViewById(R.id.text_roll_number)
        private val textDesignation: TextView = itemView.findViewById(R.id.text_designation)
        private val textCommittee: TextView = itemView.findViewById(R.id.text_committee)
        private val textEmail: TextView = itemView.findViewById(R.id.text_email)
        private val textMobile: TextView = itemView.findViewById(R.id.text_mobile)
        private val textAdminName: TextView = itemView.findViewById(R.id.text_admin_name)
        private val textAdminDescription: TextView = itemView.findViewById(R.id.text_admin_description)
        
        fun bind(admin: Map<String, Any>) {
            // Extract data safely
            val name = admin["name"] as? String ?: "Unknown"
            val rollNumber = admin["rollNumber"] as? String ?: "Unknown"
            val designation = admin["designation"] as? String ?: "Subcoordinator"
            val committee = admin["committee"] as? String ?: "Unknown"
            val email = admin["email"] as? String ?: "No email"
            val phone = admin["phone"] as? String ?: "No phone"
            
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
