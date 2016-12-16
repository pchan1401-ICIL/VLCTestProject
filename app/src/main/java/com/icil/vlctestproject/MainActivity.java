package com.icil.vlctestproject;

import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements IVLCVout.Callback, LibVLC.HardwareAccelerationError, TextureView.SurfaceTextureListener {

    //기본 위젯 설정및 변수 설정
    private LibVLC libvlc;

    private MediaPlayer mMediaPlayer = null;

    public String mFilePath = "/sdcard/practice/video.mp4";

    private int mVideoWidth;
    private int mVideoHeight;


    private SeekBar mSeekBar;
    private TextureView mTexture;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button mStartStopBtn = (Button) findViewById(R.id.stopbtn);
        mStartStopBtn.setOnClickListener(
                new Button.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mMediaPlayer == null) {
                            createPlayer(mFilePath);
                            mSeekBar.setMax((int) mMediaPlayer.getLength());
                        } else {
                            int state = mMediaPlayer.getPlayerState();
                            switch (state) {
                                case 4:  // stopping
                                case 3:  // playing
                                    mMediaPlayer.pause();
                            }
                        }
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},0);
        }

        mSeekBar = (SeekBar)findViewById(R.id.seekBar);
        mSeekBar.setMax(0);
        mSeekBar.setOnSeekBarChangeListener(mSeekListener);

        //Video on View
        mTexture = (TextureView) findViewById(R.id.surface);
        mTexture.setSurfaceTextureListener(this);
    }

    @Override //VLC 레이아웃 설정
    public void onNewLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width * height == 0)
            return;
        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override
    public void onSurfacesCreated(IVLCVout ivlcVout) {

    }

    @Override
    public void onSurfacesDestroyed(IVLCVout ivlcVout) {

    }

    @Override  //하드웨어 가속 에러시 플레이어 종료
    public void eventHardwareAccelerationError() {
        releasePlayer();
        Toast.makeText(this, "Error with hardware acceleration", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

    }

    @Override  //SurfaceTexture 화면(동영상 해상도 및 사이즈)에 따라 크기 변경
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override  //SurfaceTexture 화면이 종료되었을때 종료
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if(!(mMediaPlayer == null))
            mMediaPlayer.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    //미디어 플레이어 리스너 클래스
    private static class MyPlayerListener implements MediaPlayer.EventListener {
        private WeakReference<MainActivity> mOwner;

        //액티비티 변수를 받아오기 위하여 지정
        private MyPlayerListener(MainActivity owner) {
            mOwner = new WeakReference<>(owner);
        }

        @Override
        public void onEvent(MediaPlayer.Event event) {
            MainActivity player = mOwner.get();

            switch(event.type) {
                case MediaPlayer.Event.EndReached:
                    player.releasePlayer();
                    break;
                case MediaPlayer.Event.Playing:
                    player.mSeekBar.setMax((int) player.mMediaPlayer.getLength());
                    player.mSeekBar.setOnSeekBarChangeListener(player.mSeekListener);
                    break;
                case MediaPlayer.Event.Paused:
                    break;
                case MediaPlayer.Event.Stopped:
                    break;
                case MediaPlayer.Event.PositionChanged:
                    player.mSeekBar.setProgress((int)player.mMediaPlayer.getTime());
                default:
                    break;
            }
        }
    }

    private void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1)
            return;

        if(mTexture == null)
            return;

        //화면사이즈
        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();

        //Orientation 계산
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        ViewGroup.LayoutParams lp = mTexture.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mTexture.setLayoutParams(lp);
        mTexture.invalidate();
    }

    //VLC 플레이어 실행
    private void createPlayer(String media) {
        releasePlayer();
        try {
            if (media.length() > 0) {
                Toast toast = Toast.makeText(this, media, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create LibVLC
            ArrayList<String> options = new ArrayList<>();
            //options.add("--subsdec-encoding <encoding>");
            options.add("--aout=opensles");
            options.add("--audio-time-stretch"); // time stretching
            options.add("-vvv"); // verbosity
            libvlc = new LibVLC(options);
            libvlc.setOnHardwareAccelerationError(this);

            mTexture.setKeepScreenOn(true);

            // Create media player
            mMediaPlayer = new MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(mPlayerListener);

            // Set up video output
            final IVLCVout vout = mMediaPlayer.getVLCVout();
            vout.setVideoView(mTexture);
            //vout.setSubtitlesView(mSurfaceSubtitles);
            vout.addCallback(this);
            vout.attachViews();

            Media m = new Media(libvlc, media);
            mMediaPlayer.setMedia(m);
            mMediaPlayer.play();

        } catch (Exception e) {
            Toast.makeText(this, "Error creating player!", Toast.LENGTH_LONG).show();
        }
    }

    //플레이어 종료
    private void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.removeCallback(this);
        vout.detachViews();
        libvlc.release();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    private MediaPlayer.EventListener mPlayerListener = new MyPlayerListener(this);

    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser){
                mMediaPlayer.setTime(progress);
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }
}
