/*
 * Copyright (c) 2020  <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 */

package com.google.cardboard;

import java.util.ArrayList;
import java.util.List;

import cz.muni.fi.gag.web.scala.shared.common.Axis;
import cz.muni.fi.gag.web.scala.shared.common.Axisable;
import cz.muni.fi.gag.web.scala.shared.common.VisualizationContextAbsImpl;
import scala.Option;

/**
 * Copyright (c) 2020 <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 */
public class VisCtxImpl extends VisualizationContextAbsImpl<My3DObjectWithProps, EQuaternion> {

//    object Object3DWithProps {
//      implicit def toObject3D(ext: Object3DWithProps): Object3D = ext.o3D
//      implicit def toPropsJsTrait(ext: Object3DWithProps): PropsJsTrait = ext.props
//    }

//    @ScalaJSDefined
//    class Object3DWithProps(_props: PropsJsTrait) extends scalajs.js.Object {
//      var o3D: Object3D = new Object3D()
//      var props: PropsJsTrait = _props
//    }

  private final VrActivity vrActivity;
  private final VrActivity.YAGLRenderer yaglRenderer;
  List<Line> al = new ArrayList<Line>();

  public VisCtxImpl(VrActivity vrActivity, VrActivity.YAGLRenderer yaglRenderer) {
    this.vrActivity = vrActivity;
    this.yaglRenderer = yaglRenderer;
  }

  @Override
  public Option<My3DObjectWithProps> _add(My3DObjectWithProps o, float v, float v1, float v2) {
    My3DObjectWithProps o2 = new My3DObjectWithProps(o);
    o2.setVerts(v,v1,v2,v,v1,v2 + 0.1f);
//    o2.mvpMatrix = o.mvpMatrix.clone();
    o.add(o2);
//    o.add(o2);
    return Option.apply(o2);
//        return null;
  }
  @Override
  public My3DObjectWithProps _point(float v, float v1, float v2, Option<My3DObjectWithProps> option) {
    My3DObjectWithProps no = null;
    if(!option.isEmpty()) {
      My3DObjectWithProps o = option.get();
      //      return o;
      no = new My3DObjectWithProps(o);
      no.setColor(.0f,1.0f,.0f,1.0f);
//      no.setVerts(v,v1,v2,v,v1,v2 + 0.5f);
      no.setVerts(v,v1,v2,v,v1,v2 + 0.1f);
//      no.mvpMatrix = o.mvpMatrix.clone();
      o.add(no);
    }
    return no;
  }
  @Override
  public My3DObjectWithProps _line(float v, float v1, float v2, float v3, float v4, float v5, Option<My3DObjectWithProps> option) {
    My3DObjectWithProps no = null;
    if(!option.isEmpty()) {
      My3DObjectWithProps o = option.get();
      //      return o;
      no = new My3DObjectWithProps(o);
      no.setColor(.0f,1.0f,.0f,1.0f);
//      no.setVerts(v,v1,v2,v3,v4,v5);
      no.setVerts(v,v1,v2,v3,v4,v5);
//      no.mvpMatrix = o.mvpMatrix.clone();
      o.add(no);
    }
    return no;
//    return null;
  }
  @Override
  public void _rotateGeoms(EQuaternion myQuaternion, Option<My3DObjectWithProps> option) {
    if(!option.isEmpty()){
//          option.get().rotate(myQuaternion);
      My3DObjectWithProps o = option.get();
      o.rotate(myQuaternion);
    }
  }
  @Override
  public void _rotateGeoms(float v, Option<My3DObjectWithProps> option, Axis.AxisableVal axis) {
    if(option.nonEmpty()) {
      My3DObjectWithProps o = option.get();
//      o.rotate(v, axisable);
      o.rotate(v, axis, true, false);
    }
  }
}
