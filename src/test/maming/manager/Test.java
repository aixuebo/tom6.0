package maming.manager;

import java.util.LinkedList;

public class Test {

  public void test1(){
    LinkedList<String> sessionCreationTiming = new LinkedList<String>();
    sessionCreationTiming.add("aaa");
    sessionCreationTiming.add("bbb");
    sessionCreationTiming.add("ccc");
    System.out.println(sessionCreationTiming);
    sessionCreationTiming.poll();
    System.out.println(sessionCreationTiming);
  }
  
  public static void main(String[] args) {
    Test test = new Test();
    test.test1();
  }
}
