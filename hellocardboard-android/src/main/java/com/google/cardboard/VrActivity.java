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

import android.renderscript.Matrix4f;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.gatt.callback.BleCallback;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cz.muni.fi.gag.web.scala.shared.Hand;
import cz.muni.fi.gag.web.scala.shared.common.Axis;
import cz.muni.fi.gag.web.scala.shared.common.Quaternion;
import cz.muni.fi.gag.web.scala.shared.visualization.HandVisualization;
import scala.Array;
import scala.collection.mutable.ArrayBuffer;

/**
 * A Google Cardboard VR NDK sample application.
 *
 * <p>This is the main Activity for the sample application. It initializes a GLSurfaceView to allow
 * rendering.
 *
 * @changed <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
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

  private BleManager manager;

  // TODO deal with this on ESP32's side
  //  Its like the knowledge after the first semester on FIT and still I am too lazy
  //  to think it on my own when there is a solution on the internet
  // https://www.rgagnon.com/javadetails/java-0026.html
  public static class UnsignedByte {
    public static void main(String args[]) {
      byte b1 = 127;
      byte b2 = -128;
      byte b3 = -1;

      System.out.println(b1);
      System.out.println(b2);
      System.out.println(b3);
      System.out.println(unsignedByteToInt(b1));
      System.out.println(unsignedByteToInt(b2));
      System.out.println(unsignedByteToInt(b3));
    /*
    127
    -128
    -1
    127
    128
    255
    */
    }

    public static int unsignedByteToInt(byte b) {
      return (int) b & 0xFF;
    }

  }

  // https://github.com/Ficat/EasyBle
  private void initBleManager() {
    // check if this android device supports ble
    if (!BleManager.supportBle(this)) {
      return;
    }
    // open bluetooth without a request dialog
    BleManager.toggleBluetooth(true);

    BleManager.ScanOptions scanOptions = BleManager.ScanOptions
        .newInstance()
        .scanPeriod(8000)
        .scanDeviceName(null);

    BleManager.ConnectOptions connectOptions = BleManager.ConnectOptions
        .newInstance()
        .connectTimeout(6000);

    manager = BleManager
        .getInstance()
        .setScanOptions(scanOptions)
        .setConnectionOptions(connectOptions)
        .setLog(true, "TAG")
        .init(this.getApplication());

    // connect

// #define SERVICE_UUID           "6e400001-b5a3-f393-e0a9-e50e24dcca9e" // UART service UUID
// #define CHARACTERISTIC_UUID_RX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
// #define CHARACTERISTIC_UUID_TX "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

// #define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" // UART service UUID
// #define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
// #define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // this one

    String serviceUuid = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    String writeUuid = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    String notifyUuid = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
//    String address = "30:AE:A4:FE:53:A6";
    String address = "30:AE:A4:FF:1B:A2";

    BleConnectCallback bleConnectCallback = new BleConnectCallback() {
      @Override
      public void onStart(boolean startConnectSuccess, String info, BleDevice device) {
        if (startConnectSuccess) {
          //start to connect successfully
        } else {
          //fail to start connection, see details from 'info'
          String failReason = info;
        }
      }

      @Override
      public void onFailure(int failCode, String info, BleDevice device) {
        if (failCode == BleConnectCallback.FAIL_CONNECT_TIMEOUT) {
          //connection timeout
        } else {
          //connection fail due to other reasons
        }
      }

      @Override
      public void onConnected(BleDevice device) {

//    manager.notify(bleDevice, serviceUuid, notifyUuid, new BleNotifyCallback() {
        manager.notify(device, serviceUuid, notifyUuid, new BleNotifyCallback() {
          /*
#ifdef SEND_ACC
    #define PACKET_LENGTH 21
    #define PACKET_COUNTER_POSITION 18
#else
    #define PACKET_LENGTH 15
    #define PACKET_COUNTER_POSITION 10
#endif
          uint8_t dataPacket[PACKET_LENGTH] = {'*', 0x99, 0,  0, 0,  0, 0,  0, 0,  0, 0,
#ifdef SEND_ACC
            0, 0, 0, 0, 0, 0,
#endif
            0x00, 0x00, '\r', '\n'};
#endif

          fifoBuffer[1] = qI[0] & 0xFF;
          fifoBuffer[0] = qI[0]>> 8;
          fifoBuffer[5] = qI[1] & 0xFF;
          fifoBuffer[4] = qI[1]>> 8;
          fifoBuffer[9] = qI[2] & 0xFF;
          fifoBuffer[8] = qI[2] >> 8;
          fifoBuffer[13] = qI[3] & 0xFF;
          fifoBuffer[12] = qI[3] >> 8;

void fifoToPacket(byte * fifoBuffer, byte * packet, int selectedSensor) {
    //DEBUG_PRINTLN("fifoToPacket");
    packet[2] = selectedSensor;
    packet[3] = fifoBuffer[0];
    packet[4] = fifoBuffer[1];
    packet[5] = fifoBuffer[4];
    packet[6] = fifoBuffer[5];
    packet[7] = fifoBuffer[8];
    packet[8] = fifoBuffer[9];
    packet[9] = fifoBuffer[12];
    packet[10] = fifoBuffer[13];

        uint8_t *fifoBuffer = gyros[selectedSensor].fifoBuffer; // FIFO storage buffer
}
       */

//          public float getF(byte b0, byte b1) {
          public float getF(byte b0, byte b1) {
//            fifoBuffer[1] = qI[0] & 0xFF;
//            fifoBuffer[0] = qI[0]>> 8;
            // TODO
            short sn = getS(b0, b1);
            return (float) (((int) sn) / 16384.0);
//            return (float) (((int)sn)/32768.0);
          }

          public short getS(byte b0, byte b1) {
            return (short) ((((short) b0) << 8) + ((short) b1));
          }

          public int getS2(byte b0, byte b1) {
            return (((int) new Short((short) ((((short) b0) << 8) + ((short) b1))).intValue()) << 1);
          }

          @Override
          public void onCharacteristicChanged(byte[] d, BleDevice dev) {
            byte p = d[2];
            /*
            if (p == 5) {
              String us = "us: " + getS2(d[3], d[4]) + " " + getS2(d[5], d[6]) + " " + getS2(d[7], d[8]) + " " + getS2(d[9], d[10]) + " ";
//            float q1 = getF(d[4], d[3]);
              float w = getF(d[3], d[4]);
              float x = getF(d[5], d[6]);
              float y = getF(d[7], d[8]);
              float z = getF(d[9], d[10]);
              String s = "wxyz: " + " " + w + " " + x + " " + y + " " + z;
              Log.i("INFO", us);
              Log.i("INFO", s);
              Quaternion q = new Quaternion(w, x, y, z);
//            q = q.normalize();
              setHvQ(q,p);
            }
             */
            String us = "us: " + getS2(d[3], d[4]) + " " + getS2(d[5], d[6]) + " " + getS2(d[7], d[8]) + " " + getS2(d[9], d[10]) + " ";
//            float q1 = getF(d[4], d[3]);
            float w = getF(d[3], d[4]);
            float x = getF(d[5], d[6]);
            float y = getF(d[7], d[8]);
            float z = getF(d[9], d[10]);
            String s = "wxyz: " + " " + w + " " + x + " " + y + " " + z;
            Log.i("INFO", us);
            Log.i("INFO", s);
            Quaternion q = new Quaternion(w, x, y, z);
//            q = q.normalize();
            setHvQ(q,p);
//            hv.rotate(q.getYaw(), q.getPitch(), q.getRoll());
//            hv.littleVis().rotate(q.getYaw(), q.getPitch(), q.getRoll());
//            hv.rotate();
          }
          /*
          @Override
          public void onCharacteristicChanged(byte[] d, BleDevice dev) {
            int c = d[1],
                x = UnsignedByte.unsignedByteToInt(d[2]),
                y = UnsignedByte.unsignedByteToInt(d[3]),
                z = UnsignedByte.unsignedByteToInt(d[4]),
                b1 = d[5],
                b2 = d[6];
            String t = "rec notif data: " + "l: " + d.length + " C " + c + " X " + x + " Y " + y + " Z " + z;
            //      eye_offsets[0] += 0.001;
            System.out.println(t);
            if (x == 255) {
              head_offset[0] -= 0.05;
            } else if (x == 0) {
              head_offset[0] += 0.05;
            }
            if (y == 255) {
              head_offset[2] -= 0.05;
            } else if (y == 0) {
              head_offset[2] += 0.05;
            }

//            TextView txtMtu = findViewById(R.id.txt_notify);
//            txtMtu.setText(t);
          }
           */

          @Override
          public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {
          }

          @Override
          public void onFailure(int failCode, String info, BleDevice device) {
            switch (failCode) {
              case BleCallback.FAIL_DISCONNECTED://connection has disconnected
                break;
              case BleCallback.FAIL_OTHER://other reason
                break;
              default:
                break;
            }

          }
        });

        /*
        String data = "test";
        manager.write(device, serviceUuid, writeUuid, data.getBytes(), new BleWriteCallback() {
          @Override
          public void onWriteSuccess(byte[] data, BleDevice device) {
          }

          @Override
          public void onFailure(int failCode, String info, BleDevice device) {
          }
        });
         */
      }


      @Override
      public void onDisconnected(String info, int status, BleDevice device) {
        Log.i("DISC", info + "; status: " + status);
      }

    };

//    manager.connect(bleDevice, bleConnectCallback);
    // connect with specified connectOptions
//    manager.connect(bleDevice, connectOptions, bleConnectCallback);

    // connect with mac address
//    manager.connect(address, bleConnectCallback);
//    manager.connect(address, connectOptions, bleConnectCallback);

    Log.i("BLE", "manager.connect");
    if (manager.isConnected(address)) {
      manager.disconnect(address);
    }
    manager.connect(address, connectOptions, bleConnectCallback);
    Log.i("BLE", "manager.connected");

  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onCreate(Bundle savedInstance) {
    super.onCreate(savedInstance);
    initBleManager();

    nativeApp = nativeOnCreate(getAssets());

    setContentView(R.layout.activity_vr);
    glView = findViewById(R.id.surface_view);
    glView.setEGLContextClientVersion(2);

    // Create an OpenGL ES 2.0 context
//    setEGLContextClientVersion(2);

    YAGLRenderer renderer = new YAGLRenderer();
    // Set the Renderer for drawing on the GLSurfaceView
    glView.setRenderer(renderer);

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

  public volatile float[] head_offset = new float[]{.0f, .0f, .0f};
  public volatile HandVisualization hv;

  public volatile Quaternion[] hvQ = new Quaternion[6];

  public synchronized Quaternion getHvQ(byte i) {
    return hvQ[i];
  }

  public synchronized void setHvQ(Quaternion hvQ, byte i) {
    this.hvQ[i] = hvQ;
  }

  // YetAnotherGLRenderer
  public class YAGLRenderer implements GLSurfaceView.Renderer {

    private Line l;
    private Line l2;
    private Line l3;
    private Line l4;
    private Rectangle r1;
    private My3DObjectWithProps o;
    private Quaternion eq;

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      nativeOnSurfaceCreated(nativeApp);

      l = new Line("1");
//      l.SetVerts(0, 0f, 2.0f, 0f, -8f, 2.0f);
      l.setVerts(-5f, -10.0f, 0f, -5f, -10.0f, -2f);
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

      l4 = new Line("4");
      l4.setVerts(0f, 0f, .0f, .0f, .0f, -2.0f);
//      l3.setVerts(0f, 0f, 0.0f, 2.0f, .0f, -2.0f);
      l4.setColor(1.0f, .5f, 1.f, 1.0f);
      l3.add(l4);

      o = new Line("t1");
      hv = new HandVisualization(Hand.RIGHT(), new VisCtxImpl());

      // TODO scale kind of messes up verts location ...
//      o.setVerts(0, -10.0f, 0f, 0f, -10.0f, -0f);
      o.setVerts(300f, -10.0f, 0f, 300f, -10.0f, 0f);
      o.setColor(.0f, .9f, .0f, 1.0f);
      hv.drawWholeHand(o);
    }


    public void drawEyesView() {
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
//        Matrix4f eye_matrix = new Matrix4f(eye_matrix_def.getArray());
//        eye_matrix.translate(eye_offsets[0], eye_offsets[1], eye_offsets[2]);

        Matrix4f eye_view = new Matrix4f(eye_matrix.getArray());
        Matrix4f head_view = new Matrix4f(head_view_);
        head_view.translate(head_offset[0], head_offset[1], head_offset[2]);
        eye_view.multiply(head_view);

        float[] projection_matrices_ = getNativeMatrix(nativeApp, (eye == 0 ?
            MatrixId.m2x16_projection_matrices_left.ordinal() :
            MatrixId.m2x16_projection_matrices_right.ordinal()
        ));

        Matrix4f projection_matrix = new Matrix4f(projection_matrices_);
//        Matrix4x4 modelview_target = eye_view * model_target_;
        Matrix4f modelview_target = new Matrix4f(eye_view.getArray());

        Matrix4f modelview_projection_target_ = new Matrix4f(projection_matrix.getArray());
        modelview_projection_target_.multiply(modelview_target);

        drawEye(modelview_projection_target_);
      }
      // TODO add glcheckerror
//      CHECKGLERROR("onDrawFrame");
    }

    public void drawEye(Matrix4f modelview_projection_target_) {

      // Set mvpMatrices to Line
      l4.setMvpMatrix(modelview_projection_target_.getArray().clone());
      l3.setMvpMatrix(modelview_projection_target_.getArray().clone());
      l2.setMvpMatrix(modelview_projection_target_.getArray().clone());
      l.setMvpMatrix(modelview_projection_target_.getArray().clone());
      o.setMvpMatrix(modelview_projection_target_.getArray().clone());

      float angle = 0.09f * ((int) SystemClock.uptimeMillis() % 4000L);
//      float angle = 30;

      // Rotate
      l.rotate(angle, Axis.Y());
      l2.rotate(angle, Axis.Y());
      l3.rotate(angle, Axis.Y());
      l4.rotate(angle, Axis.Y());

      // Draw shape
      l.draw();

      float scale = 0.002f;
      Matrix.scaleM(o.getMvpMatrix(), 0, scale, scale, scale);
      o.propagateMatrixes();

      // TODO is there a bug when using not using 4000.f,
      //  i may not understand smth crucial here and now im too lazy to deal with that..
      long time = SystemClock.uptimeMillis() % 3600;
      float angle2 = 4000.f / (((int) time));
//      eq = new Quaternion(1.0f, 0.0f, 0.0f, angle2);
//      o.rotate(eq);
//      synchronized (hvQ){
      if (hvQ != null) {

        eq = getHvQ((byte) 5);
        eq = eq.multiply(new Quaternion(1.0f, 0.0f, 0.0f, 3.f));
//        o.rotate(getHvQ((byte) 5));
//        o.rotate(0.3f, Axis.Y());
        o.rotate(eq);
        hv.thumbVis().rotate(getHvQ((byte) 4));
        hv.indexVis().rotate(getHvQ((byte) 3));
        hv.middleVis().rotate(getHvQ((byte) 2));
        hv.ringVis().rotate(getHvQ((byte) 1));
        hv.littleVis().rotate(getHvQ((byte) 0));
//        o.rotate(getHvQ((byte) 5));
      } else {
        eq = new Quaternion(1.0f, 0.0f, 0.0f, 0.5f);
        o.rotate(eq);
      }

//      o.rotate((float) Math.PI/2, Axis.Y());
//      hv.littleVis().rotateZ(30);
      // right hand
//      hv.rotateY(90);
//      hv.rotateY(180);
//      hv.rotateZ(90);

      o.draw();
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
