package com.iknow.android.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.iknow.android.R;
import com.iknow.android.features.trim.VideoTrimmerAdapter;
import com.iknow.android.interfaces.IVideoTrimmerView;
import com.iknow.android.interfaces.VideoTrimListener;
import com.iknow.android.features.trim.VideoTrimmerUtil;
import com.iknow.android.utils.StorageUtil;

import iknow.android.utils.UnitConverter;
import iknow.android.utils.callback.SingleCallback;
import iknow.android.utils.thread.BackgroundExecutor;
import iknow.android.utils.thread.UiThreadExecutor;

import static com.iknow.android.features.trim.VideoTrimmerUtil.MAX_COUNT_RANGE; // this is set as 10 ? the length of seek view
import static com.iknow.android.features.trim.VideoTrimmerUtil.MAX_SHOOT_DURATION; // this is set at 10seconds
import static com.iknow.android.features.trim.VideoTrimmerUtil.RECYCLER_VIEW_PADDING;
import static com.iknow.android.features.trim.VideoTrimmerUtil.THUMB_WIDTH;
import static com.iknow.android.features.trim.VideoTrimmerUtil.VIDEO_FRAMES_WIDTH;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */


// called from   com/iknow/android/features/trim/VideoTrimmerActivity.java
// as mBinding.trimmerView.initVideoByURI(Uri.parse(path));
public class VideoTrimmerView<averagePxMs> extends FrameLayout implements IVideoTrimmerView {

  //IVideoTrimmerView - interface with a single method void onDestroy();



  private static final String TAG = VideoTrimmerView.class.getSimpleName();

  // getSimpleName() returns a simple name of the class

  private int mMaxWidth = VIDEO_FRAMES_WIDTH;
  private Context mContext;
  private RelativeLayout mLinearVideo;
  private ZVideoView mVideoView;
  private ImageView mPlayView;
  private RecyclerView mVideoThumbRecyclerView;
  private RangeSeekBarView mRangeSeekBarView;
  private LinearLayout mSeekBarLayout;
  private ImageView mRedProgressIcon;
  private TextView mVideoShootTipTv;
  private float mAverageMsPx;//每毫秒所占的px translates to pixel per milliseconds
  private float averagePxMs;//每px所占用的ms毫秒  translates to milliseconds per pixel
  private Uri mSourceUri;
  private VideoTrimListener mOnTrimVideoListener; // interface with 3 methods  void onStartTrim();
                                                                    //void onFinishTrim(String url);
                                                                    // void onCancel();
  private int mDuration = 0;
  private VideoTrimmerAdapter mVideoThumbAdapter; // ?  timeline recyclerview adapater
  private boolean isFromRestore = false;
  //new
  private long mLeftProgressPos, mRightProgressPos;
  private long mRedProgressBarPos = 0;
  private long scrollPos = 0; // position of the right end limit of mRangeSeekBarView
  private int mScaledTouchSlop;
  private int lastScrollX;
  private boolean isSeeking;
  private boolean isOverScaledTouchSlop;
  private int mThumbsTotalCount;
  private ValueAnimator mRedProgressAnimator;
  private Handler mAnimationHandler = new Handler();  // ? what's a handler - ? pushes data from background thread to message que of UI thread

  public VideoTrimmerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoTrimmerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(Context context) {
    this.mContext = context;
    LayoutInflater.from(context).inflate(R.layout.video_trimmer_view, this, true);

    mLinearVideo = findViewById(R.id.layout_surface_view);
    mVideoView = findViewById(R.id.video_loader);
    mPlayView = findViewById(R.id.icon_video_play);
    mSeekBarLayout = findViewById(R.id.seekBarLayout);
    mRedProgressIcon = findViewById(R.id.positionIcon);
    mVideoShootTipTv = findViewById(R.id.video_shoot_tip);
    mVideoThumbRecyclerView = findViewById(R.id.video_frames_recyclerView);
    mVideoThumbRecyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
    mVideoThumbAdapter = new VideoTrimmerAdapter(mContext);   // public class VideoTrimmerAdapter extends RecyclerView.Adapter
    mVideoThumbRecyclerView.setAdapter(mVideoThumbAdapter);
    mVideoThumbRecyclerView.addOnScrollListener(mOnScrollListener);
    setUpListeners();   // this method calls videoPrepared(MediaPlayer mp) which in turn calls
                                      //  startShootVideoThumbs(mContext, mSourceUri, mThumbsTotalCount, 0, mDuration);
  }

  private void initRangeSeekBarView() {
    if(mRangeSeekBarView != null) return;
    mLeftProgressPos = 0;
    if (mDuration <= MAX_SHOOT_DURATION) { //  MAX_SHOOT_DURATION = VIDEO_MAX_TIME * 1000L and mDuration = mVideoView.getDuration();
      mThumbsTotalCount = MAX_COUNT_RANGE; // set as 10 - seekbar length   // VIDEO_MAX_TIME = 10
      mRightProgressPos = mDuration;
    } else {
      mThumbsTotalCount = (int) (mDuration * 1.0f / (MAX_SHOOT_DURATION * 1.0f) * MAX_COUNT_RANGE);
      mRightProgressPos = MAX_SHOOT_DURATION;

    }
    Log.i("MAX_SHOOT_DURATION", String.valueOf(MAX_SHOOT_DURATION));
    Log.i("MAX_COUNT_RANGE", String.valueOf(MAX_COUNT_RANGE));
    Log.i(" mDuration", String.valueOf(mDuration));
    Log.i(" mThumbsTotalCount", String.valueOf(mThumbsTotalCount));

    mVideoThumbRecyclerView.addItemDecoration(new SpacesItemDecoration2(RECYCLER_VIEW_PADDING, mThumbsTotalCount));
    //  public static final int RECYCLER_VIEW_PADDING = UnitConverter.dpToPx(35);
    Log.i("RECYCLER_VIEW_PADDING", "RECYCLER_VIEW_PADDING = " +RECYCLER_VIEW_PADDING);

    mRangeSeekBarView = new RangeSeekBarView(mContext, mLeftProgressPos, mRightProgressPos);
    mRangeSeekBarView.setSelectedMinValue(mLeftProgressPos);
    mRangeSeekBarView.setSelectedMaxValue(mRightProgressPos);
    mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos);
    mRangeSeekBarView.setMinShootTime(VideoTrimmerUtil.MIN_SHOOT_DURATION);
    mRangeSeekBarView.setNotifyWhileDragging(true);
    mRangeSeekBarView.setOnRangeSeekBarChangeListener(mOnRangeSeekBarChangeListener);
    mSeekBarLayout.addView(mRangeSeekBarView);
    if(mThumbsTotalCount - MAX_COUNT_RANGE>0) {
      mAverageMsPx = (mDuration - MAX_SHOOT_DURATION) / (float) (mThumbsTotalCount - MAX_COUNT_RANGE);
    }else{
      mAverageMsPx = 0f;
    }
    averagePxMs = (mMaxWidth * 1.0f / (mRightProgressPos - mLeftProgressPos));
    Log.i("averagePxMs", String.valueOf(averagePxMs));
  }

  // called from   com/iknow/android/features/trim/VideoTrimmerActivity.java
// as mBinding.trimmerView.initVideoByURI(Uri.parse(path));


  public void initVideoByURI(final Uri videoURI) {   // This is called from com/iknow/android/features/trim/VideoTrimmerActivity.java
                                                      // ? to initialize the view
    mSourceUri = videoURI;
    mVideoView.setVideoURI(videoURI);
    mVideoView.requestFocus();
    mVideoShootTipTv.setText(String.format(mContext.getResources().getString(R.string.video_shoot_tip), VideoTrimmerUtil.VIDEO_MAX_TIME));
  }
                                                                                                                        // endPosition is the total
                                                                                                                        // duration of the video
  private void startShootVideoThumbs(final Context context, final Uri videoUri, int totalThumbsCount, long startPosition, long endPosition) {
    VideoTrimmerUtil.shootVideoThumbInBackground(context, videoUri, totalThumbsCount, startPosition, endPosition,
        new SingleCallback<Bitmap, Integer>() {   // annonymous class SingleCallback<Bitmap, Integer>() // ? callback function
      // A CallBack Function is a function that is passed into another function as an
          // argument and is expected to execute after some kind of event.
          // The purpose of the callback function is to inform a class Sync/Async
          // if some work in another class is done.
          // This is very useful when working with Asynchronous tasks.
// This is similar to calling
// VideoTrimmerUtil.shootVideoThumbInBackground(context, videoUri, totalThumbsCount, startPosition, endPosition, callback = new SingleCallback<Bitmap, Integer>())
          @Override public void onSingleCallback(final Bitmap bitmap, final Integer interval) {  // this is called from shootVideoThumbInBackground(final Context context, final Uri videoUri, final int totalThumbsCount, final long startPosition,
          //  final long endPosition, final SingleCallback<Bitmap, Integer> callback)
            if (bitmap != null) {
              UiThreadExecutor.runTask("", new Runnable() {
                @Override public void run() {
                  mVideoThumbAdapter.addBitmaps(bitmap);
                }
              }, 0L);
            }
          }
        });
  }

  private void onCancelClicked() {
    mOnTrimVideoListener.onCancel();
  }

  private void videoPrepared(MediaPlayer mp) {
    ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
    int videoWidth = mp.getVideoWidth();
    int videoHeight = mp.getVideoHeight();

    float videoProportion = (float) videoWidth / (float) videoHeight;
    int screenWidth = mLinearVideo.getWidth();
    int screenHeight = mLinearVideo.getHeight();

    if (videoHeight > videoWidth) {
      lp.width = screenWidth;
      lp.height = screenHeight;
    } else {
      lp.width = screenWidth;
      float r = videoHeight / (float) videoWidth;
      lp.height = (int) (lp.width * r);
    }
    mVideoView.setLayoutParams(lp);
    mDuration = mVideoView.getDuration(); // Gives the total duration of the video( an integer )
    Log.i("mDuration", "mDuration in milliseconds = " + mDuration);
    if (!getRestoreState()) {
      seekTo((int) mRedProgressBarPos);
    } else {
      setRestoreState(false);
      seekTo((int) mRedProgressBarPos);
    }
    initRangeSeekBarView();
    startShootVideoThumbs(mContext, mSourceUri, mThumbsTotalCount, 0, mDuration);
    //mDuration is the total duration of the video
    //  only after the videoview is set with the uri
    //  then the method to load
    //  recyclerview with thumbnail data is called //
  }

  private void videoCompleted() {
    seekTo(mLeftProgressPos);
    setPlayPauseViewIcon(false);
  }

  private void onVideoReset() {
    mVideoView.pause();
    setPlayPauseViewIcon(false);
  }

  private void playVideoOrPause() {
    mRedProgressBarPos = mVideoView.getCurrentPosition();

    if (mVideoView.isPlaying()) {
      mVideoView.pause();
      pauseRedProgressAnimation();

    } else {
      mVideoView.start();
      playingRedProgressAnimation();

    }
    setPlayPauseViewIcon(mVideoView.isPlaying());
  }

  public void onVideoPause() {
    if (mVideoView.isPlaying()) {
      seekTo(mLeftProgressPos);//复位
      mVideoView.pause();
      setPlayPauseViewIcon(false);
      mRedProgressIcon.setVisibility(GONE);
    }
  }

  public void setOnTrimVideoListener(VideoTrimListener onTrimVideoListener) {
    mOnTrimVideoListener = onTrimVideoListener;
  }

  private void setUpListeners() {
    findViewById(R.id.cancelBtn).setOnClickListener(new OnClickListener() {
      @Override public void onClick(View view) {
        onCancelClicked();
      }
    });

    findViewById(R.id.finishBtn).setOnClickListener(new OnClickListener() {
      @Override public void onClick(View view) {
        onSaveClicked();
      }
    });
    mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      @Override public void onPrepared(MediaPlayer mp) {
        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        videoPrepared(mp);
      }
    });
    mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override public void onCompletion(MediaPlayer mp) {
        videoCompleted();
      }
    });
    mPlayView.setOnClickListener(new OnClickListener() {
      @Override public void onClick(View v) {
        playVideoOrPause();
      }
    });
  }

  private void onSaveClicked() {
    if (mRightProgressPos - mLeftProgressPos < VideoTrimmerUtil.MIN_SHOOT_DURATION) {
      Toast.makeText(mContext, "视频长不足3秒,无法上传", Toast.LENGTH_SHORT).show();
    } else {
      mVideoView.pause();
      VideoTrimmerUtil.trim(mContext,
          mSourceUri.getPath(),
          StorageUtil.getCacheDir(),
          mLeftProgressPos,
          mRightProgressPos,
          mOnTrimVideoListener);
    }
  }

  private void seekTo(long msec) {
    mVideoView.seekTo((int) msec);
    Log.d(TAG, "seekTo = " + msec);
  }

  private boolean getRestoreState() {
    return isFromRestore;
  }

  public void setRestoreState(boolean fromRestore) {
    isFromRestore = fromRestore;
  }

  private void setPlayPauseViewIcon(boolean isPlaying) {
    mPlayView.setImageResource(isPlaying ? R.drawable.ic_video_pause_black : R.drawable.ic_video_play_black);
  }

  private final RangeSeekBarView.OnRangeSeekBarChangeListener mOnRangeSeekBarChangeListener = new RangeSeekBarView.OnRangeSeekBarChangeListener() {
    @Override public void onRangeSeekBarValuesChanged(RangeSeekBarView bar, long minValue, long maxValue, int action, boolean isMin,
        RangeSeekBarView.Thumb pressedThumb) {
      // min value is rangeseekbarview's min scrolled position
      // max value is rangeseekbarview's max scrolled position
      Log.i("minValue", "-----minValue----->>>>>>" + minValue);
      Log.i("maxValue", "-----maxValue----->>>>>>" + maxValue);
      mLeftProgressPos = minValue + scrollPos;
      mRedProgressBarPos = mLeftProgressPos;
      mRightProgressPos = maxValue + scrollPos;
      Log.i("scrollPos", "scrollPos in milliseconds is" + scrollPos);
      Log.d(TAG, "-----mLeftProgressPos----->>>>>>" + mLeftProgressPos);
      Log.d(TAG, "-----mRightProgressPos----->>>>>>" + mRightProgressPos);
      switch (action) {
        case MotionEvent.ACTION_DOWN:
          isSeeking = false;
          break;
        case MotionEvent.ACTION_MOVE:
          isSeeking = true;
          seekTo((int) (pressedThumb == RangeSeekBarView.Thumb.MIN ? mLeftProgressPos : mRightProgressPos));
          break;
        case MotionEvent.ACTION_UP:
          isSeeking = false;
          seekTo((int) mLeftProgressPos);
          break;
        default:
          break;
      }

      mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos);
    }
  };

  private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
      super.onScrollStateChanged(recyclerView, newState);
      Log.d(TAG, "newState = " + newState);
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
      super.onScrolled(recyclerView, dx, dy);
      isSeeking = false;
      int scrollX = calcScrollXDistance();
      Log.i("scrollX ", "scrollX  = " + scrollX );
      //达不到滑动的距离
      if (Math.abs(lastScrollX - scrollX) < mScaledTouchSlop) {
        isOverScaledTouchSlop = false;
        return;
      }
      isOverScaledTouchSlop = true;
      //初始状态,why ? 因为默认的时候有35dp的空白！
      if (scrollX == -RECYCLER_VIEW_PADDING) {
        //public static final int RECYCLER_VIEW_PADDING = UnitConverter.dpToPx(35);
        scrollPos = 0;
      } else {
        isSeeking = true;
        scrollPos = (long) (mAverageMsPx * (RECYCLER_VIEW_PADDING + scrollX) / THUMB_WIDTH);

        //public static final int RECYCLER_VIEW_PADDING = UnitConverter.dpToPx(35);
        Log.i(" scrollPos ", " scrollPos  = " +  scrollPos );

        Log.i(" THUMB_WIDTH ", " THUMB_WIDTH  = " +  THUMB_WIDTH );
      //  public static final int THUMB_WIDTH = (SCREEN_WIDTH_FULL - RECYCLER_VIEW_PADDING * 2) / VIDEO_MAX_TIME;
        mLeftProgressPos = mRangeSeekBarView.getSelectedMinValue() + scrollPos;
        mRightProgressPos = mRangeSeekBarView.getSelectedMaxValue() + scrollPos;
        Log.i("mRangeSeekBarView.getSelectedMaxValue() ", " mRangeSeekBarView.getSelectedMaxValue() = " + mRangeSeekBarView.getSelectedMaxValue() );
        //called only when the recyclerview is scrolled
        // not when the right range seek bar is moved
        Log.i(" mRightProgressPos ", " mRightProgressPos in milliseconds = " +  mRightProgressPos );
        Log.d(TAG, "onScrolled >>>> mLeftProgressPos = " + mLeftProgressPos);
        mRedProgressBarPos = mLeftProgressPos;
        if (mVideoView.isPlaying()) {
          mVideoView.pause();
          setPlayPauseViewIcon(false);
        }
        mRedProgressIcon.setVisibility(GONE);
        seekTo(mLeftProgressPos);
        mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos);
        mRangeSeekBarView.invalidate();
      }
      lastScrollX = scrollX;
    }
  };

  /**
   * 水平滑动了多少px
   */
  @SuppressLint("LongLogTag")
  private int calcScrollXDistance() {
    LinearLayoutManager layoutManager = (LinearLayoutManager) mVideoThumbRecyclerView.getLayoutManager();
    int position = layoutManager.findFirstVisibleItemPosition();
    View firstVisibleChildView = layoutManager.findViewByPosition(position);
    int itemWidth = firstVisibleChildView.getWidth();
    Log.i("findFirstVisibleItemPosition", " layoutManager.findFirstVisibleItemPosition() = " + position);
    Log.i("itemWidth", " firstVisibleChildView.getWidth() = " + itemWidth);
    Log.i("firstVisibleChildView.getLeft()", " firstVisibleChildView.getLeft() = " + firstVisibleChildView.getLeft());


    return (position) * itemWidth - firstVisibleChildView.getLeft();
  }

  private void playingRedProgressAnimation() {
    pauseRedProgressAnimation();
    playingAnimation();
    mAnimationHandler.post(mAnimationRunnable);
  }

  private void playingAnimation() {
    if (mRedProgressIcon.getVisibility() == View.GONE) {
      mRedProgressIcon.setVisibility(View.VISIBLE);
    }
    final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mRedProgressIcon.getLayoutParams();
    int start = (int) (RECYCLER_VIEW_PADDING + (mRedProgressBarPos - scrollPos) * averagePxMs);
    int end = (int) (RECYCLER_VIEW_PADDING + (mRightProgressPos - scrollPos) * averagePxMs);
    mRedProgressAnimator = ValueAnimator.ofInt(start, end).setDuration((mRightProgressPos - scrollPos) - (mRedProgressBarPos - scrollPos));
    mRedProgressAnimator.setInterpolator(new LinearInterpolator());
    mRedProgressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override public void onAnimationUpdate(ValueAnimator animation) {
        params.leftMargin = (int) animation.getAnimatedValue();
        mRedProgressIcon.setLayoutParams(params);
        Log.i("mRedProgressBarPos", "mRedProgressBarPos in milliseconds ( only on pressing play after pause = " + mRedProgressBarPos);

        Log.d(TAG, "----onAnimationUpdate--->>>>>>>" + mRedProgressBarPos);
      }
    });
    mRedProgressAnimator.start();
  }

  private void pauseRedProgressAnimation() {
    mRedProgressIcon.clearAnimation();
    if (mRedProgressAnimator != null && mRedProgressAnimator.isRunning()) {
      mAnimationHandler.removeCallbacks(mAnimationRunnable);
      mRedProgressAnimator.cancel();
    }
  }

  private Runnable mAnimationRunnable = new Runnable() {

    @Override public void run() {
      updateVideoProgress();
    }
  };

  private void updateVideoProgress() {
    long currentPosition = mVideoView.getCurrentPosition();
    Log.d(TAG, "updateVideoProgress currentPosition = " + currentPosition);
    if (currentPosition >= (mRightProgressPos)) {
      mRedProgressBarPos = mLeftProgressPos;
      pauseRedProgressAnimation();
      onVideoPause();
    } else {
      mAnimationHandler.post(mAnimationRunnable);
    }
  }

  /**
   * Cancel trim thread execut action when finish
   */
  @Override public void onDestroy() {
    BackgroundExecutor.cancelAll("", true);
    UiThreadExecutor.cancelAll("");
  }
}
