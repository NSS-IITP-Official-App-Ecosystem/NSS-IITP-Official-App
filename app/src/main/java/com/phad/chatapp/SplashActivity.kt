package com.phad.chatapp

import android.content.Intent
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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySplashBinding.inflate(layoutInflater)
		setContentView(binding.root)

		initPlayer()
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
							navigateToNextScreen()
						}
					}
				}

				override fun onPlayerError(error: PlaybackException) {
					mainHandler.postDelayed({ navigateToNextScreen() }, 800)
				}
			})
		}

		// Safety timeout in case player never becomes READY
		mainHandler.postDelayed({ if (!isFinishing) navigateToNextScreen() }, 12000)
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