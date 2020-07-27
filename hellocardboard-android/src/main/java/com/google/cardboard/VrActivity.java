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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

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

    MyGLRenderer renderer = new MyGLRenderer();
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


//    glView = new MyGLSurfaceView(this);
//    setContentView(glView);

  }


  class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer renderer;

    public MyGLSurfaceView(Context context){
      super(context);

      // Create an OpenGL ES 2.0 context
      setEGLContextClientVersion(2);

      renderer = new MyGLRenderer();

      // Set the Renderer for drawing on the GLSurfaceView
      setRenderer(renderer);
    }
  }

  public static int loadShader(int type, String shaderCode){

    // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
    // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
    int shader = GLES20.glCreateShader(type);

    // add the source code to the shader and compile it
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);

    return shader;
  }


  public class MyGLRenderer implements GLSurfaceView.Renderer {

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
      float color[] = { 0.0f, 0.0f, 0.0f, 1.0f };

      // https://community.khronos.org/t/android-sdk-eclipse-opengl-es-2-0-shader-question/3096
      private int loadShader(int shaderType, String source) {
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
//        GLES20.shader
//        LoadGLShader
//        int vertexShader = ArRenderer.loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
//        int fragmentShader = ArRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);
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
                GLES20.GL_FLOAT, false,
                VertexStride, VertexBuffer);

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



    private Line l;
    private Line l2;
    private Line l3;

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      nativeOnSurfaceCreated(nativeApp);

      // Set the background frame color
//      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
      l = new Line();
      l.SetVerts(0.0f,0.0f,0.0f,4.0f,1.0f,2.0f);
      l.SetColor(1.0f,.0f,.3f,1.0f);


      l2 = new Line();
      l2.SetVerts(0.0f,0.0f,0.0f,1.0f,1.0f,3.0f);
      l2.SetColor(1.0f,.2f,.8f,.5f);

      l3 = new Line();
      l3.SetVerts(0.0f,0.0f,0.0f,-2.0f,1.0f,3.0f);
      l3.SetColor(1.0f,.2f,.8f,.8f);

    }

    public void onDrawFrame(GL10 unused) {
      nativeOnDrawFrame(nativeApp);
      float [] projectionMatrix = getMyMatrix(nativeApp,0);
      int xxx = 0;
      xxx++;
      if(xxx > 0){

      }

//      projectionMatrix;
      /*
      // Redraw background color
//      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      // Set the camera position (View matrix)
      Matrix.setLookAtM(viewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
      // Calculate the projection and view transformation
      Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
      // Draw shape
      l.draw(vPMatrix);
      l2.draw(vPMatrix);
      l3.draw(vPMatrix);
      */

//      if (!UpdateDeviceParams()) {
//        return;
//      }

      // Update Head Pose.
//      head_view_ = GetPose();

      // Incorporate the floor height into the head_view
//      head_view_ = head_view_ * GetTranslationMatrix({0.0f, kDefaultFloorHeight, 0.0f});

      // Bind buffer
//      glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);

//      glEnable(GL_DEPTH_TEST);
//      glEnable(GL_CULL_FACE);
//      glDisable(GL_SCISSOR_TEST);
//      glEnable(GL_BLEND);
//      glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
//      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
      GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT);

      float eye_matrices_[][] = new float[2][16];
      float projection_matrices_[][] = new float[2][16];
      float  head_view_ar[] = new float[16];
//      Matrix.setLookAtM(viewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
      Matrix.setLookAtM(head_view_ar, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

      Matrix4f head_view_ = new Matrix4f(head_view_ar);


      // Draw eyes views
      int screen_width_ = glView.getWidth();
      int screen_height_ = glView.getHeight();
      int kLeft = 0;
      for (int eye = 0; eye < 2; ++eye) {
        GLES20.glViewport((eye == kLeft ? 0 : screen_width_ / 2), 0, (screen_width_ / 2), screen_height_);
        float eye_matrix_ar[] = { 1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0.0299999993f, 0f, 0f, 1f};
//        Matrix4f eye_matrix = GetMatrixFromGlArray(eye_matrices_[eye]);
//        Matrix4f eye_matrix = new Matrix4f(eye_matrices_[eye]);
//        Matrix4f eye_matrix = new Matrix4f(eye_matrix_ar);

//        Matrix4f eye_view = eye_matrix * head_view_;
//        Matrix4f eye_view_ = new Matrix4f(eye_matrices_[eye]);
        Matrix4f eye_view_ = new Matrix4f(eye_matrix_ar);
        eye_view_.multiply(head_view_);
//        Matrix4f eye_view = eye_matrix * head_view_;
        Matrix4f eye_view = eye_view_;

        /*
        float proj_mat_ar[][] = new float[2][]{new float[16] {
          1.21373904,0,0,0,0,1.19175363,0,0,0,0,0.0184479337,0,-1.002002,-1,0,0,-0.2002002,0
        }, new float[16]{
          1.21373904,0,0,0,0,1.19175363,-0.0184479337,0,-1.002002,-1,0,0,-0.2002002,0
        }};
         */
        float proj_mat_ar[] = { 1.21373904f,0f,0f,0f,0f,1.19175363f,0f,0f,0f,0f,0.0184479337f,0f,
                -1.002002f,-1f,0f,0f,-0.2002002f,0f};
//        Matrix4f projection_matrix = GetMatrixFromGlArray(projection_matrices_[eye]);

//        Matrix4f projection_matrix = new Matrix4f(projection_matrices_[eye]);
        Matrix4f projection_matrix = new Matrix4f(proj_mat_ar);
//        Matrix4f modelview_target = eye_view * model_target_;
        Matrix4f modelview_target = eye_view;
        projection_matrix.multiply(modelview_target);
//        Matrix4f modelview_projection_target_ = projection_matrix * modelview_target;
//        Matrix4f modelview_projection_target_ = projection_matrix;
        Matrix4f modelview_projection_target_ = new Matrix4f(new float[]{
//            1.20618272f, -0.130267218f, -0.041500695f, -0.0414177775f,
//            0.0344886705f, 0.560866952f, -0.883972883f, -0.882206678f,
//            -0.132048339f, -1.04342484f, -0.469976336f, -0.469037324f,
//            -0.287455887f, -2.68457794f, -1.22704637f, -1.02479458f
        1.20017862f, 0.178510413f, -0.0119997356f, -0.0119757606f,
        0.180390149f, -1.17160356f, 0.104924269f, 0.104714632f,
        -0.0230929889f, 0.125520974f, 0.996420919f, 0.994430065f,
        -0.0420216545f, 0.360829681f, 2.19802284f, 2.39343119f
        });
//        modelview_projection_target_ = projection_matrix * modelview_target;
//        modelview_projection_room_ = projection_matrix * eye_view;

        // Draw room and target
//        DrawWorld();

        // Set the camera position (View matrix)
//        Matrix.setLookAtM(viewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        // Calculate the projection and view transformation
//        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);


        // Draw shape
        l.draw(modelview_projection_target_.getArray());
        l2.draw(modelview_projection_target_.getArray());
        l3.draw(modelview_projection_target_.getArray());
      }

      // Render
//      CardboardDistortionRenderer_renderEyeToDisplay(
//              distortion_renderer_, /* target_display = */ 0, /* x = */ 0, /* y = */ 0,
//              screen_width_, screen_height_, &left_eye_texture_description_,
//      &right_eye_texture_description_);

//      CHECKGLERROR("onDrawFrame");



//      l.draw();
    }

    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];


    public void onSurfaceChanged(GL10 unused, int width, int height) {
      nativeSetScreenParams(nativeApp, width, height);
//      GLES20.glViewport(0, 0, width, height);
      GLES20.glViewport(0, 0, width, height);

      float ratio = (float) width / height;

      // this projection matrix is applied to object coordinates
      // in the onDrawFrame() method
      Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);

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

  private class Renderer implements GLSurfaceView.Renderer {
    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
      nativeOnSurfaceCreated(nativeApp);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
      nativeSetScreenParams(nativeApp, width, height);
      GLES20.glViewport(0, 0, width, height);

      float ratio = (float) width / height;

      // this projection matrix is applied to object coordinates
      // in the onDrawFrame() method
      Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);

    }

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
      float color[] = { 0.0f, 0.0f, 0.0f, 1.0f };

      // https://community.khronos.org/t/android-sdk-eclipse-opengl-es-2-0-shader-question/3096
      private int loadShader(int shaderType, String source) {
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
//        GLES20.shader
//        LoadGLShader
//        int vertexShader = ArRenderer.loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
//        int fragmentShader = ArRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);
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
                GLES20.GL_FLOAT, false,
                VertexStride, VertexBuffer);

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

    float g_vertex_buffer_data[] = {
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            0.0f,  1.0f, -1.0f,
    };

    /*
    public class Triangle {

      private final String vertexShaderCode =
              // This matrix member variable provides a hook to manipulate
              // the coordinates of the objects that use this vertex shader
              "uniform mat4 uMVPMatrix;" +
                      "attribute vec4 vPosition;" +
                      "void main() {" +
                      // the matrix must be included as a modifier of gl_Position
                      // Note that the uMVPMatrix factor *must be first* in order
                      // for the matrix multiplication product to be correct.
                      "  gl_Position = uMVPMatrix * vPosition;" +
                      "}";

      // Use to access and set the view transformation
      private int vPMatrixHandle;

      //    ...
      public void draw(float[] mvpMatrix) { // pass in the calculated transformation matrix
        //    ...

        // get handle to shape's transformation matrix
        vPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(positionHandle);
      }

    }
     */


//    private final float[] mMVPMatrix = new float[16];

    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];


    @Override
    public void onDrawFrame(GL10 gl) {
//      nativeOnDrawFrame(nativeApp);
//    https://developer.android.com/training/graphics/opengl/projection


//      float[] mMVPMatrix = new float[16];
//      float[] viewMatrix = new float[16];
//       getMatrix().mapPoints(mMVPMatrix);
//      glView.getMatrix().mapPoints(viewMatrix);

      Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

      // Calculate the projection and view transformation
      Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

      // Draw shape
//      triangle.draw(vPMatrix)

//      l.draw(viewMatrix);
//      l2.draw(viewMatrix);
//      l3.draw(viewMatrix);

//      float[] mMVPMatrix = new float[16];


//      gl10.glDrawArrays(g_vertex_buffer_data);


//      gl.glUniform1i(uniformDiffuse, 0);
//      gl.glUniformMatrix4fv(uniformMvp, 1, false, mvp.toFa_(), 0);

//      gl.glActiveTexture(gl.GL_TEXTURE0);
//      gl.glBindTexture(gl.GL_TEXTURE_2D, texture2dName.get(0));
//      gl.glBindTexture(gl.GL_TEXTURE_2D, 0);

//      gl.glBindVertexArray(vertexArrayName.get(0));
//      gl.verteglBindVertexArray(vertexArrayName.get(0));

//      gl.glDrawArrays(gl.GL_TRIANGLES, 0, vertexCount);

//      gl.glDrawArrays();
//      Line l = new Line();
//      l.SetVerts(0.0f,0.0f,0.0f,10.0f,1.0f,2.0f);
//      l.SetColor(1.0f,.0f,.3f,1.0f);
//      float[] mMVPMatrix = new float[16];
//       getMatrix().mapPoints(mMVPMatrix);
//      gl.
//      l.draw(mMVPMatrix);
//      gl10.glDrawArrays(g_vertex_buffer_data);


//      gl10.glVertexPointer(

//          0,                  // attribute 0. No particular reason for 0, but must match the layout in the shader.
//              gvPositionHandle,
//              3,                  // size
//              gl10.GL_FLOAT,           // type
//              gl10.GL_FALSE,           // normalized?
//              0,                  // stride
//          (void*)0            // array buffer offset
//              g_vertex_buffer_data
//      );
      // Draw the triangle !
//      gl10.glDrawArrays(gl10.GL_TRIANGLES, 0, 3); // Starting from vertex 0; 3 vertices total -> 1 triangle
//      gl10.glDrawElements();
      /*
      int _nrOfVertices = g_vertex_buffer_data.length;
      float _vertexBuffer [] =  g_vertex_buffer_data;

      ByteBuffer indicesByteBuffer = ByteBuffer.allocateDirect(_vertexBuffer.length * );
      indicesByteBuffer.order(ByteOrder.nativeOrder());
//      ShortBuffer shortBuffer = indicesByteBuffer.asShortBuffer();
//      shortBuffer.put(g_vertex_buffer_data);
//      shortBuffer.position(0);
      FloatBuffer shortBuffer = indicesByteBuffer.asShortBuffer();
      FloatBuffer.put(g_vertex_buffer_data);
      FloatBuffer.position(0);

      gl.glColor4f(0.5f, 0f, 0f, 0.5f);
      gl.glVertexPointer(3, gl.GL_FLOAT, 0, _vertexBuffer);
      gl.glDrawElements(gl.GL_TRIANGLES, _nrOfVertices, gl.GL_UNSIGNED_SHORT, _indexBuffer);
      // gl.glColor4f(0.5f, 0f, 0f, 0.5f);
      gl.glVertexPointer(3, gl.GL_FLOAT, 0, _vertexBuffer);
      gl.glColorPointer(4, gl.GL_FLOAT, 0, _colorBuffer);
      gl.glDrawElements(gl.GL_TRIANGLES, _nrOfVertices, gl.GL_UNSIGNED_SHORT, _indexBuffer);
       */
//      gl.glDrawArrays();
    }
  }

  /** Callback for when close button is pressed. */
  public void closeSample(View view) {
    Log.d(TAG, "Leaving VR sample");
    finish();
  }

  /** Callback for when settings_menu button is pressed. */
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

  /** Handles the requests for activity permissions. */
  private void requestPermissions() {
    final String[] permissions = new String[] {Manifest.permission.READ_EXTERNAL_STORAGE};
    ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
  }

  /** Callback for the result from requesting permissions. */
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

  private native float[] getMyMatrix(long nativeApp, long location);
}
