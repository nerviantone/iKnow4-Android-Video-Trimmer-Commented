package com.iknow.android.features.select.loader;

import android.content.Context;
import com.iknow.android.features.select.loader.ILoader;
import iknow.android.utils.callback.SimpleCallback;

/**
 * author : J.Chou
 * e-mail : who_know_me@163.com
 * time   : 2018/10/04 1:50 PM
 * version: 1.0
 * description:
 */
public class VideoLoadManager {

  private ILoader mLoader;  // Creates a variable of interface Iloader

  public void setLoader(ILoader loader) {
    this.mLoader = loader; // Loaded to
  } // setLoader method of VideoLoadManager

  public void load(final Context context, final SimpleCallback listener) {
    mLoader.load(context, listener); // Calls the load method of ? ILoader implemented inside com/iknow/android/features/select/loader/VideoCursorLoader.java
  }
}
