package org.apache.coyote.http11;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class Test {

	public void test1(){
		//http://www.51testing.com/html/72/390472-233985.html?aa=bb
		Socket s;
		try {
			s = new Socket("www.51testing.com", 80);
			InputStream ins = s.getInputStream();  
			OutputStream os = s.getOutputStream();  
			os.write("GET /html/72/390472-233985.html?aa=bb HTTP/1.1\r\n".getBytes());  
			os.write("Host:www.51testing.com\r\n".getBytes());  
			//必须加host，否则服务器会返回bad request,无论是tomcat还是resin，还是nginx或者apache httpd  
			os.write("\r\n\r\n".getBytes());//这个也必不可少，http协议规定的  
			os.flush();  
			BufferedReader br = new BufferedReader(new InputStreamReader(ins,"utf-8"));  
			String line = null;  
			line = br.readLine();  
			while(line != null){  
			    System.out.println(line);  
			    line = br.readLine();  
			}  
			ins.close();  
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		
	}
	
	public static void main(String[] args) {
		Test test = new Test();
		test.test1();
	}
}
