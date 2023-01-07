package com.iknow.android.features.common.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * author : J.Chou
 * e-mail : who_know_me@163.com
 * time   : 2019/02/22 4:38 PM
 * version: 1.0
 * description:模板设计模式：
 * 定义算法骨架，将一些步骤延时到子类，可定义钩子函数。
 */
@SuppressLint("Registered")
public abstract class BaseActivity extends AppCompatActivity {

  protected abstract void initUI(); // abstract method hence not implemented here
  protected void loadData() {
  }
// protected modifier to be assessed from subclasses
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      render(); // calls the initUI(); , loadData(); below.

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
// private modifier unable to asses from any class
  private void render() {
    initUI();
    loadData();
  }
}
