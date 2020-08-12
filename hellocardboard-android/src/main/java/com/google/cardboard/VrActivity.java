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
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.TimeUtils;

import android.renderscript.Matrix4f;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cz.muni.fi.gag.web.scala.shared.Hand;
import cz.muni.fi.gag.web.scala.shared.common.Axis;
import cz.muni.fi.gag.web.scala.shared.common.Axisable;
import cz.muni.fi.gag.web.scala.shared.common.VisualizationContextAbsImpl;
import cz.muni.fi.gag.web.scala.shared.visualization.HandVisualization;
import scala.Enumeration;
import scala.Option;

/**
 * A Google Cardboard VR NDK sample application.
 *
 * <p>This is the main Activity for the sample application. It initializes a GLSurfaceView to allow
 * rendering.
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
//  private MyGLRenderer renderer;

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

    // https://stackoverflow.com/questions/16027455/what-is-the-easiest-way-to-draw-line-using-opengl-es-android
    public class Line {
      private FloatBuffer VertexBuffer;

      private final String VertexShaderCode =
          // This matrix member variable provides a hook to manipulate
          // the coordinates of the objects that use this vertex shader
          "uniform mat4 uMVPMatrix;" +

              "attribute vec4 vPosition;" +
              "void main() {" +
              // the matrix must be included as a modifier of gl_Position
              "  gl_Position = uMVPMatrix * vPosition;" +
              "}";

      private final String FragmentShaderCode =
          "precision mediump float;" +
              "uniform vec4 vColor;" +
              "void main() {" +
              "  gl_FragColor = vColor;" +
              "}";

      protected int GlProgram;
      protected int PositionHandle;
      protected int ColorHandle;
      protected int MVPMatrixHandle;

      // number of coordinates per vertex in this array
      static final int COORDS_PER_VERTEX = 3;
      float LineCoords[] = {
          0.0f, 0.0f, 0.0f,
          1.0f, 0.0f, 0.0f
      };

      private final int VertexCount = LineCoords.length / COORDS_PER_VERTEX;
      private final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

      // Set color with red, green, blue and alpha (opacity) values
      float color[] = {0.0f, 0.0f, 0.0f, 1.0f};

      public Line() {
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
            // (number of coordinate values * 4 bytes per float)
            LineCoords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        VertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        VertexBuffer.put(LineCoords);
        // set the buffer to read the first coordinate
        VertexBuffer.position(0);

//        LoadGLShader
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

        GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
      }

      public void SetVerts(float v0, float v1, float v2, float v3, float v4, float v5) {
        LineCoords[0] = v0;
        LineCoords[1] = v1;
        LineCoords[2] = v2;
        LineCoords[3] = v3;
        LineCoords[4] = v4;
        LineCoords[5] = v5;

        VertexBuffer.put(LineCoords);
        // set the buffer to read the first coordinate
        VertexBuffer.position(0);
      }

      public void SetColor(float red, float green, float blue, float alpha) {
        color[0] = red;
        color[1] = green;
        color[2] = blue;
        color[3] = alpha;
      }

      public void draw(float[] mvpMatrix) {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(GlProgram);
        // get handle to vertex shader's vPosition member
        PositionHandle = GLES20.glGetAttribLocation(GlProgram, "vPosition");
        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(PositionHandle);
        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(PositionHandle, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, VertexStride, VertexBuffer);
        // get handle to fragment shader's vColor member
        ColorHandle = GLES20.glGetUniformLocation(GlProgram, "vColor");
        // Set color for drawing the triangle
        GLES20.glUniform4fv(ColorHandle, 1, color, 0);
        // get handle to shape's transformation matrix
        MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");
//        ArRenderer.checkGlError("glGetUniformLocation");
        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);
//        ArRenderer.checkGlError("glUniformMatrix4fv");
        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, VertexCount);
        // Disable vertex array
        GLES20.glDisableVertexAttribArray(PositionHandle);
      }
    }

    class MyQuaternion /*extends Quaternion*/ {
      float data[] = new float[]{0.0f,0.0f,0.0f,0.0f};
    }
    class My3DObjectWithProps /*extends Quaternion*/ {
      Line l;
      public My3DObjectWithProps() {}
      public My3DObjectWithProps(My3DObjectWithProps o) {
        float[] cds = o.l.LineCoords;
        this.l.SetVerts(cds[0],cds[1],cds[2],cds[3],cds[4],cds[5]);
      }
      public My3DObjectWithProps add(My3DObjectWithProps o){
        return o;
      }
    }

//    object Object3DWithProps {
//      implicit def toObject3D(ext: Object3DWithProps): Object3D = ext.o3D
//      implicit def toPropsJsTrait(ext: Object3DWithProps): PropsJsTrait = ext.props
//    }

//    @ScalaJSDefined
//    class Object3DWithProps(_props: PropsJsTrait) extends scalajs.js.Object {
//      var o3D: Object3D = new Object3D()
//      var props: PropsJsTrait = _props
//    }

    class VisCtxImpl extends VisualizationContextAbsImpl<My3DObjectWithProps, MyQuaternion> {
      List<Line> al = new ArrayList<Line>();
//      @Override
//      public void _rotateGeoms(float f, Option<My3DObjectWithProps> o, Enumeration.Value v) {}
      @Override
      public Option<My3DObjectWithProps> _add(My3DObjectWithProps o, float v, float v1, float v2) {
//        o.l.SetColor(1.0f,1.0f,1.0f,1.0f);
//        o.l.SetVerts(v,v1,v2,v,v1,v2 + 0.5f);
//        o.l.SetVerts(v,v1,v2,v3,v4,v5);
        My3DObjectWithProps o2 = new My3DObjectWithProps(o);
        o.add(o2);
        return Option.apply(o2);
//        return null;
      }
      @Override
      public My3DObjectWithProps _point(float v, float v1, float v2, Option<My3DObjectWithProps> option) {
        if(option.isEmpty()) {
          My3DObjectWithProps o = option.get();
          o.l.SetColor(1.0f,1.0f,1.0f,1.0f);
          o.l.SetVerts(v,v1,v2,v,v1,v2 + 0.5f);
//          o.l.SetVerts(v,v1,v2,v3,v4,v5);
          return o;
        }
        return null;
      }
      @Override
      public My3DObjectWithProps _line(float v, float v1, float v2, float v3, float v4, float v5, Option<My3DObjectWithProps> option) {
        if(option.isEmpty()) {
          My3DObjectWithProps o = option.get();
          o.l.SetColor(1.0f,1.0f,1.0f,1.0f);
          o.l.SetVerts(v,v1,v2,v3,v4,v5);
          return o;
        }
        return null;
      }
      @Override
      public void _rotateGeoms(MyQuaternion myQuaternion, Option<My3DObjectWithProps> option) {
        if(option.isEmpty()){
//          option.get().rotate(myQuaternion);
        }
      }

      @Override
      public void _rotateGeoms(float v, Option<My3DObjectWithProps> option, Axisable axisable) {
        if(Axis.X() == axisable) {}
      }

    }

    private Line l;
    private Line l2;
    private HandVisualization hv;

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      nativeOnSurfaceCreated(nativeApp);

      l = new Line();
      l.SetVerts(0.5f, 0.5f, 0.5f, 4.0f, 1.0f, 2.0f);
      l.SetColor(1.0f, .0f, .3f, 1.0f);

      l2 = new Line();
      l2.SetVerts(-8f, -8f, 2.0f, 4.0f, -8.0f, 2.0f);
      l2.SetColor(1.0f, .0f, .3f, 1.0f);

      // Value hi, VisualizationContextT<GeomType, QuaternionType> ap
      Enumeration.Value h = Hand.RIGHT();
      VisCtxImpl vci = new VisCtxImpl();
      hv = new HandVisualization(h, vci);
    }
    private float[] rotationMatrix = new float[16];

    public void onDrawFrame(GL10 unused) {
      nativeOnDrawFrame(nativeApp);
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
        l.draw(modelview_projection_target_.getArray());

        // https://developer.android.com/training/graphics/opengl/motion
        long time = SystemClock.uptimeMillis() % 4000L;
        float angle = 0.090f * ((int) time);
        Matrix.setRotateM(rotationMatrix, 0, angle, -1.0f, 0, 0f);
        float[] scratch = new float[16];

        // Combine the rotation matrix with the projection and camera view
        // Note that the vPMatrix factor *must be first* in order
        // for the matrix multiplication product to be correct.
        Matrix.multiplyMM(scratch, 0, modelview_projection_target_.getArray(), 0, rotationMatrix, 0);

//        l2.draw(modelview_projection_target_.getArray());
        l2.draw(scratch);
//        My3DObjectWithProps o = new My3DObjectWithProps();
//        hv.draw();
//        hv.drawWholeHand(o);
      }

//      CHECKGLERROR("onDrawFrame");
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
