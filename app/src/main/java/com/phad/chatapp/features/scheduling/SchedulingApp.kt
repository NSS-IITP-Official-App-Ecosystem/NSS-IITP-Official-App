package com.phad.chatapp.features.scheduling

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phad.chatapp.features.scheduling.ui.theme.SchedulingTheme
import com.phad.chatapp.features.scheduling.schedule.ScheduleMakerScreen
import com.phad.chatapp.features.scheduling.schedule.AvailabilityOptionsScreen
import com.phad.chatapp.features.scheduling.schedule.VolunteerPresetsScreen
import com.phad.chatapp.features.scheduling.schedule.EditTeachingSlotsScreen
import com.phad.chatapp.features.scheduling.schedule.GeneratedSchedulesScreen
import com.phad.chatapp.features.scheduling.schedule.SetAvailabilityScreen
import com.phad.chatapp.features.scheduling.schedule.CreateTeachingSlotsScreen
import com.phad.chatapp.features.scheduling.schedule.ManageVolunteersScreen
import com.phad.chatapp.features.scheduling.schedule.SelectTeachingSlotsForScheduleScreen
import com.phad.chatapp.features.scheduling.schedule.TeachingSlotsOptionsScreen
import com.phad.chatapp.features.scheduling.schedule.TeachingSlotsScreen
import com.phad.chatapp.features.scheduling.schedule.ScheduleGenerationScreen
import com.phad.chatapp.features.scheduling.schedule.ScheduleCreationScreen
import com.phad.chatapp.features.scheduling.schedule.ViewAssignmentsScreen

/**
 * Main composable for the Scheduling feature.
 * This will be used inside the SchedulingFragment.
 */
@Composable
fun SchedulingApp(
    startDestination: String? = null,
    onBackClick: () -> Unit = {}
) {
    SchedulingTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(
                startDestination = startDestination,
                onBackClick = onBackClick
            )
        }
    }
}

@Composable
fun AppNavigation(
    startDestination: String? = null,
    onBackClick: () -> Unit = {}
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController, 
        startDestination = startDestination ?: "scheduleMaker"
    ) {
        composable("scheduleMaker") {
            ScheduleMakerScreen(navController = navController)
        }
        composable("createTeachingSlots") { 
            CreateTeachingSlotsScreen(navController = navController)
        }
        composable("createTeachingSlots/{presetId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("presetId") ?: ""
            CreateTeachingSlotsScreen(navController = navController, presetId = id)
        }
        composable("editTeachingSlots") { 
            EditTeachingSlotsScreen(navController = navController)
        }
        composable("availabilityOptions") {
            AvailabilityOptionsScreen(navController = navController)
        }

        composable("viewAssignments") {
            ViewAssignmentsScreen(navController = navController)
        }

        composable("volunteerPresets") {
            VolunteerPresetsScreen(navController = navController)
        }
        composable("manageVolunteers/{presetId}") { backStackEntry ->
            val presetId = backStackEntry.arguments?.getString("presetId") ?: ""
            ManageVolunteersScreen(navController = navController, presetId = presetId)
        }
        composable("manageStudents") {
            com.phad.chatapp.features.scheduling.schedule.AdminStudentListScreen(
                navController = navController,
                onBackClick = onBackClick
            )
        }
        composable("manageSubjects") {
            com.phad.chatapp.features.scheduling.schedule.ManageSubjectsScreen(
                navController = navController,
                onBackClick = onBackClick
            )
        }
        composable("setAvailability") {
            SetAvailabilityScreen(navController = navController)
        }
        composable("setAvailability/{presetId}") { backStackEntry ->
            val presetId = backStackEntry.arguments?.getString("presetId") ?: "new"
            SetAvailabilityScreen(navController = navController, presetId = presetId)
        }
        composable("teachingSlotsOptionsScreen") {
            TeachingSlotsOptionsScreen(navController = navController)
        }
        composable("teachingSlotsOptionsScreen?destination={destination}") { backStackEntry ->
            val destination = backStackEntry.arguments?.getString("destination")
            TeachingSlotsOptionsScreen(navController = navController, destination = destination)
        }
        composable("generatedSchedules") {
            GeneratedSchedulesScreen(navController = navController)
        }
        composable("selectTeachingSlotsForSchedule") {
            SelectTeachingSlotsForScheduleScreen(navController = navController)
        }
        composable("teachingSlots") {
            TeachingSlotsScreen(navController = navController)
        }
        
        // New screens for schedule generation
        composable("scheduleGeneration") {
            ScheduleGenerationScreen(navController = navController)
        }
        composable("scheduleCreation/{vpId}/{vaIds}") { backStackEntry ->
            val vpId = backStackEntry.arguments?.getString("vpId") ?: ""
            val vaIds = backStackEntry.arguments?.getString("vaIds") ?: ""
            ScheduleCreationScreen(
                navController = navController,
                vpId = vpId,
                vaIds = vaIds
            )
        }


    }
} 