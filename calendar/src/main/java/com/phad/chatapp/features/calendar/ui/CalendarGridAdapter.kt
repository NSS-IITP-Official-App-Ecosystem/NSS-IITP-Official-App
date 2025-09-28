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
import com.phad.chatapp.features.calendar.R
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.LeaveApplication
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import android.util.Log

class CalendarGridAdapter(
    private val context: Context,
    private val events: List<CalendarEvent> = emptyList(),
    private val leaveApplications: List<LeaveApplication> = emptyList(),
    private val isTeachingCalendar: Boolean = false // Flag to determine if this is the teaching calendar
) : BaseAdapter() {
    
    private val calendar: Calendar = Calendar.getInstance()
    private val today: Calendar = Calendar.getInstance()
    private val dates: MutableList<Date> = ArrayList()
    private var selectedPosition = -1
    
    // Date range selection support
    private var isRangeModeEnabled = false
    private var selectedDateRange: List<Date> = emptyList()
    private var rangeStartDate: Date? = null
    private var rangeEndDate: Date? = null
    
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
    
    // Set range selection mode
    fun setRangeSelectionMode(enabled: Boolean) {
        isRangeModeEnabled = enabled
        if (!enabled) {
            selectedDateRange = emptyList()
            rangeStartDate = null
            rangeEndDate = null
        }
        notifyDataSetChanged()
    }
    
    // Update the selected date range
    fun setSelectedDateRange(range: List<Date>, start: Date?, end: Date?) {
        selectedDateRange = range
        rangeStartDate = start
        rangeEndDate = end
        notifyDataSetChanged()
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
        
        // Set cell style based on the date
        // Default style - consistent white background with dark text
        dayCell.setCardBackgroundColor(Color.WHITE)
        dateText.setTextColor(Color.BLACK)
        
        // Style for dates not in current month
        if (dateCalendar.get(Calendar.MONTH) != calendar.get(Calendar.MONTH)) {
            dayCell.alpha = 0.3f
            // Disable clicking on dates outside current month
            dayCell.isClickable = false
        } else {
            dayCell.alpha = 1.0f
            dayCell.isClickable = true
            
            // Check for leave applications on this date
            val hasApprovedLeave = leaveApplications.any { leave -> 
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
            
            // Special handling for teaching calendar
            if (isTeachingCalendar) {
                // Check if there are approved leaves that have been accepted
                val approvedLeavesForDate = leaveApplications.filter { leave -> 
                    leave.status == EventStatus.APPROVED && 
                    isSameDay(dateCalendar, Calendar.getInstance().apply { time = leave.date })
                }
                
                // Check if all approved leaves have associated accepted classes
                val allLeavesAccepted = if (approvedLeavesForDate.isNotEmpty()) {
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
                
                if (hasApprovedLeave && !allLeavesAccepted) {
                    // Priority 1: Highlight dates with approved leaves that haven't been accepted yet
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.leave_approved))
                    dateText.setTextColor(Color.WHITE)
                } else if (hasPendingLeave) {
                    // Priority 2: Highlight dates with pending leaves
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.leave_pending))
                    dateText.setTextColor(Color.BLACK)
                } else if (hasRejectedLeave) {
                    // Priority 3: Highlight dates with rejected leaves
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.leave_rejected))
                    dateText.setTextColor(Color.WHITE)
                } else if (isSameDay(dateCalendar, today)) {
                    // Priority 4: Style for today - consistent appearance with blue text
                    dayCell.setCardBackgroundColor(Color.WHITE)
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.teaching_day)) // Blue color
                } else {
                    // Style for days with events in teaching calendar
                    val hasEvent = events.any { event -> 
                        isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
                    }
                    
                    if (hasEvent) {
                        // If all leaves are accepted or there are no leaves, use normal teaching day color
                        dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.teaching_day))
                        dateText.setTextColor(Color.WHITE)
                    }
                }
            } else {
                // Non-teaching calendar (general events)
                if (isSameDay(dateCalendar, today)) {
                    // Priority 1: Style for today - consistent appearance with blue text
                    dayCell.setCardBackgroundColor(Color.WHITE)
                    dateText.setTextColor(ContextCompat.getColor(context, R.color.teaching_day)) // Blue color
                } else {
                    // Style for days with events
                    val hasEvent = events.any { event -> 
                        isSameDay(dateCalendar, Calendar.getInstance().apply { time = event.date })
                    }
                    
                    if (hasEvent) {
                        dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.event_day))
                        dateText.setTextColor(Color.WHITE)
                    }
                }
            }
            
            // Style for range selection mode (don't override leave colors in teaching calendar)
            if ((!isTeachingCalendar || (!hasApprovedLeave && !hasPendingLeave && !hasRejectedLeave)) && 
                isRangeModeEnabled && selectedDateRange.isNotEmpty()) {
                // Check if date is in the selected range
                val isInRange = selectedDateRange.any { rangeDate ->
                    isSameDay(dateCalendar, Calendar.getInstance().apply { time = rangeDate })
                }
                
                if (isInRange) {
                    // Style for dates in range
                    dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.range_selection))
                    
                    // Special styling for range boundaries
                    if (rangeStartDate != null && isSameDay(dateCalendar, Calendar.getInstance().apply { time = rangeStartDate!! })) {
                        dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.range_start))
                    } else if (rangeEndDate != null && isSameDay(dateCalendar, Calendar.getInstance().apply { time = rangeEndDate!! })) {
                        dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.range_end))
                    }
                }
            } else if ((!isTeachingCalendar || (!hasApprovedLeave && !hasPendingLeave && !hasRejectedLeave)) && 
                position == selectedPosition) {
                // Style for selected date (single selection mode)
                dayCell.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_date))
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

        // Make empty days visually non-clickable
        if (!hasEvent && !hasLeaveApplication && dateCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) {
            dayCell.isClickable = false
            dayCell.isFocusable = false
            dayCell.foreground = null // Remove ripple effect
        } else if (dateCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) {
            dayCell.isClickable = true
            dayCell.isFocusable = true
            // Restore ripple effect for clickable days
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

                // Only allow clicks on days that have events or leave applications
                if (hasEventForClick || hasLeaveForClick) {
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

                    // Log EXACT date being clicked
                    Log.d("CalendarGridAdapter", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("CalendarGridAdapter", "EXACT DATE CLICKED:")
                    Log.d("CalendarGridAdapter", "Day: $clickedDay, Month: ${clickedMonth + 1}, Year: $clickedYear")
                    Log.d("CalendarGridAdapter", "Formatted date: ${dateFormat.format(exactDate)}")
                    Log.d("CalendarGridAdapter", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    // Update selected position
                    val oldPosition = selectedPosition
                    selectedPosition = position

                    // Force redraw of old and new positions
                    notifyDataSetChanged()

                    // Call the listener with the EXACT date
                    onDateSelectedListener?.invoke(exactDate)
                }
                // Empty days are now non-interactive - no action taken
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