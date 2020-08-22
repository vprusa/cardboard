/*
 * Copyright (c) 2020  <a href="mailto:prusa.vojtech@gmail.com">Vojtech Prusa</a>
 */

package com.google.cardboard;

public class My3DObjectWithProps extends Line {

  public My3DObjectWithProps(String name) {
    super(name);
  }

  public My3DObjectWithProps(My3DObjectWithProps o) {
    this("unknown");
    if(o!=null){
      name = o.name;
      color = o.color;
    }

  }

}
