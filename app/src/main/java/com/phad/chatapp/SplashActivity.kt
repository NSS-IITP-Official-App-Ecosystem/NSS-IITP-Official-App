package com.phad.chatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.phad.chatapp.databinding.ActivitySplashBinding
import com.phad.chatapp.ui.dialogs.ForceUpdateDialogFragment
import com.phad.chatapp.utils.PlayStoreUpdateChecker
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
	private var forceUpdateDialog: ForceUpdateDialogFragment? = null

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
		}, 12000)
	}

	private fun checkForUpdates() {
		if (isCheckingUpdate || isFinishing) {
			return
		}
		isCheckingUpdate = true

		PlayStoreUpdateChecker.checkForImmediateUpdate(this) { result ->
			runOnUiThread {
				isCheckingUpdate = false
				updateCheckComplete = true
				when (result) {
					PlayStoreUpdateChecker.Result.UpdateAvailable -> {
						canNavigate = false
						showForceUpdateDialog()
					}
					PlayStoreUpdateChecker.Result.NoUpdate -> {
						canNavigate = true
						dismissForceUpdateDialog()
						tryNavigate()
					}
					is PlayStoreUpdateChecker.Result.Failure -> {
						canNavigate = true
						dismissForceUpdateDialog()
						tryNavigate()
					}
				}
			}
		}
	}

	private fun tryNavigate() {
		// Only navigate if both conditions are met:
		// 1. Update check is complete AND no update is required (canNavigate = true)
		// 2. Video has ended or timeout occurred
		if (updateCheckComplete && canNavigate && videoEnded && !isFinishing) {
			navigateToNextScreen()
		}
	}

	private fun showForceUpdateDialog(message: String? = null) {
		val existing = forceUpdateDialog
		if (existing?.isAdded == true) {
			return
		}

		forceUpdateDialog = ForceUpdateDialogFragment.newInstance(message).also { dialog ->
			dialog.show(supportFragmentManager, ForceUpdateDialogFragment.TAG)
		}
	}

	private fun dismissForceUpdateDialog() {
		forceUpdateDialog?.dismissAllowingStateLoss()
		forceUpdateDialog = null
	}

	private fun navigateToNextScreen() {
		val isLoggedIn = /* TODO: Replace with actual login check */ false
		if (isLoggedIn) {
			startActivity(Intent(this, NssMainActivity::class.java))
		} else {
			startActivity(Intent(this, LoginActivity::class.java))
		}
		finish()
	}


}