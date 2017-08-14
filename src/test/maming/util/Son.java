package maming.util;

public class Son extends Father{

  
  public static void main(String[] args) {
    Son s = new Son();
    Class c = s.getClass();
    System.out.println(c.getSuperclass());
  }
}
