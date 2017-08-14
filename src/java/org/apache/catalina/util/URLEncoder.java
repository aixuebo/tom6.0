/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.BitSet;

/**
*
* This class is very similar to the java.net.URLEncoder class.
*
* Unfortunately, with java.net.URLEncoder there is no way to specify to the 
* java.net.URLEncoder which characters should NOT be encoded.
* 这个类类似于java.net.URLEncoder类，但不幸的是,
* java.net.URLEncoder类不能将一些特殊字符不编码
* @author Craig R. McClanahan
* @author Remy Maucherat
* 该类作用:
* 1.将给定path路径按照UTF-8进行编码。转换成字节数组。
* 2.因为每个字节占8位比特。而16进制只需要4位比特。
*  因此将每个字节拆分成前四个比特(high)和后四个比特(low).
*  将比特位对应的16进制转化了即可
*  然后前面加上%即可。即%+high+low
*  例如.字符，ascii为46.二进制为101110，high为10，转化为十进制为2，low为1110，转化为十进制为14
*  因此最终为%2e
* 3.该类的特殊作用就是可以将一些默认字符不进行过滤。默认字符为数字、字母。以及调用addSafeCharacter(':')的方法
*/
public class URLEncoder {
    protected static final char[] hexadecimal =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
     'A', 'B', 'C', 'D', 'E', 'F'};

    //Array containing the safe characters set.
    protected BitSet safeCharacters = new BitSet(256);

    public URLEncoder() {
        for (char i = 'a'; i <= 'z'; i++) {
            addSafeCharacter(i);
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            addSafeCharacter(i);
        }
        for (char i = '0'; i <= '9'; i++) {
            addSafeCharacter(i);
        }
    }

    public void addSafeCharacter( char c ) {
	safeCharacters.set( c );
    }

    public String encode( String path ) {
        int maxBytesPerChar = 10;
        int caseDiff = ('a' - 'A');
        StringBuffer rewrittenPath = new StringBuffer(path.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(buf, "UTF8");
        } catch (Exception e) {
            e.printStackTrace();
            writer = new OutputStreamWriter(buf);
        }

        for (int i = 0; i < path.length(); i++) {
            int c = (int) path.charAt(i);
            if (safeCharacters.get(c)) {
                rewrittenPath.append((char)c);
            } else {
                // convert to external encoding before hex conversion
                try {
                    writer.write((char)c);//向buffer缓冲区中添加c,其实这里面什么时候都只有一个char,因为每次处理完都清空缓冲区
                    writer.flush();
                } catch(IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (int j = 0; j < ba.length; j++) {
                    // Converting each byte in the buffer
                    byte toEncode = ba[j];
                    rewrittenPath.append('%');
                    int low = (int) (toEncode & 0x0f);//1111
                    int high = (int) ((toEncode & 0xf0) >> 4);//11110000
                    rewrittenPath.append(hexadecimal[high]);
                    rewrittenPath.append(hexadecimal[low]);
                }
                buf.reset();//重新清空buffer缓冲区
            }
        }
        return rewrittenPath.toString();
    }
    
    public static void main(String[] args) {
    	URLEncoder test = new URLEncoder();
    	test.addSafeCharacter(':');
    	test.addSafeCharacter('/');
    	String path = "http://www.baidu.com?key=马a";
    	String path1 = test.encode(path);
    	System.out.println(path1);
    	System.out.println((byte)'.');//46
    	System.out.println(java.net.URLEncoder.encode(path));
	}
}
