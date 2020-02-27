package tgio.github.com.mediapickerlib.videoProcessing

import android.animation.ValueAnimator
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.recyclerview.widget.RecyclerView
import net.protyposis.android.mediaplayer.MediaPlayer
import net.protyposis.android.mediaplayer.VideoView
import tgio.github.com.mediapickerlib.R
import tgio.github.com.mediapickerlib.videoProcessing.callbacks.VideoCompressListener
import tgio.github.com.mediapickerlib.videoProcessing.callbacks.VideoTrimListener
import tgio.github.com.mediapickerlib.videoProcessing.proccessing.VideoProccessing
import tgio.github.com.mediapickerlib.videoProcessing.widget.OnRangeSeekBarChangeListener
import tgio.github.com.mediapickerlib.videoProcessing.widget.RangeSeekBarView
import tgio.github.com.mediapickerlib.videoProcessing.widget.TimelineView
import kotlin.math.abs
import kotlin.math.min

class VideoTrimmer(
    context: Context,
    private val timelineView: TimelineView,
    private val videoView: VideoView,
    private val rangeSeekBarView: RangeSeekBarView,
    private val paramMinDuration: Long,
    private val paramMaxDuration: Long,
    private val paramMaxDisplayedThumbs: Int,
    private val videoSource: Uri,
    private val setIsPlaying: (Boolean) -> Unit,
    private val setDurationText: (Long) -> Unit,
    private val applyVideoViewParams: (Int, Int) -> Unit
) : RecyclerView.OnScrollListener(),
    OnRangeSeekBarChangeListener,
    MediaPlayer.OnPreparedListener {

    private var screenWidth = 0
    private var horizontalPadding = 0
    private var videoFramesWidth = 0
    private var mMaxWidth = 0
    private var mAverageMsPx = 0f
    private var averagePxMs = 0f
    private var mDuration = 0L
    private var mMaxDuration = 0L
    private var mLeftProgressPos: Long = 0
    private var mRightProgressPos: Long = 0
    private var scrollPos: Long = 0
    private val mScaledTouchSlop = 0
    private var lastScrollX = 0
    private var isSeeking = false
    private var isOverScaledTouchSlop = false
    private var mThumbsTotalCount = 0
    private var pausedVideoPosition = 0
    private var pausedProgressPosition = 0F

    init {
        screenWidth = context.resources.displayMetrics.widthPixels
        horizontalPadding =
            context.resources.getDimensionPixelOffset(R.dimen.bloom_native_paddingTimeline) + context.resources.getDimensionPixelOffset(
                R.dimen.bloom_native_thumb_width
            )
        videoFramesWidth = screenWidth - horizontalPadding * 2
        mMaxWidth = videoFramesWidth
        timelineView.addOnScrollListener(this)
        rangeSeekBarView.setOnRangeSeekBarChangeListener(this)
        videoView.setOnPreparedListener(this)
        videoView.setVideoURI(videoSource)
    }

    fun compress(sourcePath: String, outputPath: String, listener: VideoCompressListener) {
        VideoProccessing.compress(sourcePath, outputPath, listener)
    }

    fun trimVideo(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        doEncode: Boolean,
        listener: VideoTrimListener
    ) {
        VideoProccessing.trim(
            inputFile = inputPath,
            _outputFile = outputPath,
            startMs = startMs,
            endMs = endMs,
            encode = doEncode,
            callback = listener
        )
    }

    private fun playAfterSeek() {
        valueAnimator?.cancel()
        playProgressAnimation()
    }

    override fun onRangeSeekBarValuesChanged(
        bar: RangeSeekBarView?,
        minValue: Long,
        maxValue: Long,
        action: Int,
        pressedThumb: RangeSeekBarView.Thumb?
    ) {
        mLeftProgressPos = minValue + scrollPos
        mRightProgressPos = maxValue + scrollPos
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isSeeking = false
                setIsPlaying.invoke(true)
            }
            MotionEvent.ACTION_MOVE -> {
                isSeeking = true
                if (pressedThumb == RangeSeekBarView.Thumb.L) {
                    seekTo(mLeftProgressPos)
                    playAfterSeek()
                } else {
                    seekTo(mRightProgressPos)
                    playAfterSeek()
                }
            }
            MotionEvent.ACTION_UP -> {
                setIsPlaying.invoke(false)
                isSeeking = false
                seekTo(mLeftProgressPos)
                playAfterSeek()
                rangeSeekBarView.setProgressPos(0F)
            }
            else -> {
            }
        }

        val length = rangeSeekBarView.getEndPosition() - rangeSeekBarView.getStartPosition()
        setDurationText.invoke(length)
    }

    private fun seekTo(msec: Long) {
        videoView.seekTo(msec.toInt())
    }

    private fun calcrangeSeekBarView() {
        val rangeWidth: Int
        mLeftProgressPos = 0
        if (mDuration <= paramMaxDuration) {
            mThumbsTotalCount = paramMaxDisplayedThumbs
            rangeWidth = mMaxWidth
            mRightProgressPos = mDuration
            mMaxDuration = mDuration
            setDurationText(paramMaxDuration)
        } else {
            mThumbsTotalCount =
                (mDuration * 1.0f / (paramMaxDuration * 1.0f) * paramMaxDisplayedThumbs).toInt()
            rangeWidth = mMaxWidth / paramMaxDisplayedThumbs * mThumbsTotalCount
            mRightProgressPos = paramMaxDuration
            mMaxDuration = paramMaxDuration
        }
        rangeSeekBarView.setParams(
            startPosition = mLeftProgressPos,
            endPosition = mRightProgressPos,
            minDuration = paramMinDuration,
            maxDuration = mMaxDuration
        )
        rangeSeekBarView.selectedMinValue = mLeftProgressPos
        rangeSeekBarView.selectedMaxValue = mRightProgressPos
        rangeSeekBarView.isNotifyWhileDragging = true
        rangeSeekBarView.reset()

        valueAnimator?.cancel()

        mAverageMsPx = mDuration * 1.0f / rangeWidth * 1.0f
        averagePxMs = mMaxWidth * 1.0f / (mRightProgressPos - mLeftProgressPos)
        val length = rangeSeekBarView.getEndPosition() - rangeSeekBarView.getStartPosition()
        setDurationText.invoke(length)
    }

    override fun onScrolled(
        recyclerView: RecyclerView,
        dx: Int,
        dy: Int
    ) {
        isSeeking = false
        rangeSeekBarView.setIsSeeking(false)
        val scrollX = timelineView.calcScrollXDistance()
        if (abs(lastScrollX - scrollX) < mScaledTouchSlop) {
            isOverScaledTouchSlop = false
            return
        }
        isOverScaledTouchSlop = true
        if (scrollX == -horizontalPadding) {
            scrollPos = 0
        } else {
            isSeeking = true
            rangeSeekBarView.setIsSeeking(true)
            scrollPos = (mAverageMsPx * (horizontalPadding + scrollX)).toLong()
            mLeftProgressPos = rangeSeekBarView.selectedMinValue + scrollPos
            mRightProgressPos = rangeSeekBarView.selectedMaxValue + scrollPos

            rangeSeekBarView.setExtraMsFromTimeline(scrollPos)

            rangeSeekBarView.alignProgressPosition()
            if (rangeSeekBarView.isRightThumbPressed()) {
                seekTo(mRightProgressPos)
            } else {
                seekTo(mLeftProgressPos)
            }
            playAfterSeek()
        }
        lastScrollX = scrollX
    }

    private var isPrepared = false

    override fun onPrepared(mp: MediaPlayer) {
        if(isPrepared.not()) {
            applyVideoViewParams.invoke(mp.videoWidth, mp.videoHeight)
            mDuration = videoView.duration.toLong()
            rangeSeekBarView.setDuration(mDuration)
            calcrangeSeekBarView()
            timelineView.setData(
                videoLengthMs = mDuration,
                numThumbs = mThumbsTotalCount,
                videoPath = videoSource.path!!,
                maxDisplayedThumbsCount = paramMaxDisplayedThumbs
            )
            isPrepared = true
        }
    }

    fun reset() {
        valueAnimator?.cancel()
        rangeSeekBarView.reset()
        timelineView.reset()
        videoView.pause()
        videoView.seekTo(0)
        setIsPlaying.invoke(false)
        mLeftProgressPos = 0
        scrollPos = 0
        mRightProgressPos = mMaxDuration
        averagePxMs = mMaxWidth * 1.0f / (mRightProgressPos - mLeftProgressPos)
        setDurationText(min(paramMaxDuration, mDuration))
    }

    private fun playProgressAnimation() {
        val from = if (rangeSeekBarView.isRightThumbPressed())
            rangeSeekBarView.getRightPos()
        else
            rangeSeekBarView.getProgressPos()

        val to = rangeSeekBarView.getRightPos()

        val fromMs = videoView.currentPosition
        val toMs = mRightProgressPos

        val duration = toMs - fromMs

        if (duration < 0)
            return

        valueAnimator = ValueAnimator.ofFloat(from, to)
        valueAnimator?.duration = duration
        valueAnimator?.interpolator = LinearInterpolator()
        valueAnimator?.addUpdateListener {
            setIsPlaying.invoke(videoView.isPlaying)
            rangeSeekBarView.setProgressPos(it.animatedValue as Float)
            if (it.animatedValue as Float >= to) {
                valueAnimator?.cancel()
            }
        }
        valueAnimator?.doOnStart {
            videoView.start()
            setIsPlaying.invoke(true)
        }
        valueAnimator?.doOnEnd {
            videoView.pause()
            if (rangeSeekBarView.isRightThumbPressed()) {
                rangeSeekBarView.setProgressPos(rangeSeekBarView.getRightPos())
            } else {
                setIsPlaying.invoke(false)
                videoView.seekTo(mLeftProgressPos.toInt())
                rangeSeekBarView.setProgressPos(rangeSeekBarView.getLeftPos())
            }
        }
        valueAnimator?.doOnCancel {
            setIsPlaying.invoke(isSeeking)
        }
        valueAnimator?.start()
    }

    private var valueAnimator: ValueAnimator? = null

    fun playVideoOrPause() {
        if (videoView.isPlaying) {
            valueAnimator?.pause()
            if (videoView.isPlaying) {
                videoView.pause()
            }
        } else {
            playProgressAnimation()
        }
        setIsPlaying.invoke(videoView.isPlaying)
    }

    fun onPause() {
        pausedProgressPosition = rangeSeekBarView.getProgressPos()
        pausedVideoPosition = videoView.currentPosition
        valueAnimator?.pause()
        videoView.pause()
        setIsPlaying.invoke(false)
    }

    fun onResume() {
        videoView.seekTo(pausedVideoPosition)
        rangeSeekBarView.setProgressPos(pausedProgressPosition)
    }
}