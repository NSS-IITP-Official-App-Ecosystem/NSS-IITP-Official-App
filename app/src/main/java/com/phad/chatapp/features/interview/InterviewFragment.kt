package com.phad.chatapp.features.interview

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InterviewFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var currentView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.parseColor("#181818"))
        layout.setPadding(48, 120, 48, 48)
        layout.gravity = Gravity.CENTER_HORIZONTAL

        val title = TextView(context)
        title.text = "Interview Portal"
        title.setTextColor(Color.WHITE)
        title.textSize = 24f
        title.setPadding(0, 0, 0, 32)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        layout.addView(title)

        

        val subtitle = TextView(context)
        subtitle.text = "Enter Candidate Details"
        subtitle.setTextColor(Color.WHITE)
        subtitle.textSize = 16f
        subtitle.setPadding(0, 0, 0, 24)
        layout.addView(subtitle)

        // Roll Number input
        val rollNumberLabel = TextView(context)
        rollNumberLabel.text = "Roll Number"
        rollNumberLabel.setTextColor(Color.WHITE)
        rollNumberLabel.textSize = 14f
        rollNumberLabel.setPadding(0, 0, 0, 8)
        layout.addView(rollNumberLabel)

        val editTextRollNumber = EditText(context)
        editTextRollNumber.hint = "Enter Roll Number"
        editTextRollNumber.setHintTextColor(Color.parseColor("#AAAAAA"))
        editTextRollNumber.setTextColor(Color.WHITE)
        editTextRollNumber.textSize = 18f
        editTextRollNumber.setBackgroundColor(Color.parseColor("#232323"))
        editTextRollNumber.setPadding(32, 24, 32, 24)
        val editRollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        editRollParams.setMargins(0, 0, 0, 24)
        editTextRollNumber.layoutParams = editRollParams
        layout.addView(editTextRollNumber)

        // Bench Number input
        val benchNumberLabel = TextView(context)
        benchNumberLabel.text = "Bench Number"
        benchNumberLabel.setTextColor(Color.WHITE)
        benchNumberLabel.textSize = 14f
        benchNumberLabel.setPadding(0, 0, 0, 8)
        layout.addView(benchNumberLabel)

        val editTextBenchNumber = EditText(context)
        editTextBenchNumber.hint = "Enter Bench Number (1-7)"
        editTextBenchNumber.setHintTextColor(Color.parseColor("#AAAAAA"))
        editTextBenchNumber.setTextColor(Color.WHITE)
        editTextBenchNumber.textSize = 18f
        editTextBenchNumber.setBackgroundColor(Color.parseColor("#232323"))
        editTextBenchNumber.setPadding(32, 24, 32, 24)
        editTextBenchNumber.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val editBenchParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        editBenchParams.setMargins(0, 0, 0, 32)
        editTextBenchNumber.layoutParams = editBenchParams
        layout.addView(editTextBenchNumber)

        val button = Button(context)
        button.text = "Submit"
        button.setTextColor(Color.BLACK)
        button.textSize = 16f
        button.setBackgroundColor(Color.parseColor("#006BFF")) // Blue accent
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.setMargins(0, 0, 0, 0)
        button.layoutParams = btnParams
        layout.addView(button)

        // Add Set Biases Button
        val setBiasesButton = Button(context)
        setBiasesButton.text = "Set Evaluation Biases"
        setBiasesButton.setTextColor(Color.BLACK)
        setBiasesButton.textSize = 16f
        setBiasesButton.setBackgroundColor(Color.parseColor("#006BFF"))
        val biasesBtnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        biasesBtnParams.setMargins(0, 64, 0, 32)
        setBiasesButton.layoutParams = biasesBtnParams
        layout.addView(setBiasesButton)

        // Add Normalise Data Button
        val normaliseButton = Button(context)
        normaliseButton.text = "Normalise Data"
        normaliseButton.setTextColor(Color.BLACK)
        normaliseButton.textSize = 16f
        normaliseButton.setBackgroundColor(Color.parseColor("#006BFF"))
        val normaliseBtnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        normaliseBtnParams.setMargins(0, 32, 0, 32)
        normaliseButton.layoutParams = normaliseBtnParams
        layout.addView(normaliseButton)

        setBiasesButton.setOnClickListener {
            showBiasesDialog()
        }

        normaliseButton.setOnClickListener {
            // Show confirmation dialog first
            android.app.AlertDialog.Builder(context)
                .setTitle("Normalise Data")
                .setMessage("You must have finished the evaluation of all students to normalise data. Are you sure you want to proceed?")
                .setPositiveButton("Yes, Proceed") { dialog, _ ->
                    // Dismiss confirmation dialog
                    dialog.dismiss()

                    // Start normalization process here
                    lifecycleScope.launch(Dispatchers.Main) {
                        // Declare loadingDialog outside the try block
                        var loadingDialog: android.app.AlertDialog? = null

                        try {
                            // Show loading dialog
                            loadingDialog = android.app.AlertDialog.Builder(context)
                                .setTitle("Normalising Data")
                                .setMessage("Please wait while we normalize the scores...")
                                .setCancelable(false)
                                .create()
                            loadingDialog.show()

                            // Get all students with unnormalized scores
                            val studentsSnapshot = db.collection("Student")
                                .whereNotEqualTo("Itw_score_unormalised", null)
                                .get()
                                .await()

                            // Group students by bench number
                            val benchGroups = mutableMapOf<String, MutableList<Pair<String, Double>>>()

                            studentsSnapshot.documents.forEach { doc ->
                                val studentId = doc.id
                                val data = doc.data

                                Log.d("InterviewFragment", "Processing document ID: $studentId")
                                Log.d("InterviewFragment", "Document data: $data")

                                // Check and log the type of 'Interview bench'
                                val benchField = data?.get("Interview bench")
                                val benchNo = when (benchField) {
                                    is String -> {
                                        Log.d("InterviewFragment", "Interview bench is String: $benchField")
                                        benchField
                                    }
                                    is Long -> {
                                        val value = benchField.toString()
                                        Log.d("InterviewFragment", "Interview bench is Long: $value")
                                        value
                                    }
                                    is Double -> {
                                        val value = benchField.toInt().toString() // Convert double to Int then String
                                        Log.d("InterviewFragment", "Interview bench is Double: $value")
                                        value
                                    }
                                    else -> {
                                        Log.w("InterviewFragment", "Interview bench has unexpected type: ${benchField?.javaClass?.name}")
                                        null // Handle other types or null
                                    }
                                }

                                // Check and log the type of 'Itw_score_unormalised'
                                val scoreField = data?.get("Itw_score_unormalised")
                                val unnormalizedScore = when (scoreField) {
                                    is Number -> {
                                        Log.d("InterviewFragment", "Itw_score_unormalised is Number: $scoreField")
                                        scoreField.toDouble()
                                    }
                                    else -> {
                                        Log.w("InterviewFragment", "Itw_score_unormalised has unexpected type or is null: ${scoreField?.javaClass?.name}")
                                        null // Handle other types or null
                                    }
                                }

                                if (benchNo != null && unnormalizedScore != null) {
                                    benchGroups.getOrPut(benchNo) { mutableListOf() }.add(studentId to unnormalizedScore)
                                } else {
                                     Log.w("InterviewFragment", "Skipping document $studentId due to missing/invalid bench ($benchNo) or score ($unnormalizedScore).")
                                }
                            }

                            // Calculate min-max for each bench and normalize scores
                            val updates = mutableListOf<Pair<String, Double>?>()

                            benchGroups.forEach { (benchNo, scores) ->
                                if (scores.size > 1) { // Only normalize if we have more than one score
                                    val minScore = scores.minOf { it.second }
                                    val maxScore = scores.maxOf { it.second }
                                    val range = maxScore - minScore

                                    if (range > 0) { // Avoid division by zero
                                        scores.forEach { (studentId, score) ->
                                            // Normalize using min-max scaling: (x - min) / (max - min)
                                            val normalizedScore = (score - minScore) / range
                                            updates.add(studentId to normalizedScore)
                                        }
                                    } else {
                                        // If all scores are the same, set normalized score to 0.5
                                        scores.forEach { (studentId, _) ->
                                            updates.add(studentId to 0.5)
                                        }
                                    }
                                } else if (scores.size == 1) {
                                    // If only one score exists for a bench, set normalized score to 0.5
                                    updates.add(scores[0].first to 0.5)
                                } else {
                                     Log.w("InterviewFragment", "Bench $benchNo has no students with valid data. Skipping normalization for this bench.")
                                }
                            }

                            // Batch update all documents
                            val batch = db.batch()
                            updates.filterNotNull().forEach { (studentId, normalizedScore) ->
                                val docRef = db.collection("Student").document(studentId)
                                batch.update(docRef, "Interview_score", normalizedScore)
                            }
                            batch.commit().await()

                            loadingDialog.dismiss()

                            // Show success dialog
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Normalization Complete")
                                .setMessage("Scores have been normalized successfully for all benches.")
                                .setPositiveButton("OK") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()

                        } catch (e: Exception) {
                            Log.e("InterviewFragment", "Error normalizing scores: ${e.message}", e)
                            // Dismiss loading dialog if it was successfully created and is showing
                             loadingDialog?.dismiss()
                            Toast.makeText(context, "Failed to normalize scores. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    // Dismiss confirmation dialog
                    dialog.dismiss()
                }
                .create()
                .show()
        }

        button.setOnClickListener {
            val rollNo = editTextRollNumber.text.toString().trim()
            val benchNo = editTextBenchNumber.text.toString().trim()

            Log.d("InterviewFragment", "Button clicked - Roll No: '$rollNo', Bench No: '$benchNo'")

            if (rollNo.isEmpty()) {
                Toast.makeText(context, "Please enter a roll number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (benchNo.isEmpty()) {
                Toast.makeText(context, "Please enter a bench number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate bench number is between 1-7
            val benchNumber = benchNo.toIntOrNull()
            if (benchNumber == null || benchNumber < 1 || benchNumber > 7) {
                Toast.makeText(context, "Bench number must be between 1 and 7", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Call Firestore to check if roll number and bench number exist
            lifecycleScope.launch(Dispatchers.Main) {
                button.isEnabled = false
                Log.d("InterviewFragment", "Starting validation process")

                val studentExists = checkStudentExists(rollNo)
                Log.d("InterviewFragment", "Student exists check completed: $studentExists")

                val benchExists = checkBenchNumberExists(benchNo)
                Log.d("InterviewFragment", "Bench exists check completed: $benchExists")

                when {
                    !studentExists -> {
                        Log.d("InterviewFragment", "Invalid roll number case")
                        Toast.makeText(context, "Invalid roll number", Toast.LENGTH_SHORT).show()
                        button.isEnabled = true
                    }
                    !benchExists -> {
                        Log.d("InterviewFragment", "Invalid bench number case")
                        Toast.makeText(context, "Invalid bench number", Toast.LENGTH_SHORT).show()
                        button.isEnabled = true
                    }
                    else -> {
                        Log.d("InterviewFragment", "Both validations passed, updating bench number")
                        val updateSuccess = updateStudentBenchNumber(rollNo, benchNo)
                        button.isEnabled = true

                        if (updateSuccess) {
                            Log.d("InterviewFragment", "Update successful, showing next page")
                            showNextPage(layout, rollNo, benchNo)
                        } else {
                            Log.d("InterviewFragment", "Update failed")
                            Toast.makeText(context, "Failed to update bench number. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        currentView = layout
        return layout
    }

    private suspend fun checkStudentExists(rollNo: String): Boolean {
        return try {
            Log.d("InterviewFragment", "=== Starting Firebase Check ===")
            Log.d("InterviewFragment", "Checking document with ID: $rollNo")

            val doc = db.collection("Student").document(rollNo).get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.e("InterviewFragment", "Error in checkStudentExists: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun updateStudentBenchNumber(rollNo: String, benchNo: String): Boolean {
        return try {
            Log.d("InterviewFragment", "=== Starting Bench Number Update ===")

            val docRef = db.collection("Student").document(rollNo)
            val doc = docRef.get().await()

            if (!doc.exists()) {
                Log.e("InterviewFragment", "Could not find document for roll number: $rollNo")
                return false
            }

            Log.d("InterviewFragment", "Found document to update: $rollNo")
            Log.d("InterviewFragment", "Current data: ${doc.data}")

            docRef.update("Interview bench", benchNo).await()
            Log.d("InterviewFragment", "Successfully updated bench number")
            true

        } catch (e: Exception) {
            Log.e("InterviewFragment", "Error in updateStudentBenchNumber: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun checkBenchNumberExists(benchNo: String): Boolean {
        return try {
            val doc = db.collection("bench").document(benchNo).get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.e("InterviewFragment", "Error checking bench number: $e")
            false
        }
    }

    private fun showBiasesDialog() {
        val context = requireContext()
        val dialogLayout = LinearLayout(context)
        dialogLayout.orientation = LinearLayout.VERTICAL
        dialogLayout.setBackgroundColor(Color.parseColor("#181818"))
        dialogLayout.setPadding(48, 48, 48, 48)

        val title = TextView(context)
        title.text = "Set Evaluation Biases"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.setPadding(0, 0, 0, 32)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        dialogLayout.addView(title)

        // Map of display names to field names
        val criteriaMap = mapOf(
            "Fluency in Hindi" to "Fluency_in_hindi",
            "Content Delivery" to "Content_delivery",
            "Overall Body Language" to "Overall_body_language",
            "Resourcefulness" to "Resourcefullness",
            "General Questions" to "General_questions"
        )

        val biasInputs = mutableMapOf<String, EditText>()

        criteriaMap.forEach { (displayName, fieldName) ->
            // Criterion Label
            val label = TextView(context)
            label.text = displayName
            label.setTextColor(Color.WHITE)
            label.textSize = 16f
            label.setPadding(0, 0, 0, 8)
            dialogLayout.addView(label)

            // Bias Input
            val biasInput = EditText(context)
            biasInput.hint = "Enter bias (0-10)"
            biasInput.setHintTextColor(Color.parseColor("#AAAAAA"))
            biasInput.setTextColor(Color.WHITE)
            biasInput.textSize = 18f
            biasInput.setBackgroundColor(Color.parseColor("#232323"))
            biasInput.setPadding(32, 24, 32, 24)
            biasInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            biasInput.filters = arrayOf(android.text.InputFilter.LengthFilter(2))
            
            // Load existing bias if any
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    val doc = db.collection("biases").document("Bias_normal").get().await()
                    if (doc.exists()) {
                        val bias = doc.getLong(fieldName)?.toString() ?: "0"
                        biasInput.setText(bias)
                    }
                } catch (e: Exception) {
                    Log.e("InterviewFragment", "Error loading bias: ${e.message}")
                }
            }

            val inputParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputParams.setMargins(0, 0, 0, 32)
            biasInput.layoutParams = inputParams
            dialogLayout.addView(biasInput)
            biasInputs[fieldName] = biasInput
        }

        // Save Button
        val saveButton = Button(context)
        saveButton.text = "Save Biases"
        saveButton.setTextColor(Color.BLACK)
        saveButton.textSize = 16f
        saveButton.setBackgroundColor(Color.parseColor("#006BFF"))
        val saveBtnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        saveBtnParams.setMargins(0, 32, 0, 0)
        saveButton.layoutParams = saveBtnParams
        dialogLayout.addView(saveButton)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        saveButton.setOnClickListener {
            var hasError = false
            biasInputs.forEach { (fieldName, input) ->
                val bias = input.text.toString().toIntOrNull() ?: -1
                if (bias !in 0..10) {
                    input.error = "Bias must be between 0 and 10"
                    hasError = true
                }
            }

            if (!hasError) {
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        val biasData = biasInputs.mapValues { (_, input) ->
                            input.text.toString().toIntOrNull() ?: 0
                        }
                        
                        db.collection("biases").document("Bias_normal")
                            .set(biasData)
                            .await()
                            
                        Toast.makeText(context, "Biases saved successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Log.e("InterviewFragment", "Error saving biases: ${e.message}")
                        Toast.makeText(context, "Failed to save biases. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private suspend fun getBiasForCriterion(criterion: String): Int {
        return try {
            val fieldName = when (criterion) {
                "Fluency in Hindi" -> "Fluency_in_hindi"
                "Content Delivery" -> "Content_delivery"
                "Overall Body Language" -> "Overall_body_language"
                "Resourcefulness" -> "Resourcefullness"
                "General Questions" -> "General_questions"
                else -> throw IllegalArgumentException("Unknown criterion: $criterion")
            }
            
            val doc = db.collection("biases").document("Bias_normal").get().await()
            doc.getLong(fieldName)?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e("InterviewFragment", "Error getting bias: ${e.message}")
            0
        }
    }

    private fun showNextPage(parent: LinearLayout, rollNo: String, benchNo: String) {
        // First show bench assignment confirmation
        showBenchAssignmentConfirmation(parent, rollNo, benchNo)
    }

    private fun showBenchAssignmentConfirmation(parent: LinearLayout, rollNo: String, benchNo: String) {
        parent.removeAllViews()

        val context = requireContext()

        val title = TextView(context)
        title.text = "Bench Assigned"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.setPadding(0, 0, 0, 32)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.gravity = Gravity.CENTER
        parent.addView(title)

        val rollNoView = TextView(context)
        rollNoView.text = "Roll Number: $rollNo"
        rollNoView.setTextColor(Color.WHITE)
        rollNoView.textSize = 18f
        rollNoView.setPadding(0, 0, 0, 16)
        parent.addView(rollNoView)

        val benchNoView = TextView(context)
        benchNoView.text = "Bench Number: $benchNo"
        benchNoView.setTextColor(Color.WHITE)
        benchNoView.textSize = 18f
        parent.addView(benchNoView)

        val proceedBtn = Button(context)
        proceedBtn.text = "Proceed to Evaluation"
        proceedBtn.setTextColor(Color.BLACK)
        proceedBtn.textSize = 16f
        proceedBtn.setBackgroundColor(Color.parseColor("#006BFF"))
        val proceedParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        proceedParams.setMargins(0, 64, 0, 16)
        proceedBtn.layoutParams = proceedParams
        parent.addView(proceedBtn)

        val returnBtn = Button(context)
        returnBtn.text = "Back"
        returnBtn.setTextColor(Color.BLACK)
        returnBtn.textSize = 16f
        returnBtn.setBackgroundColor(Color.parseColor("#006BFF"))
        val returnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        returnParams.setMargins(0, 0, 0, 0)
        returnBtn.layoutParams = returnParams
        parent.addView(returnBtn)

        proceedBtn.setOnClickListener {
            showEvaluationForm(parent, rollNo, benchNo)
        }

        returnBtn.setOnClickListener {
            requireActivity().recreate()
        }
    }

    private fun showEvaluationForm(parent: LinearLayout, rollNo: String, benchNo: String) {
        parent.removeAllViews()
        
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.parseColor("#181818"))
        layout.setPadding(48, 48, 48, 48)
        layout.gravity = Gravity.CENTER_HORIZONTAL

        // Title
        val title = TextView(requireContext())
        title.text = "Interview Evaluation"
        title.setTextColor(Color.WHITE)
        title.textSize = 24f
        title.setPadding(0, 0, 0, 32)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        layout.addView(title)

        // Candidate Info
        val candidateInfo = TextView(requireContext())
        candidateInfo.text = "Roll No: $rollNo | Bench: $benchNo"
        candidateInfo.setTextColor(Color.WHITE)
        candidateInfo.textSize = 16f
        candidateInfo.setPadding(0, 0, 0, 48)
        layout.addView(candidateInfo)

        // Create rating boxes for each criterion
        val criteria = listOf(
            "Fluency in Hindi",
            "Content Delivery",
            "Overall Body Language",
            "Resourcefulness",
            "General Questions"
        )

        val ratingInputs = mutableListOf<EditText>()

        criteria.forEach { criterion ->
            // Criterion Label
            val label = TextView(requireContext())
            label.text = criterion
            label.setTextColor(Color.WHITE)
            label.textSize = 16f
            label.setPadding(0, 0, 0, 8)
            layout.addView(label)

            // Rating Input
            val ratingInput = EditText(requireContext())
            ratingInput.hint = "Enter rating (0-10)"
            ratingInput.setHintTextColor(Color.parseColor("#AAAAAA"))
            ratingInput.setTextColor(Color.WHITE)
            ratingInput.textSize = 18f
            ratingInput.setBackgroundColor(Color.parseColor("#232323"))
            ratingInput.setPadding(32, 24, 32, 24)
            ratingInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            ratingInput.filters = arrayOf(android.text.InputFilter.LengthFilter(2))
            
            // Add input filter to limit to 0-10
            ratingInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val rating = ratingInput.text.toString().toIntOrNull() ?: 0
                    if (rating > 10) {
                        ratingInput.setText("10")
                        Toast.makeText(context, "Maximum rating is 10", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val inputParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputParams.setMargins(0, 0, 0, 32)
            ratingInput.layoutParams = inputParams
            layout.addView(ratingInput)
            ratingInputs.add(ratingInput)
        }

        // Submit Button
        val submitButton = Button(requireContext())
        submitButton.text = "Submit Evaluation"
        submitButton.setTextColor(Color.BLACK)
        submitButton.textSize = 16f
        submitButton.setBackgroundColor(Color.parseColor("#006BFF"))
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.setMargins(0, 32, 0, 0)
        submitButton.layoutParams = btnParams
        layout.addView(submitButton)

        submitButton.setOnClickListener {
            // Get ratings directly from the input list
            val ratings = ratingInputs.map { input ->
                input.text.toString().toIntOrNull() ?: 0
            }

            if (ratings.any { it !in 0..10 }) {
                Toast.makeText(context, "All ratings must be between 0 and 10", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    // Get biases for each criterion
                    val biases = criteria.map { criterion ->
                        getBiasForCriterion(criterion)
                    }

                    // Debug logs for ratings and biases
                    Log.d("DEBUG", "Ratings: $ratings")
                    Log.d("DEBUG", "Biases: $biases")

                    // Calculate products of scores and biases
                    val scoreProducts = ratings.zip(biases).map { (rating, bias) ->
                        rating * bias
                    }

                    // Debug log for products
                    Log.d("DEBUG", "Products: $scoreProducts")

                    // Calculate the sum of products (unnormalized score)
                    val unnormalizedScore = scoreProducts.sum()

                    // Debug logs
                    Log.d("DEBUG", "rollNo: $rollNo")
                    Log.d("DEBUG", "Final Score: $unnormalizedScore")

                    val docRef = db.collection("Student").document(rollNo)
                    val evaluationData = mapOf(
                        "Interview bench" to benchNo,
                        "Itw_score_unormalised" to unnormalizedScore,
                        "Evaluation Date" to Timestamp.now()
                    )

                    // Use set with merge to ensure the document is updated or created safely
                    docRef.set(evaluationData, SetOptions.merge()).await()

                    Toast.makeText(context, "Evaluation submitted successfully", Toast.LENGTH_SHORT).show()
                    requireActivity().recreate()
                } catch (e: Exception) {
                    Log.e("InterviewFragment", "Error saving evaluation: ${e.message}")
                    Toast.makeText(context, "Failed to save evaluation. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        parent.addView(layout)
    }
}
