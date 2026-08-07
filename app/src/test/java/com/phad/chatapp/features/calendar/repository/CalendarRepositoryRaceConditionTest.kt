package com.phad.chatapp.features.calendar.repository

import com.google.firebase.firestore.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryRaceConditionTest {

    @Mock private lateinit var mockFirestore: FirebaseFirestore
    @Mock private lateinit var mockCollection: CollectionReference
    @Mock private lateinit var mockDocRef: DocumentReference

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Ignore("Disabled for UI testing phase")
    @Test
    fun `test acceptClass - Double Booking Race Condition`() = runTest {
        // Setup mock environment
        `when`(mockFirestore.collection(anyString())).thenReturn(mockCollection)
        `when`(mockCollection.document(anyString())).thenReturn(mockDocRef)
        
        // Assume repository initialization (with injected mock if refactored, else relies on static mock)
        val repository = CalendarRepository()

        // Simulate network delay on blind update()
        `when`(mockDocRef.update(anyMap<String, Any>())).thenAnswer {
            Thread.sleep(50) 
            null
        }

        // Volunteer A and Volunteer B concurrently accept the class
        val volunteerA = async { repository.acceptClass("event123", "ROLL001", "Student A") }
        val volunteerB = async { repository.acceptClass("event123", "ROLL002", "Student B") }
        
        val results = awaitAll(volunteerA, volunteerB)

        // ASSERTION: Because the code uses a blind .update() without runTransaction,
        // both threads execute concurrently and both return true. Volunteer B silently overwrites A.
        // A secure implementation would result in one returning false/exception.
        assertTrue("VULNERABILITY: Both volunteers successfully booked the same class", results[0] && results[1])
        verify(mockDocRef, times(2)).update(anyMap<String, Any>())
    }
}
