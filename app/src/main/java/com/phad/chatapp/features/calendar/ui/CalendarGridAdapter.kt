package com.phad.chatapp.features.calendar.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.phad.chatapp.R
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.LeaveApplication
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import android.util.Log
import java.util.Locale
import com.phad.chatapp.features.scheduling.models.SubjectAssignmentDetails

class CalendarGridAdapter(
    private val context: Context,
    private val events: List<CalendarEvent> = emptyList(),
    private val leaveApplications: List<LeaveApplication> = emptyList(),
    private val teachingAssignments: List<SubjectAssignmentDetails> = emptyList(),
    private val isTeachingCalendar: Boolean = false, // Flag to determine if this is the teaching calendar
    private val userRollNumber: String = "" // Roll number to highlight accepted substitutions
) : BaseAdapter() {
    
    private val calendar: Calendar = Calendar.getInstance()
    private val today: Calendar = Calendar.getInstance()
    private val dates: MutableList<Date> = ArrayList()
    private var selectedPosition = -1
    
    var onDateSelectedListener: ((Date) -> Unit)? = null
    
    private val dateFormat = SimpleDateFormat("MMM d, yyyy")
    
    init {
        // Initialize with current month
        initCalendarDates()
    }
    
    fun setMonth(year: Int, month: Int) {
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        initCalendarDates()
        notifyDataSetChanged()
    }
    
    fun getCurrentMonth(): Int {
        return calendar.get(Calendar.MONTH)
    }
    
    fun getCurrentYear(): Int {
        return calendar.get(Calendar.YEAR)
    }
    
    fun setSelectedDate(date: Date) {
        val cal = Calendar.getInstance().apply { time = date }
        for (i in dates.indices) {
            val dateCal = Calendar.getInstance().apply { time = dates[i] }
            if (cal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == dateCal.get(Calendar.MONTH) &&
                cal.get(Calendar.DAY_OF_MONTH) == dateCal.get(Calendar.DAY_OF_MONTH)) {
                selectedPosition = i
                notifyDataSetChanged()
                break
            }
        }
    }
    
    private fun initCalendarDates() {
        dates.clear()
        
        // Clone the calendar to avoid modifying the original
        val tempCalendar = calendar.clone() as Calendar
        
        // Move to first day of month
        tempCalendar.set(Calendar.DAY_OF_MONTH, 1)
        
        // Fill in days from previous month if needed
        val firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK)
        if (firstDayOfWeek > Calendar.SUNDAY) {
            tempCalendar.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - Calendar.SUNDAY))
        }
        
        // Add 42 days (6 weeks) to cover the full calendar grid
        for (i in 0 until 42) {
            dates.add(tempCalendar.time)
            tempCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    fun getMonthAndYear(): String {
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", 
                                "July", "August", "September", "October", "November", "December")
        return "${monthNames[calendar.get(Calendar.MONTH)]} ${calendar.get(Calendar.YEAR)}"
    }
    
    override fun getCount(): Int = dates.size
    
    override fun getItem(position: Int): Date = dates[position]
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.calendar_day_cell, parent, false)
        
        val dayCell = view.findViewById<CardView>(R.id.cardDayCell)
        val dateText = view.findViewById<TextView>(R.id.tvDayNumber)
        
        val date = getItem(position)
        val dateCalendar = Calendar.getInstance().apply { time = date }
        
        // Set date number
        dateText.text = dateCalendar.get(Calendar.DAY_OF_MONTH).toString()
        
        var hasTeachingAssignment = false
        
        // Set cell style based on the date
        // Style for dates not in current month
        if (dateCalendar.get(Calendar.MONTH) != calendar.get(Calendar.MONTH)) {
            dayCell.setCardBackgroundColor(Color.TRANSPARENT)
            dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_disabled))
            dayCell.alpha = 0.2f
            // Disable clicking on dates outside current month
            dayCell.isClickable = false
        } else {
            // Default style for in-month dates - surface gray, white text
            dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_surface))
            dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_primary))
            
            dayCell.alpha = 1.0f
            dayCell.isClickable = true
            
            // Check for leave applications on this date
            val hasAvailableLeave = leaveApplications.any { leave -> 
                leave.status == EventStatus.APPROVED && 
                isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
            }
            
            val hasPendingLeave = leaveApplications.any { leave -> 
                leave.status == EventStatus.PENDING && 
                isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
            }
            
            val hasRejectedLeave = leaveApplications.any { leave -> 
                leave.status == EventStatus.REJECTED && 
                isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
            }
            
            // Check for teaching assignments (matching by day of week)
            hasTeachingAssignment = if (isTeachingCalendar) {
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val dayName = dayFormat.format(dateCalendar.time).lowercase()
                teachingAssignments.any { assignment: SubjectAssignmentDetails ->
                    val dbDay = assignment.dayName.trim().lowercase()
                    dbDay == dayName || dbDay.startsWith(dayName) || dayName.startsWith(dbDay)
                }
            } else false
            
            // Special handling for teaching calendar
            if (isTeachingCalendar) {
                // Check if there are available leaves that have been accepted
                val availableLeavesForDate = leaveApplications.filter { leave -> 
                    leave.status == EventStatus.APPROVED && 
                    isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
                }
                
                // Check if all available leaves have associated accepted classes
                val allLeavesAccepted = if (availableLeavesForDate.isNotEmpty()) {
                    val acceptedEventsForDate = events.filter { event ->
                        event.acceptedByRollNumber.isNotEmpty() &&
                        event.status == EventStatus.ACCEPTED &&
                        isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
                    }
                    
                    // If we have at least one accepted class for this date with an accepted roll number
                    acceptedEventsForDate.isNotEmpty()
                } else {
                    false
                }
                
                // Color the cell green if the current user has an accepted substitution on this day
                val userIsSubstituting = leaveApplications.any { leave -> 
                    leave.status == EventStatus.ACCEPTED &&
                    leave.substitutedByRollNumber.isNotEmpty() &&
                    leave.substitutedByRollNumber == userRollNumber &&
                    isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
                }
                
                if (userIsSubstituting) {
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_success))
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_primary))
                } else if (hasAvailableLeave && !allLeavesAccepted) {
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_error))
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_primary))
                } else if (hasPendingLeave) {
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_warning))
                    dateText.setTextColor(Color.BLACK)
                } else if (hasRejectedLeave) {
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_text_disabled))
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_primary))
                } else if (isSameDay(dateCalendar, today)) {
                    // Today: elevated surface with yellow accent text
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_surface_elevated))
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_accent))
                } else {
                    val hasEvent = events.any { event ->
                        isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
                    }

                    if (hasEvent || hasTeachingAssignment) {
                        // Teaching day: amber/gold background
                        dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_accent_dim))
                        dateText.setTextColor(Color.BLACK)
                    }
                }
            } else {
                // Non-teaching calendar (general events)
                if (isSameDay(dateCalendar, today)) {
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_surface_elevated))
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_accent))
                } else {
                    val hasEvent = events.any { event ->
                        isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
                    }

                    if (hasEvent) {
                        dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_info))
                        dateText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_primary))
                    }
                }
            }
            
            // Style for selected date
            if (position == selectedPosition) {
                dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cal_accent))
                dateText.setTextColor(Color.BLACK)
            }
        }

        // Check if this date has any events or leave applications to determine clickability
        val hasEvent = events.any { event ->
            isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
        }

        val hasLeaveApplication = leaveApplications.any { leave ->
            isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
        }

        // All dates in the current month are always clickable
        if (dateCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) {
            dayCell.isClickable = true
            dayCell.isFocusable = true
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            dayCell.foreground = ContextCompat.getDrawable(context, typedValue.resourceId)
        }

        // Set click listener with improved handling
        view.setOnClickListener {
            // Only process clicks for current month
            if (dateCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) {
                // Check if this date has any events or leave applications
                val hasEventForClick = events.any { event ->
                    isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
                }

                val hasLeaveForClick = leaveApplications.any { leave ->
                    isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
                }
                
                // Check if this date has any teaching assignments (periodic)
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val dayNameFormatted = dayFormat.format(dateCalendar.time).lowercase()
                val hasAssignmentForClick = isTeachingCalendar && teachingAssignments.any { assignment: SubjectAssignmentDetails ->
                    val dbDay = assignment.dayName.trim().lowercase()
                    dbDay == dayNameFormatted || dbDay.startsWith(dayNameFormatted) || dayNameFormatted.startsWith(dbDay)
                }

                // Get the exact calendar date for the clicked cell
                val clickedDay = dateCalendar.get(Calendar.DAY_OF_MONTH)
                val clickedMonth = dateCalendar.get(Calendar.MONTH)
                val clickedYear = dateCalendar.get(Calendar.YEAR)

                // IMPORTANT: Create a fresh Calendar object to ensure we have the correct date
                val exactDate = Calendar.getInstance().apply {
                    clear() // Clear all fields to start fresh
                    set(clickedYear, clickedMonth, clickedDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                Log.d("CalendarGridAdapter", "Date clicked: ${dateFormat.format(exactDate)}")

                // Update selected position
                selectedPosition = position
                notifyDataSetChanged()

                // Always call the listener with the EXACT date
                onDateSelectedListener?.invoke(exactDate)
            }
        }
        
        return view
    }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
} 
