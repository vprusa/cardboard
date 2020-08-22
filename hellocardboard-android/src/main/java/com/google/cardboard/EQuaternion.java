/*
 * Copyright (c) 2020  <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 */

package com.google.cardboard;

import cz.muni.fi.gag.web.scala.shared.common.Quaternion;

//class MyQuaternion extends Quaternion {
public class EQuaternion extends Quaternion {
  public EQuaternion(float a, float b, float c, float d) {
    super(a, b, c, d);
    normalize();
    toEuler();
  }

  float roll, pitch, yaw;

  // TODO deal with double to float cast
  // https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles
  void toEuler() {
    // roll (x-axis rotation)
    float sinr_cosp = 2 * (getQ0() * getQ1() + getQ2() * getQ3());
    float cosr_cosp = 1 - 2 * (getQ1() * getQ1() + getQ2() * getQ2());
    this.roll = (float) Math.atan2(sinr_cosp, cosr_cosp);
    this.roll *= 360;

    // pitch (y-axis rotation)
    float sinp = 2 * (getQ0() * getQ2() - getQ3() * getQ1());
    if (Math.abs(sinp) >= 1){
      this.pitch = (float) Math.copySign(Math.PI / 2.0f, sinp); // use 90 degrees if out of range
    } else {
      this.pitch = (float) Math.asin(sinp);
    }
    this.pitch *= 360;

    // yaw (z-axis rotation)
    float siny_cosp = 2 * (getQ0() * getQ3() + getQ1() * getQ2());
    float cosy_cosp = 1 - 2 * (getQ2() * getQ2() + getQ3() * getQ3());
    this.yaw = (float) Math.atan2(siny_cosp, cosy_cosp);
    this.yaw *= 360;
//    return new float[]{roll, pitch, yaw};
  }


}
