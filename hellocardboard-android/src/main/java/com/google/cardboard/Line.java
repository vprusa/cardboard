package com.google.cardboard;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import cz.muni.fi.gag.web.scala.shared.common.Axis;

/**
 * Copyright (c) 2020 <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 */
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
  String name;

  public Line(final String name) {
    this.name = name;
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
    int vertexShader = VrActivity.loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
    int fragmentShader = VrActivity.loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

    GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
    GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
    GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
    GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
  }

  public void setVerts(float v0, float v1, float v2, float v3, float v4, float v5) {
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

  public void setColor(float red, float green, float blue, float alpha) {
    color[0] = red;
    color[1] = green;
    color[2] = blue;
    color[3] = alpha;
  }

  public float[] getMvpMatrix() {
    return mvpMatrix;
  }

  public void setMvpMatrix(float[] mvpMatrix) {
    this.mvpMatrix = mvpMatrix;
  }

  float[] mvpMatrix;

  public void draw() {
    // first apply rotation
    Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix.clone(), 0, rotationMatrix, 0);

    for (Line l : childern) {
      l.draw();
    }
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

  float[] rotationMatrix = new float[16];

  // https://developer.android.com/training/graphics/opengl/motion
  public void rotate(float angle, Axis.AxisableVal axis) {
    rotate(angle, axis, true);
  }

  public void rotate(float angle, Axis.AxisableVal axis, boolean relative) {

    long time = SystemClock.uptimeMillis() % 4000L;
//        float angle = 0.090f * ((int) time);
//        float angle = 0.90f;
    float angDir = (angle >= 0.0f ? 1.0f : -1.0f);
    angle = 0.090f * ((int) time) * angDir;
    float angAbs = Math.abs(angle);

    if (this.parent != null) {
//      rotationMatrix = parent.rotationMatrix.clone();
      rotationMatrix = parent.rotationMatrix.clone();
//      Matrix.setIdentityM(rotationMatrix, 0);
    } else {
      Matrix.setIdentityM(rotationMatrix, 0);
    }
    if(relative) {
      float[] c = LineCoords;

      Matrix.translateM(rotationMatrix, 0, c[0], c[1], c[2]);
      if (Axis.X().equals(axis)) {
        Matrix.rotateM(rotationMatrix, 0, angAbs, angDir, .0f, 0.f);
      } else if (Axis.Y().equals(axis)) {
        Matrix.rotateM(rotationMatrix, 0, angAbs, .0f, angDir, 0.f);
      } else if (Axis.Z().equals(axis)) {
        Matrix.rotateM(rotationMatrix, 0, angAbs, .0f, 0.f, angDir);
      }
      Matrix.translateM(rotationMatrix, 0, -c[0], -c[1], -c[2]);
    }

    for (Line l : childern) {
      l.rotate(angle, axis, false);
    }
  }

  List<Line> childern = new ArrayList<Line>();
  Line parent;

  public void add(Line dst) {
    float[] o = dst.LineCoords;
    float[] c = this.LineCoords;
    dst.setVerts(o[0] + c[3], o[1] + c[4], o[2] + c[5], o[3] + c[3], o[4] + c[4], o[5] + c[5]);
    childern.add(dst);
    dst.parent = this;
  }
}
