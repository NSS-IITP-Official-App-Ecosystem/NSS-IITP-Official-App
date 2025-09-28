package com.phad.chatapp.utils

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

object AddItemDialog {

    fun show(context: Context, title: String, onConfirm: (String) -> Unit) {
        val inputEditText = EditText(context)
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(inputEditText)
            .setPositiveButton("Add") { dialog, _ ->
                val enteredText = inputEditText.text.toString().trim()
                if (enteredText.isNotEmpty()) {
                    onConfirm(enteredText)
                    dialog.dismiss()
                } else {
                    inputEditText.error = "Title cannot be empty"
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()

        dialog.show()
    }
} 