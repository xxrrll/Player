package com.baidu.player;

import com.baidu.cyberplayer.core.BVideoView;
import com.baidu.cyberplayer.core.BVideoView.OnCompletionListener;
import com.baidu.cyberplayer.core.BVideoView.OnCompletionWithParamListener;
import com.baidu.cyberplayer.core.BVideoView.OnErrorListener;
import com.baidu.cyberplayer.core.BVideoView.OnInfoListener;
import com.baidu.cyberplayer.core.BVideoView.OnPlayingBufferCacheListener;
import com.baidu.cyberplayer.core.BVideoView.OnPreparedListener;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class VideoPlayActivity extends Activity implements OnPreparedListener,
        OnCompletionListener,
        OnErrorListener,
        OnInfoListener,
        OnPlayingBufferCacheListener,
        OnCompletionWithParamListener {

    private final String TAG = "VideoPlayActivity";
    private static final int MESSAGE_SHOW_PROGRESS = 1;
    private static final int MESSAGE_FADE_OUT = 2;
    private static final int MESSAGE_SEEK_NEW_POSITION = 3;
    private static final int MESSAGE_HIDE_CENTER_BOX = 4;
    private static final int MESSAGE_RESTART_PLAY = 5;

    private String mVideoSource = null;
    private BVideoView mVV = null;
    private RelativeLayout mViewHolder = null;

    private Query $;
    private boolean isShowing = false;
    private float brightness = -1;
    private int volume = -1;
    private int newPosition = -1;
    private boolean isLive = false;//是否为直播
    private int screenWidthPixels;
    private AudioManager audioManager;
    private int mMaxVolume;
    private boolean isDragging;
    private int defaultTimeout = 2000;
    private boolean fullScreenOnly = false;
    private SeekBar seekBar;
    private int duration;
    private boolean instantSeeking = false;
    private boolean portrait;

    private WakeLock mWakeLock = null;

    private View mRoot;

    private int STATUS_ERROR = -1;
    private int STATUS_IDLE = 0;
    private int STATUS_LOADING = 1;
    private int STATUS_PLAYING = 2;
    private int STATUS_PAUSE = 3;
    private int STATUS_COMPLETED = 4;
    private int status = STATUS_IDLE;

    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.app_video_fullscreen) {
                if (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                updateFullScreenButton();
            } else if (v.getId() == R.id.app_video_play) {
                doPauseResume();
                show(defaultTimeout);
            } else if (v.getId() == R.id.app_video_replay_icon) {
                mVV.seekTo(0);
                mVV.start();
                doPauseResume();
            }
        }
    };
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser)
                return;
            int newPosition = (int) ((duration * progress * 1.0) / 100);
            String time = generateTime(newPosition);
            if (instantSeeking) {
                mVV.seekTo(newPosition);
            }
            $.id(R.id.app_video_currentTime).text(time);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isDragging = true;
            show(3600000);
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (instantSeeking) {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (!instantSeeking) {
                mVV.seekTo((int) ((duration * seekBar.getProgress() * 1.0) / 100));
            }
            show(defaultTimeout);
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            isDragging = false;
            handler.sendEmptyMessageDelayed(MESSAGE_SHOW_PROGRESS, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_video);

        mVideoSource = getIntent().getStringExtra("url");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mViewHolder = (RelativeLayout) findViewById(R.id.view_holder);

        BVideoView.setAK("4795f6d00635461ebfe00288c4a645bb");
        mVV = new BVideoView(this);
        mViewHolder.addView(mVV);

        mVV.setOnPreparedListener(this);
        mVV.setOnCompletionListener(this);
        mVV.setOnCompletionWithParamListener(this);
        mVV.setOnErrorListener(this);
        mVV.setOnInfoListener(this);
        mVV.setOnPlayingBufferCacheListener(this);

        mVV.setDecodeMode(BVideoView.DECODE_SW);
        mVV.showCacheInfo(true);
        mVV.setVideoPath(mVideoSource);

        $ = new Query(this);
        seekBar = (SeekBar) findViewById(R.id.app_video_seekBar);
        seekBar.setOnSeekBarChangeListener(mSeekListener);

        $.id(R.id.app_video_play).clicked(onClickListener);
        $.id(R.id.app_video_fullscreen).clicked(onClickListener);
        $.id(R.id.app_video_replay_icon).clicked(onClickListener);

        portrait = getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        screenWidthPixels = getResources().getDisplayMetrics().widthPixels;

        onPrepared();
    }

    @Override
    public boolean onInfo(int what, int extra) {
        switch (what) {
            /**
             * 开始缓冲
             */
            case BVideoView.MEDIA_INFO_BUFFERING_START:
                System.out.println("caching start,now playing url : " + mVV.getCurrentPlayingUrl());

                break;
            /**
             * 结束缓冲
             */
            case BVideoView.MEDIA_INFO_BUFFERING_END:
                System.out.println("caching end,now playing url : " + mVV.getCurrentPlayingUrl());

                break;
            default:
                break;
        }
        return false;
    }

    /**
     * 当前缓冲的百分比， 可以配合onInfo中的开始缓冲和结束缓冲来显示百分比到界面
     */
    @Override
    public void onPlayingBufferCache(int percent) {
        seekBar.setSecondaryProgress(percent);
    }

    /**
     * 播放出错
     */
    @Override
    public boolean onError(int what, int extra) {
        return true;
    }

    /**
     * 播放完成
     */
    @Override
    public void onCompletion() {
        finish();
    }

    /**
     * 播放准备就绪
     */
    @Override
    public void onPrepared() {
        duration = mVV.getDuration();

        final GestureDetector gestureDetector = new GestureDetector(this, new PlayerGestureListener());
        mRoot = findViewById(R.id.touch_area);
        mRoot.setClickable(true);
        mRoot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (gestureDetector.onTouchEvent(motionEvent))
                    return true;

                // 处理手势结束
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                        endGesture();
                        break;
                }

                return false;
            }
        });

        mVV.start();
    }

    @Override
    public void OnCompletionWithParam(int param) {

    }

    private void doPauseResume() {
        if (status == STATUS_COMPLETED) {
            $.id(R.id.app_video_replay).gone();
            mVV.seekTo(0);
            mVV.start();
        } else if (mVV.isPlaying()) {
            statusChange(STATUS_PAUSE);
            mVV.pause();
        } else {
            mVV.resume();
        }
        updatePausePlay();
    }

    private void updateFullScreenButton() {
        if (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            $.id(R.id.app_video_fullscreen).image(R.drawable.ic_fullscreen_exit_white_36dp);
        } else {
            $.id(R.id.app_video_fullscreen).image(R.drawable.ic_fullscreen_white_24dp);
        }
    }

    private void statusChange(int newStatus) {
        status = newStatus;
        if (!isLive && newStatus == STATUS_COMPLETED) {
            hideAll();
            $.id(R.id.app_video_replay).visible();
        } else if (newStatus == STATUS_ERROR) {
            hideAll();
        } else if (newStatus == STATUS_LOADING) {
            hideAll();
            $.id(R.id.app_video_loading).visible();
        } else if (newStatus == STATUS_PLAYING) {
            hideAll();
        }

    }

    private void hideAll() {
        $.id(R.id.app_video_replay).gone();
        $.id(R.id.app_video_loading).gone();
        $.id(R.id.app_video_fullscreen).invisible();
        showBottomControl(false);
    }

    public void hide(boolean force) {
        if (force || isShowing) {
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            showBottomControl(false);
            $.id(R.id.app_video_fullscreen).invisible();
            isShowing = false;
        }
    }

    /**
     * @param timeout
     */
    public void show(int timeout) {
        if (!isShowing) {
            if (!isLive) {
                showBottomControl(true);
            }
            if (!fullScreenOnly) {
                $.id(R.id.app_video_fullscreen).visible();
            }
            isShowing = true;
        }
        updatePausePlay();
        handler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
        handler.removeMessages(MESSAGE_FADE_OUT);
        if (timeout != 0) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_FADE_OUT), timeout);
        }
    }

    private void showBottomControl(boolean show) {
        $.id(R.id.app_video_play).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_currentTime).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_endTime).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_seekBar).visibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {
        if (volume == -1) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volume < 0)
                volume = 0;
        }
        hide(true);

        int index = (int) (percent * mMaxVolume) + volume;
        if (index > mMaxVolume)
            index = mMaxVolume;
        else if (index < 0)
            index = 0;

        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

        // 变更进度条
        int i = (int) (index * 1.0 / mMaxVolume * 100);
        String s = i + "%";
        if (i == 0) {
            s = "";
        }
        // 显示
        $.id(R.id.app_video_volume_icon).image(i == 0 ? R.drawable.ic_volume_off_white_36dp : R.drawable
                .ic_volume_up_white_36dp);
        $.id(R.id.app_video_brightness_box).gone();
        $.id(R.id.app_video_volume_box).visible();
        $.id(R.id.app_video_volume_box).visible();
        $.id(R.id.app_video_volume).text(s).visible();
    }

    /**
     * 滑动改变亮度
     */
    private void onBrightnessSlide(float percent) {
        if (brightness < 0) {
            brightness = getWindow().getAttributes().screenBrightness;
            if (brightness <= 0.00f) {
                brightness = 0.50f;
            } else if (brightness < 0.01f) {
                brightness = 0.01f;
            }
        }
        $.id(R.id.app_video_brightness_box).visible();
        WindowManager.LayoutParams lpa = getWindow().getAttributes();
        lpa.screenBrightness = brightness + percent;
        if (lpa.screenBrightness > 1.0f) {
            lpa.screenBrightness = 1.0f;
        } else if (lpa.screenBrightness < 0.01f) {
            lpa.screenBrightness = 0.01f;
        }
        $.id(R.id.app_video_brightness).text(((int) (lpa.screenBrightness * 100)) + "%");
        getWindow().setAttributes(lpa);

    }

    private int setProgress() {
        if (isDragging) {
            return 0;
        }

        int position = mVV.getCurrentPosition();
        int duration = mVV.getDuration();
        if (seekBar != null) {
            if (duration > 0) {
                seekBar.setProgress((position * 100 / duration));
            }
        }

        this.duration = duration;

        $.id(R.id.app_video_currentTime).text(generateTime(position));
        $.id(R.id.app_video_endTime).text(generateTime(this.duration));
        return position;
    }

    private String generateTime(int time) {
        int totalSeconds = time;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d",
                minutes, seconds);
    }

    private void onProgressSlide(float percent) {
        int position = mVV.getCurrentPosition();
        int duration = mVV.getDuration();
        int deltaMax = Math.min(200, duration - position);
        int delta = (int) (deltaMax * percent);

        newPosition = delta + position;
        if (newPosition > duration) {
            newPosition = duration;
        } else if (newPosition <= 0) {
            newPosition = 0;
            delta = -position;
        }
        int showDelta = delta;
        if (showDelta != 0) {
            $.id(R.id.app_video_fastForward_box).visible();
            String text = showDelta > 0 ? ("+" + showDelta) : "" + showDelta;
            $.id(R.id.app_video_fastForward).text(text + "s");
            $.id(R.id.app_video_fastForward_target).text(generateTime(newPosition) + "/");
            $.id(R.id.app_video_fastForward_all).text(generateTime(duration));
        }
    }

    private void updatePausePlay() {
        if (mVV.isPlaying()) {
            $.id(R.id.app_video_play).image(R.drawable.ic_stop_white_24dp);
        } else {
            $.id(R.id.app_video_play).image(R.drawable.ic_play_arrow_white_24dp);
        }
    }

    /**
     * 手势结束
     */
    private void endGesture() {
        volume = -1;
        brightness = -1f;
        if (newPosition >= 0) {
            handler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
            handler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
        }
        handler.removeMessages(MESSAGE_HIDE_CENTER_BOX);
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_CENTER_BOX, 500);

    }

    private int getScreenOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_FADE_OUT:
                    hide(false);
                    break;
                case MESSAGE_HIDE_CENTER_BOX:
                    $.id(R.id.app_video_volume_box).gone();
                    $.id(R.id.app_video_brightness_box).gone();
                    $.id(R.id.app_video_fastForward_box).gone();
                    break;
                case MESSAGE_SEEK_NEW_POSITION:
                    if (!isLive && newPosition >= 0) {
                        mVV.seekTo(newPosition);
                        newPosition = -1;
                        if (!mVV.isPlaying()) {
                            doPauseResume();
                        }
                    }
                    break;
                case MESSAGE_SHOW_PROGRESS:
                    int pos = setProgress();
                    if (!isDragging && isShowing) {
                        msg = obtainMessage(MESSAGE_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1 - pos);
                        updatePausePlay();
                    }
                    break;
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (mVV.isPlaying()) {
            mVV.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (null != mWakeLock && (!mWakeLock.isHeld())) {
            mWakeLock.acquire();
        }
        if (!mVV.isPlaying()) {
            mVV.resume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mVV.isPlaying()) {
            mVV.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mVV.stopPlayback();
    }

    class Query {
        private final Activity activity;
        private View view;

        public Query(Activity activity) {
            this.activity = activity;
        }

        public Query id(int id) {
            view = activity.findViewById(id);
            return this;
        }

        public Query image(int resId) {
            if (view instanceof ImageView) {
                ((ImageView) view).setImageResource(resId);
            }
            return this;
        }

        public Query visible() {
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
            return this;
        }

        public Query gone() {
            if (view != null) {
                view.setVisibility(View.GONE);
            }
            return this;
        }

        public Query invisible() {
            if (view != null) {
                view.setVisibility(View.INVISIBLE);
            }
            return this;
        }

        public Query clicked(View.OnClickListener handler) {
            if (view != null) {
                view.setOnClickListener(handler);
            }
            return this;
        }

        public Query text(CharSequence text) {
            if (view != null && view instanceof TextView) {
                ((TextView) view).setText(text);
            }
            return this;
        }

        public Query visibility(int visible) {
            if (view != null) {
                view.setVisibility(visible);
            }
            return this;
        }

        private void size(boolean width, int n, boolean dip) {
            if (view != null) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (n > 0 && dip) {
                    n = dip2pixel(activity, n);
                }
                if (width) {
                    lp.width = n;
                } else {
                    lp.height = n;
                }
                view.setLayoutParams(lp);
            }
        }

        public void height(int height, boolean dip) {
            size(false, height, dip);
        }

        public int dip2pixel(Context context, float n) {
            int value = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, context.getResources()
                    .getDisplayMetrics());
            return value;
        }

        public float pixel2dip(Context context, float n) {
            Resources resources = context.getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float dp = n / (metrics.densityDpi / 160f);
            return dp;

        }
    }

    public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
        private boolean firstTouch;
        private boolean volumeControl;
        private boolean toSeek;

        /**
         * 双击
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
//            videoView.toggleAspectRatio();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            firstTouch = true;
            return super.onDown(e);

        }

        /**
         * 滑动
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            float mOldX = e1.getX(), mOldY = e1.getY();
            float deltaY = mOldY - e2.getY();
            float deltaX = mOldX - e2.getX();
            if (firstTouch) {
                toSeek = Math.abs(distanceX) >= Math.abs(distanceY);
                volumeControl = mOldX > screenWidthPixels * 0.5f;
                firstTouch = false;
            }

            if (toSeek) {
                if (!isLive) {
                    onProgressSlide(-deltaX / mRoot.getWidth());
                }
            } else {
                float percent = deltaY / mRoot.getHeight();
                if (volumeControl) {
                    onVolumeSlide(percent);
                } else {
                    onBrightnessSlide(percent);
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (isShowing) {
                hide(false);
            } else {
                show(defaultTimeout);
            }
            return true;
        }
    }
}