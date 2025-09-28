package com.phad.chatapp.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onTaskCompletionChanged: (Task, Boolean) -> Unit,
    private val onTaskDeleted: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.taskCheckbox)
        private val titleTextView: TextView = itemView.findViewById(R.id.taskTitle)
        private val dueDateTextView: TextView = itemView.findViewById(R.id.taskDueDate)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(task: Task) {
            // Set task title
            titleTextView.text = task.title
            
            // Set checkbox state without triggering the listener
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = task.isCompleted
            
            // Apply strikethrough if task is completed
            if (task.isCompleted) {
                titleTextView.paintFlags = titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                titleTextView.paintFlags = titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            
            // Set due date text
            dueDateTextView.text = formatDueDate(task.dueDate)
            
            // Set checkbox listener
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onTaskCompletionChanged(task, isChecked)
            }
            
            // Set delete button listener
            deleteButton.setOnClickListener {
                onTaskDeleted(task)
            }
        }
        
        private fun formatDueDate(date: Date?): String {
            if (date == null) {
                return "No due date"
            }
            
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val today = Date()
            val tomorrow = Date(today.time + 86400000) // Today + 24 hours in milliseconds
            
            return when {
                isSameDay(date, today) -> "Due: Today"
                isSameDay(date, tomorrow) -> "Due: Tomorrow"
                else -> "Due: ${dateFormat.format(date)}"
            }
        }
        
        private fun isSameDay(date1: Date, date2: Date): Boolean {
            val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            return fmt.format(date1) == fmt.format(date2)
        }
    }
    
    fun updateTasks(tasks: List<Task>) {
        submitList(tasks)
    }
    
    private class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
} 