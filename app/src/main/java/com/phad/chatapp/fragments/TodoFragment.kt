package com.phad.chatapp.fragments

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phad.chatapp.R
import com.phad.chatapp.adapters.TaskAdapter
import com.phad.chatapp.databinding.FragmentTodoBinding
import com.phad.chatapp.models.Task
import com.phad.chatapp.repositories.TaskRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TodoFragment : Fragment() {
    private val TAG = "TodoFragment"
    private var _binding: FragmentTodoBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var taskAdapter: TaskAdapter
    private val taskRepository = TaskRepository()
    private var selectedDueDate: Date? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodoBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupTaskRecyclerView()
        setupAddTaskButton()
        loadTasks()
    }
    
    private fun setupToolbar() {
        binding.toolbar.findViewById<View>(R.id.backButton).setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }
    
    private fun setupTaskRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskCompletionChanged = { task, isCompleted ->
                updateTaskCompletion(task, isCompleted)
            },
            onTaskDeleted = { task ->
                confirmTaskDeletion(task)
            }
        )
        
        binding.tasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
    }
    
    private fun setupAddTaskButton() {
        binding.addTaskFab.setOnClickListener {
            showAddTaskDialog()
        }
    }
    
    private fun loadTasks() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE
        
        taskRepository.getUserTasks()
            .addOnSuccessListener { tasks ->
                binding.progressBar.visibility = View.GONE
                
                if (tasks.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                }
                
                taskAdapter.updateTasks(tasks)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading tasks", e)
                Toast.makeText(context, "Error loading tasks: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun showAddTaskDialog() {
        // Reset selected due date
        selectedDueDate = null
        
        // Initialize the dialog
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_task)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Find views in the dialog
        val taskTitleInput = dialog.findViewById<EditText>(R.id.taskTitleInput)
        val taskDescriptionInput = dialog.findViewById<EditText>(R.id.taskDescriptionInput)
        val dueDateSelector = dialog.findViewById<LinearLayout>(R.id.dueDateSelector)
        val dueDateText = dialog.findViewById<TextView>(R.id.dueDateText)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val addTaskButton = dialog.findViewById<Button>(R.id.addTaskButton)
        
        // Set up due date selector
        dueDateSelector.setOnClickListener {
            showDatePicker { date ->
                selectedDueDate = date
                dueDateText.text = formatDate(date)
            }
        }
        
        // Set up cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Set up add task button
        addTaskButton.setOnClickListener {
            val title = taskTitleInput.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val description = taskDescriptionInput.text.toString().trim()
            
            // Disable buttons to prevent multiple submissions
            addTaskButton.isEnabled = false
            cancelButton.isEnabled = false
            
            taskRepository.addTask(title, description, selectedDueDate)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Task added", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadTasks() // Reload tasks
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error adding task", e)
                    Toast.makeText(requireContext(), "Error adding task: ${e.message}", Toast.LENGTH_SHORT).show()
                    addTaskButton.isEnabled = true
                    cancelButton.isEnabled = true
                }
        }
        
        dialog.show()
    }
    
    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        
        // Set initial date to selectedDueDate if it exists, otherwise today
        if (selectedDueDate != null) {
            calendar.time = selectedDueDate!!
        }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                onDateSelected(calendar.time)
            },
            year, month, day
        ).show()
    }
    
    private fun formatDate(date: Date): String {
        val format = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return format.format(date)
    }
    
    private fun updateTaskCompletion(task: Task, isCompleted: Boolean) {
        taskRepository.updateTaskCompletion(task.id, isCompleted)
            .addOnSuccessListener {
                // Task updated successfully in the database
                // The UI is already updated via the checkbox
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating task", e)
                Toast.makeText(context, "Error updating task: ${e.message}", Toast.LENGTH_SHORT).show()
                // Reload tasks to ensure UI is in sync with the database
                loadTasks()
            }
    }
    
    private fun confirmTaskDeletion(task: Task) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this task?")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { dialog, _ ->
                deleteTask(task)
                dialog.dismiss()
            }
            .show()
    }
    
    private fun deleteTask(task: Task) {
        taskRepository.deleteTask(task.id)
            .addOnSuccessListener {
                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                loadTasks() // Reload tasks
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting task", e)
                Toast.makeText(context, "Error deleting task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance(): TodoFragment {
            return TodoFragment()
        }
    }
} 