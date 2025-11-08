package com.phad.chatapp.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.phad.chatapp.R

class ForceUpdateDialogFragment : DialogFragment() {

    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        message = arguments?.getString(ARG_MESSAGE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_force_update, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Allow the activity behind to be visible but dimmed
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes.dimAmount = 0.7f
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val messageView: TextView = view.findViewById(R.id.message)
        val updateButton: Button = view.findViewById(R.id.button_update)
        val exitButton: TextView = view.findViewById(R.id.button_exit)

        message?.let {
            if (it.isNotBlank()) {
                messageView.text = it
            }
        }

        updateButton.setOnClickListener {
            openPlayStore()
        }

        exitButton.setOnClickListener {
            requireActivity().finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun openPlayStore() {
        val packageName = requireContext().packageName
        val marketIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            val url = "https://play.google.com/store/apps/details?id=$packageName"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
        }
    }

    companion object {
        private const val ARG_MESSAGE = "arg_force_update_message"

        fun newInstance(message: String? = null): ForceUpdateDialogFragment {
            return ForceUpdateDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                }
            }
        }

        const val TAG = "ForceUpdateDialog"
    }
}


