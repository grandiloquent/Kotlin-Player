package psycho.euphoria.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.FileDataSourceFactory
import java.io.File
import java.io.FileFilter
import kotlin.math.max

class MainActivity : Activity(), PlayerControlView.VisibilityListener, PlaybackPreparer {
    private lateinit var mPlayerView: PlayerView
    private var mPlayer: ExoPlayer? = null
    private var mTrackSelector: DefaultTrackSelector? = null
    private var mStratAutoPlay = false
    private var mTrackSelectorParameters: DefaultTrackSelector.Parameters? = null
    private var mStartWindow = C.INDEX_UNSET
    private var mStartPosition = C.TIME_UNSET
    private var mMediaSource: MediaSource? = null
    private fun clearStartPosition() {


        mStratAutoPlay = true
        mStartWindow = C.INDEX_UNSET
        mStartPosition = C.TIME_UNSET
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {


        return mPlayerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    private fun generateMediaSource(uri: Uri): MediaSource? {


        val files = File(uri.path).parentFile.listFiles(FileFilter { it.isFile && it.isVideo() })
                ?: return null
        var fileDataSourceFactory = FileDataSourceFactory()
        val length = files.size
        if (length == 1) {
            mStartWindow = 0
            return ExtractorMediaSource.Factory(fileDataSourceFactory).createMediaSource(Uri.fromFile(files[0]))
        }
        val mediaSources = arrayOfNulls<MediaSource>(length)
        for (i in 0 until length) {
            val u = Uri.fromFile(files[i])
            if (uri == u) {
                mStartWindow = i
            }
            mediaSources[i] = ExtractorMediaSource.Factory(fileDataSourceFactory).createMediaSource(u)
        }
        val concatenatingMediaSource = ConcatenatingMediaSource()
        for (i in 0 until length) {
            concatenatingMediaSource.addMediaSource(mediaSources[i])
        }
        return concatenatingMediaSource
    }

    private fun initialize() {


    }

    private fun initializePlayer() {


        if (mPlayer == null) {
            mPlayer = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector()).also {
                mPlayerView.player = it
                mPlayerView.setPlaybackPreparer(this)
                //it.playWhenReady = true
                it.addListener(object : Player.EventListener {
                    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
                    }

                    override fun onSeekProcessed() {

                    }

                    override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
                        //Log.e(TAG,"trackGroups => ${trackGroups} \ntrackSelections => ${trackSelections} \n")
                    }

                    override fun onPlayerError(error: ExoPlaybackException?) {
                        //Log.e(TAG,"error => ${error} \n")
                    }

                    override fun onLoadingChanged(isLoading: Boolean) {

                    }

                    override fun onPositionDiscontinuity(reason: Int) {

                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {

                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {

                    }

                    override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
                        //Log.e(TAG,"timeline => ${timeline} \nmanifest => ${manifest} \nreason => ${reason} \n")
                    }

                    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        //Log.e(TAG,"playWhenReady => ${playWhenReady} \nplaybackState => ${playbackState} \n")
                    }
                })
            }
            var uri = intent.data
            uri?.let {
                mMediaSource = generateMediaSource(it)
            }
        }
        mMediaSource?.let {

            mPlayer?.prepare(it)
        }
    }

    private fun initializePlayerView() {


        mPlayerView = findViewById(R.id.player_view)
        mPlayerView.let {
            it.setControllerVisibilityListener(this)
            it.requestFocus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializePlayerView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"), REQUEST_PERMISSION_CODE)
        } else initialize()
    }

    override fun onDestroy() {


        super.onDestroy()
        releasePlayer()
    }

    override fun onNewIntent(intent: Intent?) {


        releasePlayer()
        setIntent(intent)
    }

    override fun onPause() {


        super.onPause()
        atMost(23, { releasePlayer() }, {})
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {


        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == 0 })
            initialize()
    }

    override fun onResume() {


        super.onResume()
        atMost(23, { if (mPlayer == null) initializePlayer() }, {})
    }

    override fun onStart() {


        super.onStart()
        more(23, { initializePlayer() }, {})
    }

    override fun onStop() {


        super.onStop()
        more(23, { releasePlayer() }, {})
    }

    override fun onVisibilityChange(visibility: Int) {


    }

    override fun preparePlayback() {


        initializePlayer()
    }

    private fun releasePlayer() {


        mPlayer?.let {
            updateTrackSelectorParameters()
            updateStartPosition()
            it.release()
            mPlayer = null
            mMediaSource = null
            mTrackSelector = null
        }
    }

    private fun updateStartPosition() {


        mPlayer?.let {
            mStratAutoPlay = it.playWhenReady
            mStartWindow = it.currentWindowIndex
            mStartPosition = max(0, it.contentPosition)
        }
    }

    private fun updateTrackSelectorParameters() {


        mTrackSelector?.let {
            mTrackSelectorParameters = it.parameters
        }
    }

    companion object {
        const val REQUEST_PERMISSION_CODE = 10
        private const val TAG = "MainActivity"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_WINDOW = "window"
        private const val KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters"
        private const val KEY_POSITION = "position"
    }
}