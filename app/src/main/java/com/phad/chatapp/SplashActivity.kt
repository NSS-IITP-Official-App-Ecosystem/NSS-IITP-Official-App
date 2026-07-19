package com.phad.chatapp

import android.content.Intent
import com.phad.chatapp.activities.LoginActivity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.phad.chatapp.databinding.ActivitySplashBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.google.android.exoplayer2.PlaybackException

class SplashActivity : AppCompatActivity() {
	private lateinit var binding: ActivitySplashBinding
	private var player: ExoPlayer? = null
	private val mainHandler = Handler(Looper.getMainLooper())
	private var canNavigate = false
	private var updateCheckComplete = false
	private var videoEnded = false
	private var isCheckingUpdate = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySplashBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// DEBUG: Long press on splash to show update dialog for testing
		setupDebugTestTrigger()

		initPlayer()
		
		// DEBUG: Check if we should skip update check (for testing UI)
		val skipUpdateCheck = intent.getBooleanExtra("SKIP_UPDATE_CHECK", false)
		if (!skipUpdateCheck) {
			checkForUpdates()
		}
	}
	
	private fun setupDebugTestTrigger() {
		binding.root.setOnLongClickListener {
			// Show update dialog immediately for testing
			canNavigate = false
			updateCheckComplete = true
			showForceUpdateDialog()
			true
		}
	}

	override fun onStart() {
		super.onStart()
		player?.playWhenReady = true
	}

	override fun onStop() {
		super.onStop()
		binding.videoViewSplash.player = null
		player?.release()
		player = null
	}

	override fun onResume() {
		super.onResume()
		if (!updateCheckComplete || !canNavigate) {
			checkForUpdates()
		}
	}
	
	override fun onBackPressed() {
		// If update is required, prevent back button from working
		if (!canNavigate) {
			// Force exit if user tries to go back during mandatory update
			finishAffinity()
			android.os.Process.killProcess(android.os.Process.myPid())
			return
		}
		super.onBackPressed()
	}

	private fun initPlayer() {
		// Build exoplayer
		player = ExoPlayer.Builder(this).build().also { exoPlayer ->
			binding.videoViewSplash.player = exoPlayer
			binding.videoViewSplash.setShutterBackgroundColor(android.graphics.Color.WHITE)
			val uri: Uri = RawResourceDataSource.buildRawResourceUri(R.raw.nss_splash_anim)
			val mediaItem = MediaItem.fromUri(uri)
			exoPlayer.setMediaItem(mediaItem)
			exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
			exoPlayer.prepare()
			exoPlayer.playWhenReady = true

			// Add tap to skip
			binding.root.setOnClickListener {
				if (!isFinishing) {
					videoEnded = true
					tryNavigate()
				}
			}

			exoPlayer.addListener(object : Player.Listener {
				override fun onPlaybackStateChanged(state: Int) {
					when (state) {
						Player.STATE_READY -> {
							// First frame will render; nothing to hide (white bg)
						}
						Player.STATE_ENDED -> {
							videoEnded = true
							tryNavigate()
						}
					}
				}

				override fun onPlayerError(error: PlaybackException) {
					mainHandler.postDelayed({ 
						videoEnded = true
						tryNavigate()
					}, 800)
				}
			})
		}

		// Safety timeout in case player never becomes READY
		mainHandler.postDelayed({ 
			if (!isFinishing) {
				videoEnded = true
				tryNavigate()
			}
		}, 3000)
	}

	private fun checkForUpdates() {
        // No-op: Logic moved to NssMainActivity
        tryNavigate()
	}

	private fun tryNavigate() {
		// Only navigate if video has ended or timeout occurred
		if (videoEnded && !isFinishing) {
			navigateToNextScreen()
		}
	}

	private fun showForceUpdateDialog(message: String? = null) {
        // No-op
	}

	private fun dismissForceUpdateDialog() {
        // No-op
	}


	private fun navigateToNextScreen() {
		val sessionManager = com.phad.chatapp.utils.SessionManager(this)
		val isLoggedIn = sessionManager.isLoggedIn()
		
		val nextIntent = if (isLoggedIn) {
		    val lastChoice = sessionManager.getLastInterfaceChoice()
		    if (lastChoice == "TEACHING_WING") {
		        Intent(this, MainActivity::class.java)
		    } else {
			    Intent(this, NssMainActivity::class.java)
			}
		} else {
			Intent(this, LoginActivity::class.java)
		}
		
		// Forward any extras (e.g. from FCM background push data) so they aren't lost
		intent.extras?.let { nextIntent.putExtras(it) }
		
		startActivity(nextIntent)
		finish()
	}


}