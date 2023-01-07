package com.iknow.android.features.select;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import com.iknow.android.R;
import com.iknow.android.databinding.ActivityVideoSelectBinding;
import com.iknow.android.features.common.ui.BaseActivity;
import com.iknow.android.features.record.VideoRecordActivity;
import com.iknow.android.features.record.view.PreviewSurfaceView;
import com.iknow.android.features.select.loader.VideoCursorLoader;
import com.iknow.android.features.select.loader.VideoLoadManager;
import com.tbruyelle.rxpermissions2.RxPermissions;
import iknow.android.utils.callback.SimpleCallback;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */
@SuppressWarnings("ResultOfMethodCallIgnored")

/// Everything starts here.                                     //onClickListener is an interface inside View that declares
                                                                      //void onClick(View var1); method
                                                                  // setOnClickListener(this)
public class VideoSelectActivity extends BaseActivity implements View.OnClickListener {

  private ActivityVideoSelectBinding mBinding;
  private VideoSelectAdapter mVideoSelectAdapter;
  private VideoLoadManager mVideoLoadManager;
  private PreviewSurfaceView mSurfaceView;
  private ViewGroup mCameraSurfaceViewLy;

  @SuppressLint("CheckResult")  //initUI is a custom method declared in java/com/iknow/android/features/common/ui/BaseActivity.java
  @Override public void initUI() {
    mVideoLoadManager = new VideoLoadManager();
    mVideoLoadManager.setLoader(new VideoCursorLoader()); // setLoader(ILoader loader)  inside the VideoLoadManager.java file
    // , VideoCursorLoader() is ILoader type  VideoCursorLoader() creates a loader and loads videofiles as cursor
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_video_select);
    mCameraSurfaceViewLy = findViewById(R.id.layout_surface_view);
    mBinding.mBtnBack.setOnClickListener(this); //setOnClickListener(this) is a method inside View.class taking a
                                                    //View.OnClickListener as argument within which onClick method is defined

    RxPermissions rxPermissions = new RxPermissions(this);
    rxPermissions.request(Manifest.permission.READ_EXTERNAL_STORAGE).subscribe(granted -> {
      if (granted) { // Always true pre-M        // what is a call back ? what does it do ?
        mVideoLoadManager.load(this, new SimpleCallback() { // calling load(final Context context, final SimpleCallback listener)
                                                                  // inside the VideoLoadManager.java file which inturns calls load method from interface
                                                                  //Iloader implemented inside com/iknow/android/features/select/loader/VideoCursorLoader.java
                                                                  // which returns a cursor which is denoted here as obj
               // annonymous implementation of iknow/android/utils/callback/SimpleCallback.java
          @Override public void success(Object obj) { // onece the loader finishes loading
                                                // mSimpleCallback.success(cursor) is called form inside
                                              //com/iknow/android/features/select/loader/VideoCursorLoader.java

            if (mVideoSelectAdapter == null) {
              mVideoSelectAdapter = new VideoSelectAdapter(VideoSelectActivity.this, (Cursor) obj);

                // VideoSelectorAdapter.java later calls VideoTrimmerActivity.call
            } else {
              mVideoSelectAdapter.swapCursor((Cursor) obj);
            }
            if (mBinding.videoGridview.getAdapter() == null) {
              mBinding.videoGridview.setAdapter(mVideoSelectAdapter);
            }
            mVideoSelectAdapter.notifyDataSetChanged();
          }
        });
      } else {
        finish();
      }
    });
    if (rxPermissions.isGranted(Manifest.permission.CAMERA)) {
      initCameraPreview();
    } else {
      mBinding.cameraPreviewLy.setVisibility(View.GONE);
      mBinding.openCameraPermissionLy.setVisibility(View.VISIBLE);
      mBinding.mOpenCameraPermission.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          rxPermissions.request(Manifest.permission.CAMERA).subscribe(granted -> {
            if (granted) {
              initCameraPreview();
            }
          });
        }
      });
    }
  }

  private void initCameraPreview() {
    mSurfaceView = new PreviewSurfaceView(this);
    mBinding.cameraPreviewLy.setVisibility(View.VISIBLE);
    mBinding.openCameraPermissionLy.setVisibility(View.GONE);
    addSurfaceView(mSurfaceView);
    mSurfaceView.startPreview();

    mBinding.cameraPreviewLy.setOnClickListener(v -> {
      mSurfaceView.release();
      VideoRecordActivity.call(this);
    });
  }

  private void hideOtherView() {
    mBinding.titleLayout.setVisibility(View.GONE);
    mBinding.videoGridview.setVisibility(View.GONE);
    mBinding.cameraPreviewLy.setVisibility(View.GONE);
  }

  private void resetHideOtherView() {
    mBinding.titleLayout.setVisibility(View.VISIBLE);
    mBinding.videoGridview.setVisibility(View.VISIBLE);
    mBinding.cameraPreviewLy.setVisibility(View.VISIBLE);
  }

  private void addSurfaceView(PreviewSurfaceView surfaceView) {
    mCameraSurfaceViewLy.addView(surfaceView);
  }

  @Override protected void onResume() {
    super.onResume();
    if (mSurfaceView != null) mSurfaceView.startPreview();
  }

  @Override protected void onPause() {
    super.onPause();
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (mSurfaceView != null) mSurfaceView.startPreview();
  }

  @Override public void onClick(View v) {
    if (v.getId() == mBinding.mBtnBack.getId()) {
      finish(); // calls finish() method of this activity resulting in closing this activity
    }
  }
}
