package com.phad.chatapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.phad.chatapp.adapters.AdminAdapter
import com.phad.chatapp.models.Admin
import com.phad.chatapp.repositories.FirestoreRepository
import kotlinx.coroutines.launch

class AdminListFragment : Fragment() {
    private val TAG = "AdminListFragment"
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var toolbar: Toolbar
    private val repository = FirestoreRepository()
    private lateinit var adapter: AdminAdapter



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progress_bar)
        toolbar = view.findViewById(R.id.toolbar)
        
        setupToolbar()
        setupRecyclerView()
        loadAdmin1Only()
    }
    
    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            // Navigate back to the chat fragment
            requireActivity().supportFragmentManager.popBackStack()
        }
    }
    
    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        adapter = AdminAdapter(mutableListOf()) { admin ->
            // Handle admin click
            Toast.makeText(context, "Clicked on ${admin.name}", Toast.LENGTH_SHORT).show()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }
    
    /**
     * Load only Admin1 users from Firestore
     */
    private fun loadAdmin1Only() {
        showLoading()
        Log.d(TAG, "Starting to load Admin1 data...")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Fetch only Admin1 users from users collection
                Log.d(TAG, "Calling repository.getAdmin1UsersOnly()")
                val admin1List = repository.getAdmin1UsersOnly()
                Log.d(TAG, "Received ${admin1List.size} Admin1 users from repository")
                
                // Debug each admin
                admin1List.forEachIndexed { index, admin ->
                    Log.d(TAG, "Admin $index: name=${admin.name}, desc=${admin.description}, roll=${admin.rollNumber}")
                }
                
                activity?.runOnUiThread {
                    if (admin1List.isNotEmpty()) {
                        // Update adapter with new data
                        adapter.updateData(admin1List)
                        showContent()
                    } else {
                        // Show empty state
                        Log.w(TAG, "No Admin1 users found in users collection")
                        showEmptyState("No Admin1 users found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Admin1 data", e)
                e.printStackTrace()
                
                val errorMessage = when (e) {
                    is FirebaseFirestoreException -> {
                        when (e.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                                "Firestore permission denied. Check your security rules."
                            FirebaseFirestoreException.Code.UNAVAILABLE -> 
                                "Firestore is unavailable. Check your internet connection."
                            FirebaseFirestoreException.Code.UNAUTHENTICATED -> 
                                "Authentication error. You may need to sign in."
                            else -> "Firestore error: ${e.message}"
                        }
                    }
                    is FirebaseException -> "Firebase error: ${e.message}"
                    else -> "Error: ${e.message}"
                }
                
                activity?.runOnUiThread {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    showEmptyState(errorMessage)
                }
            }
        }
    }
    
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.GONE
    }
    
    private fun showContent() {
        progressBar.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
    }
    
    private fun showEmptyState(message: String = getString(R.string.no_admins_found)) {
        progressBar.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = message
    }
} 