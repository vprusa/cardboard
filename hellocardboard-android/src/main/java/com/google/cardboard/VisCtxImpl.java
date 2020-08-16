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
public class VisCtxImpl extends VisualizationContextAbsImpl<VisCtxImpl.My3DObjectWithProps, VisCtxImpl.MyQuaternion> {

//    object Object3DWithProps {
//      implicit def toObject3D(ext: Object3DWithProps): Object3D = ext.o3D
//      implicit def toPropsJsTrait(ext: Object3DWithProps): PropsJsTrait = ext.props
//    }

//    @ScalaJSDefined
//    class Object3DWithProps(_props: PropsJsTrait) extends scalajs.js.Object {
//      var o3D: Object3D = new Object3D()
//      var props: PropsJsTrait = _props
//    }

  class MyQuaternion /*extends Quaternion*/ {
    float data[] = new float[]{0.0f,0.0f,0.0f,0.0f};
  }
  class My3DObjectWithProps /*extends Quaternion*/ {
    Line l;
    public My3DObjectWithProps() {}
    public My3DObjectWithProps(My3DObjectWithProps o) {
      float[] cds = o.l.LineCoords;
      this.l.setVerts(cds[0],cds[1],cds[2],cds[3],cds[4],cds[5]);
    }
    public My3DObjectWithProps add(My3DObjectWithProps o){
      return o;
    }
  }

  private final VrActivity vrActivity;
  private final VrActivity.YAGLRenderer yaglRenderer;
  List<Line> al = new ArrayList<Line>();

  public VisCtxImpl(VrActivity vrActivity, VrActivity.YAGLRenderer yaglRenderer) {
    this.vrActivity = vrActivity;
    this.yaglRenderer = yaglRenderer;
  }

  @Override
  public Option<VisCtxImpl.My3DObjectWithProps> _add(VisCtxImpl.My3DObjectWithProps o, float v, float v1, float v2) {
    VisCtxImpl.My3DObjectWithProps o2 = new VisCtxImpl.My3DObjectWithProps(o);
    o.add(o2);
    return Option.apply(o2);
//        return null;
  }
  @Override
  public VisCtxImpl.My3DObjectWithProps _point(float v, float v1, float v2, Option<VisCtxImpl.My3DObjectWithProps> option) {
    if(option.isEmpty()) {
      VisCtxImpl.My3DObjectWithProps o = option.get();
      o.l.setColor(1.0f,1.0f,1.0f,1.0f);
      o.l.setVerts(v,v1,v2,v,v1,v2 + 0.5f);
      return o;
    }
    return null;
  }
  @Override
  public VisCtxImpl.My3DObjectWithProps _line(float v, float v1, float v2, float v3, float v4, float v5, Option<VisCtxImpl.My3DObjectWithProps> option) {
    if(option.isEmpty()) {
      VisCtxImpl.My3DObjectWithProps o = option.get();
      o.l.setColor(1.0f,1.0f,1.0f,1.0f);
      o.l.setVerts(v,v1,v2,v3,v4,v5);
      return o;
    }
    return null;
  }
  @Override
  public void _rotateGeoms(VisCtxImpl.MyQuaternion myQuaternion, Option<VisCtxImpl.My3DObjectWithProps> option) {
    if(!option.isEmpty()){
//          option.get().rotate(myQuaternion);
    }
  }
  @Override
  public void _rotateGeoms(float v, Option<VisCtxImpl.My3DObjectWithProps> option, Axisable axisable) {
    if(Axis.X() == axisable) {}
  }
}
