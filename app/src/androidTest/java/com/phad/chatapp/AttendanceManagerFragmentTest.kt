package com.phad.chatapp

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phad.chatapp.fragments.AttendanceManagerFragment
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttendanceManagerFragmentTest {

    @Test
    fun testAttendanceManagerFragmentLayout() {
        // Launch the fragment
        launchFragmentInContainer<AttendanceManagerFragment>(themeResId = R.style.Theme_ChatApp)

        // Check that the main UI components are displayed
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        onView(withId(R.id.titleTextView)).check(matches(withText("Mark Your Attendance")))
        onView(withId(R.id.rollNumberInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.cameraButton)).check(matches(isDisplayed()))
        onView(withId(R.id.imageView)).check(matches(isDisplayed()))
    }

    @Test
    fun testRollNumberInputValidation() {
        // Launch the fragment
        launchFragmentInContainer<AttendanceManagerFragment>(themeResId = R.style.Theme_ChatApp)

        // Test roll number input validation
        onView(withId(R.id.rollNumberInput)).perform(androidx.test.espresso.action.ViewActions.typeText(""))
        onView(withId(R.id.markAttendanceButton)).perform(androidx.test.espresso.action.ViewActions.click())

        // Error should be displayed
        onView(withId(R.id.rollNumberInputLayout)).check(matches(hasTextInputLayoutErrorText("Roll number is required")))
    }

    /**
     * Custom matcher for TextInputLayout error text
     */
    private fun hasTextInputLayoutErrorText(expectedErrorText: String) = object : org.hamcrest.TypeSafeMatcher<View>() {
        override fun describeTo(description: org.hamcrest.Description) {
            description.appendText("with error: $expectedErrorText")
        }

        override fun matchesSafely(view: View): Boolean {
            if (view !is com.google.android.material.textfield.TextInputLayout) {
                return false
            }

            val error = view.error ?: return false
            val errorText = error.toString()
            return expectedErrorText == errorText
        }
    }
} 