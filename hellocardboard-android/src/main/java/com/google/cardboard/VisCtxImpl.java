package com.google.cardboard;

import cz.muni.fi.gag.web.scala.shared.common.Axis;
import cz.muni.fi.gag.web.scala.shared.common.Quaternion;
import cz.muni.fi.gag.web.scala.shared.common.VisualizationContextAbsImpl;
import scala.Option;

/**
 * Copyright (c) 2020 <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 *
 * TODO functional or remote Option?
 */
public class VisCtxImpl extends VisualizationContextAbsImpl<My3DObjectWithProps, Quaternion> {

  // TODO remove? why not? smth about scala's threejs impl? Oh, man.. i forgot why is this here.
  @Override
  public Option<My3DObjectWithProps> _add(My3DObjectWithProps o, float v, float v1, float v2) {
//    My3DObjectWithProps o2 = new My3DObjectWithProps(o);
//    o2.setVerts(v, v1, v2, v, v1, v2 + 0.1f);
//    o.add(o2);
//    return Option.apply(o2);
    return Option.apply(o);
  }

  @Override
  public My3DObjectWithProps _point(float v, float v1, float v2, Option<My3DObjectWithProps> option) {
    My3DObjectWithProps no = null;
    if (!option.isEmpty()) {
      My3DObjectWithProps o = option.get();
      no = new Line(o);
      no.setColor(.0f, 1.0f, .0f, 1.0f);
      no.setVerts(v, v1, v2, v, v1, v2 + 0.1f);
      o.add(no);
    }
    return no;
  }

  @Override
  public My3DObjectWithProps _line(float v, float v1, float v2, float v3, float v4, float v5, Option<My3DObjectWithProps> option) {
    My3DObjectWithProps no = null;
    if (!option.isEmpty()) {
      My3DObjectWithProps o = option.get();
      no = new Line(o);
      no.setColor(.0f, 1.0f, .0f, 1.0f);
      no.setVerts(v, v1, v2, v3, v4, v5);
      o.add(no);
    }
    return no;
  }

  @Override
  public void _rotateGeoms(Quaternion q, Option<My3DObjectWithProps> optObj) {
    if (!optObj.isEmpty()) {
      My3DObjectWithProps o = optObj.get();
      o.rotate(q);
    }
  }

  @Override
  public void _rotateGeoms(float v, Option<My3DObjectWithProps> option, Axis.AxisableVal axis) {
    if (option.nonEmpty()) {
      My3DObjectWithProps o = option.get();
      o.rotate(v, axis, true, false);
    }
  }
}
