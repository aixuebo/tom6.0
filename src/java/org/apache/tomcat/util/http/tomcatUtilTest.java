package org.apache.tomcat.util.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Locale;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;


public class tomcatUtilTest {

	public void test1(){
		System.out.println(Locale.getDefault());
	}
	
    private static final String tspecials = ",; ";
    private static final String tspecials2 = "()<>@,;:\\\"/[]?={} \t";
    private static final String tspecials2NoSlash = "()<>@,;:\\\"[]?={} \t";

    /**
     * 判断value值中是否包含特别字符，包含则返回false，不包含则返回true
     */
    public static boolean isToken(String value) {
        return isToken(value,null);
    }
    
    public static boolean isToken(String value, String literals) {
        String tspecials = (literals==null?tomcatUtilTest.tspecials:literals);
        if( value==null) return true;
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if (tspecials.indexOf(c) != -1)
                return false;
        }
        return true;
    }
    
    public static void unescapeDoubleQuotes(ByteChunk bc) {
        if (bc == null || bc.getLength() == 0 || bc.indexOf('"', 0) == -1) {
            return;
        }

        int src = bc.getStart();
        int end = bc.getEnd();
        int dest = src;
        byte[] buffer = bc.getBuffer();
        
        while (src < end) {
            if (buffer[src] == '\\' && src < end && buffer[src+1]  == '"') {
                src++;
            }
            buffer[dest] = buffer[src];
            dest ++;
            src ++;
        }
        bc.setEnd(dest);
    }
    
    
    private static String escapeDoubleQuotes(String s, int beginIndex, int endIndex) {

        if (s == null || s.length() == 0 || s.indexOf('"') == -1) {
            return s;
        }

        StringBuffer b = new StringBuffer();
        for (int i = beginIndex; i < endIndex; i++) {
            char c = s.charAt(i);
            if (c == '\\' ) {
                b.append(c);
                //ignore the character after an escape, just append it
                if (++i>=endIndex) throw new IllegalArgumentException("Invalid escape character in cookie value.");
                b.append(s.charAt(i));
            } else if (c == '"')
                b.append('\\').append('"');
            else
                b.append(c);
        }

        return b.toString();
    }
    
    public void test4(){
		ByteChunk bc = new ByteChunk(2);
		bc.setLimit(5);
    	String str = "马明哈哈的顶顶顶顶顶顶顶顶顶顶";
		byte[] b;
		try {
			b = str.getBytes("gbk");
			System.out.println(b.length);
			bc.setBytes(b,0,b.length);
			bc.setCharset(Charset.forName("gbk"));
			System.out.println("-------");
			System.out.println(bc.getLimit());
			System.out.println(bc.getStart());
			System.out.println(bc.getEnd());
			System.out.println("-----------");
			System.out.println(bc.toString());
			System.out.println(Charset.defaultCharset());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public void test5(){
    	ByteChunk bc = new ByteChunk(2);
		bc.setLimit(5);
    	String str = "\"dadba\"";
		byte[] b;
		try {
			b = str.getBytes("gbk");
			bc.setBytes(b,0,b.length);
			bc.setCharset(Charset.forName("gbk"));
			
			unescapeDoubleQuotes(bc);
			
			System.out.println(bc.toString());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
    }
    /**
     * 
     */
    private static final int slashCount() {
    	String name = "/data/ecom/project/a/";
        int pos = -1;
        int count = 0;
        while ((pos = name.indexOf('/', pos + 1)) != -1) {
            count++;
        }
        System.out.println(count);
        return count;
    }
    
	public static void main(String[] args) {
		tomcatUtilTest test = new tomcatUtilTest();
		System.out.println(0x0D);
		System.out.println(0x0A);
		System.out.println(0x2D);
	}
}

