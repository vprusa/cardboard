/*
 * Copyright (c) 2020  <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 */

package com.google.cardboard;

import cz.muni.fi.gag.web.scala.shared.common.Axis;
import cz.muni.fi.gag.web.scala.shared.common.Quaternion;

/**
 * TODO too many methods
 *  split into multiple?
 *   parent, parent:line, parent:dot
 *
 * */
public interface My3DObjectWithProps {
  
  void setVerts(float v0, float v1, float v2, float v3, float v4, float v5);

  void setColor(float red, float green, float blue, float alpha);

  float[] getColor();

  String getName();

  float[] getMvpMatrix();

  void setMvpMatrix(float[] mvpMatrix);

  void draw();

  void propagateMatrixes();

  void rotate(final float angle, final Axis.AxisableVal axis);

  void rotate(final Quaternion q);

  void add(My3DObjectWithProps dst);

  void rotate(final float angle, final Axis.AxisableVal axis, final boolean relative, final boolean resetRotation);

  void setRotationMatrix(float[] clone);

  float[] getLineCoords();

  void setParent(My3DObjectWithProps parent);

  float[] getRotationMatrix();

}
