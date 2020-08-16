/*
 * Copyright 2019 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cardboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.renderscript.Matrix4f;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cz.muni.fi.gag.web.scala.shared.Hand;
import cz.muni.fi.gag.web.scala.shared.common.Axis;
import cz.muni.fi.gag.web.scala.shared.visualization.HandVisualization;
import scala.Enumeration;

/**
 * A Google Cardboard VR NDK sample application.
 *
 * <p>This is the main Activity for the sample application. It initializes a GLSurfaceView to allow
 * rendering.
 *
 */
public class VrActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {
  static {
    System.loadLibrary("cardboard_jni");
  }

  private static final String TAG = VrActivity.class.getSimpleName();

  // Permission request codes
  private static final int PERMISSIONS_REQUEST_CODE = 2;

  // Opaque native pointer to the native CardboardApp instance.
  // This object is owned by the VrActivity instance and passed to the native methods.
  private long nativeApp;

  private GLSurfaceView glView;

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onCreate(Bundle savedInstance) {
    super.onCreate(savedInstance);

    nativeApp = nativeOnCreate(getAssets());

    setContentView(R.layout.activity_vr);
    glView = findViewById(R.id.surface_view);
    glView.setEGLContextClientVersion(2);

    // Create an OpenGL ES 2.0 context
//    setEGLContextClientVersion(2);

    YAGLRenderer renderer = new YAGLRenderer();
    // Set the Renderer for drawing on the GLSurfaceView
    glView.setRenderer(renderer);

//    Renderer renderer = new Renderer();
//    glView.setRenderer(renderer);
    glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    glView.setOnTouchListener(
        (v, event) -> {
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Signal a trigger event.
            glView.queueEvent(
                () -> {
                  nativeOnTriggerEvent(nativeApp);
                });
            return true;
          }
          return false;
        });

    // TODO(b/139010241): Avoid that action and status bar are displayed when pressing settings
    // button.
    setImmersiveSticky();
    View decorView = getWindow().getDecorView();
    decorView.setOnSystemUiVisibilityChangeListener(
        (visibility) -> {
          if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            setImmersiveSticky();
          }
        });

    // Forces screen to max brightness.
    WindowManager.LayoutParams layout = getWindow().getAttributes();
    layout.screenBrightness = 1.f;
    getWindow().setAttributes(layout);

    // Prevents screen from dimming/locking.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  // https://community.khronos.org/t/android-sdk-eclipse-opengl-es-2-0-shader-question/3096
  public static int loadShader(int shaderType, String source) {
    int shader = GLES20.glCreateShader(shaderType);
    if (shader != 0) {
      GLES20.glShaderSource(shader, source);
      GLES20.glCompileShader(shader);

      int[] compiled = new int[1];
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
      if (compiled[0] == 0) {
        Log.e(TAG, "Could not compile shader " + shaderType + ":");
        Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
        GLES20.glDeleteShader(shader);
        shader = 0;
      }
    }
    return shader;
  }

  // TODO share this across Java and C?
  public enum MatrixId {
    //      : unsigned long
    /* ... values go here ... */
    m2x16_projection_matrices_left,
    m2x16_projection_matrices_right,
    m2x16_eye_matrices_left,
    m2x16_eye_matrices_right,
    m4x4_head_view,
    m4x4_model_target,
    m4x4_modelview_projection_target,
    m4x4_modelview_projection_room
  }

  // YetAnotherGLRenderer
  public class YAGLRenderer implements GLSurfaceView.Renderer {

    private Line l;
    private Line l2;
    private Line l3;
    private HandVisualization hv;

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      nativeOnSurfaceCreated(nativeApp);

      l = new Line("1");
//      l.SetVerts(0, 0f, 2.0f, 0f, -8f, 2.0f);
      l.setVerts(0,-10.0f, 0f,  0f, -10.0f, -2f);
      l.setColor(1.0f, .0f, .0f, 1.0f);

      l2 = new Line("2");
//      l2.setVerts(-4f, -8f, -10.0f, 4.0f, -8.0f, -10.0f);
      l2.setVerts(0f, 0f, 0.0f, 0f, .0f, -2.0f);
//      l2.SetVerts(0, 0f, 2.0f, 0f, -8f, 2.0f);
      l2.setColor(1.0f, .0f, .5f, 1.0f);
      l.add(l2);

      l3 = new Line("3");
      l3.setVerts(0f, 0f, .0f, .0f, .0f, -2.0f);
//      l3.setVerts(0f, 0f, 0.0f, 2.0f, .0f, -2.0f);
      l3.setColor(1.0f, .0f, 1.f, 1.0f);
      l2.add(l3);

      // Value hi, VisualizationContextT<GeomType, QuaternionType> ap
      Enumeration.Value h = Hand.RIGHT();
      VisCtxImpl vci = new VisCtxImpl(VrActivity.this, this);
      hv = new HandVisualization(h, vci);
    }

    public void drawEyesView(){
      GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

      // Draw eyes views
      int screen_width_ = glView.getWidth();
      int screen_height_ = glView.getHeight();
      int kLeft = 0;
      for (int eye = 0; eye < 2; ++eye) {
        GLES20.glViewport((eye == kLeft ? 0 : screen_width_ / 2), 0, (screen_width_ / 2), screen_height_);

        float[] eye_matrices_ = getNativeMatrix(nativeApp, (eye == 0 ?
            MatrixId.m2x16_eye_matrices_left.ordinal() :
            MatrixId.m2x16_eye_matrices_right.ordinal()
        ));

        float[] head_view_ = getNativeMatrix(nativeApp, MatrixId.m4x4_head_view.ordinal());

        Matrix4f eye_matrix = new Matrix4f(eye_matrices_);
        Matrix4f eye_view = new Matrix4f(eye_matrix.getArray());
        eye_view.multiply(new Matrix4f(head_view_));

        float[] projection_matrices_ = getNativeMatrix(nativeApp, (eye == 0 ?
            MatrixId.m2x16_projection_matrices_left.ordinal() :
            MatrixId.m2x16_projection_matrices_right.ordinal()
        ));

        Matrix4f projection_matrix = new Matrix4f(projection_matrices_);
//        Matrix4x4 modelview_target = eye_view * model_target_;
        Matrix4f modelview_target = new Matrix4f(eye_view.getArray());

        Matrix4f modelview_projection_target_ = new Matrix4f(projection_matrix.getArray());
        modelview_projection_target_.multiply(modelview_target);
//        modelview_projection_target_.rotate(.2f, 1.0f, 1.0f, 0.0f);

        // Draw shape
        l3.setMvpMatrix(modelview_projection_target_.getArray().clone());
        l2.setMvpMatrix(modelview_projection_target_.getArray().clone());
        l.setMvpMatrix(modelview_projection_target_.getArray().clone());

//        l2.rotate(0.9f, Axis.Y());
        l.rotate(0.9f, Axis.Y());
        l2.rotate(0.9f, Axis.Y());
        l3.rotate(0.9f, Axis.Y());
//        l2.rotate(0.9f, Axis.Y());
        l.draw();
//        My3DObjectWithProps o = new My3DObjectWithProps();
//        hv.draw();
//        hv.drawWholeHand(o);
      }

//      CHECKGLERROR("onDrawFrame");
    }

    public void onDrawFrame(GL10 unused) {
      nativeOnDrawFrame(nativeApp);
      drawEyesView();
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
      nativeSetScreenParams(nativeApp, width, height);
    }

  }

  @Override
  protected void onPause() {
    super.onPause();
    nativeOnPause(nativeApp);
    glView.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Checks for activity permissions, if not granted, requests them.
    if (!arePermissionsEnabled()) {
      requestPermissions();
      return;
    }

    glView.onResume();
    nativeOnResume(nativeApp);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    nativeOnDestroy(nativeApp);
    nativeApp = 0;
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      setImmersiveSticky();
    }
  }

  /**
   * Callback for when close button is pressed.
   */
  public void closeSample(View view) {
    Log.d(TAG, "Leaving VR sample");
    finish();
  }

  /**
   * Callback for when settings_menu button is pressed.
   */
  public void showSettings(View view) {
    PopupMenu popup = new PopupMenu(this, view);
    MenuInflater inflater = popup.getMenuInflater();
    inflater.inflate(R.menu.settings_menu, popup.getMenu());
    popup.setOnMenuItemClickListener(this);
    popup.show();
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    if (item.getItemId() == R.id.switch_viewer) {
      nativeSwitchViewer(nativeApp);
      return true;
    }
    return false;
  }

  /**
   * Checks for activity permissions.
   *
   * @return whether the permissions are already granted.
   */
  private boolean arePermissionsEnabled() {
    return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED;
  }

  /**
   * Handles the requests for activity permissions.
   */
  private void requestPermissions() {
    final String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
  }

  /**
   * Callback for the result from requesting permissions.
   */
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (!arePermissionsEnabled()) {
      Toast.makeText(this, R.string.no_permissions, Toast.LENGTH_LONG).show();
      if (!ActivityCompat.shouldShowRequestPermissionRationale(
          this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
        // Permission denied with checking "Do not ask again".
        launchPermissionsSettings();
      }
      finish();
    }
  }

  private void launchPermissionsSettings() {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.fromParts("package", getPackageName(), null));
    startActivity(intent);
  }

  private void setImmersiveSticky() {
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  private native long nativeOnCreate(AssetManager assetManager);

  private native void nativeOnDestroy(long nativeApp);

  private native void nativeOnSurfaceCreated(long nativeApp);

  private native void nativeOnDrawFrame(long nativeApp);

  private native void nativeOnTriggerEvent(long nativeApp);

  private native void nativeOnPause(long nativeApp);

  private native void nativeOnResume(long nativeApp);

  private native void nativeSetScreenParams(long nativeApp, int width, int height);

  private native void nativeSwitchViewer(long nativeApp);

  private native float[] getNativeMatrix(long nativeApp, int location);

}
