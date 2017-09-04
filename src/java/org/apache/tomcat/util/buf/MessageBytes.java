/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.buf;

import java.text.*;
import java.util.*;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * This class is used to represent a subarray of bytes in an HTTP message.
 * It represents all request/response elements. The byte/char conversions are
 * delayed and cached. Everything is recyclable.
 *
 * The object can represent a byte[], a char[], or a (sub) String. All
 * operations can be made in case sensitive mode or not.
 *
 * @author dac@eng.sun.com
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin Manolache
 * 
1.void setString( String s )  当表示String的时候
private boolean hasStrValue=true
private String strValue; 用于存储具体的值
type=T_STR;

2.void setChars( char[] c, int off, int len ) 当表示char的时候
CharChunk charC=new CharChunk(); 用于存储全部的char内容
type=T_CHARS;


3.void setBytes(byte[] b, int off, int len) 当表示byte的时候
ByteChunk byteC=new ByteChunk(); 用于存储全部的byte内容
type=T_BYTES;

该类用于存储一个字符串,使用char、byte、String三种之一存储,并且三者还可以互相转换

该类存储的如果是int、long、Date类型的值的时候,有简化方法
 */
public final class MessageBytes implements Cloneable, Serializable {
	
	//存储字符串内容,有四种方式,null,或者String、或者byte、char,这四种对象其中之一组成的
    // primary type ( whatever is set as original value )
    private int type = T_NULL;

    public static final int T_NULL = 0;//说明值是null

    //以下三个类型都是类型最终是String,只是创建MessageBytes对象的时候使用的是String还是char还是byte而已,但是代表的都是String
    /** getType() is T_STR if the the object used to create the MessageBytes
        was a String */
    public static final int T_STR  = 1;
    /** getType() is T_STR if the the object used to create the MessageBytes
        was a byte[] */ 
    public static final int T_BYTES = 2;
    /** getType() is T_STR if the the object used to create the MessageBytes
        was a char[] */ 
    public static final int T_CHARS = 3;

    private int hashCode=0;
    // did we computed the hashcode ? 
    private boolean hasHashCode=false;//表示对该内容是否计算了hash值

    // Is the represented object case sensitive ?
    private boolean caseSensitive=true;//默认true表示大小写是敏感的

    // Internal objects to represent array + offset, and specific methods
    private ByteChunk byteC=new ByteChunk();//用于存储byte的内容
    private CharChunk charC=new CharChunk();//用于存储char的内容
    
    // String
    private String strValue;//用于存储最终的字符串,或者是byteC和charC转换成的字符串
    // true if a String value was computed. Probably not needed,
    // strValue!=null is the same
    private boolean hasStrValue=false;//true表示已经转换成字符串了

    /**
     * Creates a new, uninitialized MessageBytes object.
     * @deprecated Use static newInstance() in order to allow
     *   future hooks.
     */
    public MessageBytes() {
    }

    /** Construct a new MessageBytes instance
     */
    public static MessageBytes newInstance() {
	return factory.newInstance();
    }

    /** Configure the case sensitivity
     */
    public void setCaseSenitive( boolean b ) {
	caseSensitive=b;
    }

    public MessageBytes getClone() {
	try {
	    return (MessageBytes)this.clone();
	} catch( Exception ex) {
	    return null;
	}
    }

    public boolean isNull() {
//		should we check also hasStrValue ???
		return byteC.isNull() && charC.isNull() && ! hasStrValue;
	// bytes==null && strValue==null;
    }
    
    /**
     * Resets the message bytes to an uninitialized (NULL) state.
     */
    public void recycle() {
	type=T_NULL;
	byteC.recycle();
	charC.recycle();

	strValue=null;
	caseSensitive=true;

	hasStrValue=false;
	hasHashCode=false;
	hasIntValue=false;
    hasLongValue=false;
	hasDateValue=false;	
    }


    /**
     * Sets the content to the specified subarray of bytes.
     *
     * @param b the bytes
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     */
    public void setBytes(byte[] b, int off, int len) {
        byteC.setBytes( b, off, len );
        type=T_BYTES;
        hasStrValue=false;
        hasHashCode=false;
        hasIntValue=false;
        hasLongValue=false;
        hasDateValue=false; 
    }

    /** Set the encoding. If the object was constructed from bytes[]. any
     *  previous conversion is reset.
     *  If no encoding is set, we'll use 8859-1.
     */
    public void setCharset(Charset charset) {
        if( !byteC.isNull() ) {
            // if the encoding changes we need to reset the conversion results
            charC.recycle();
            hasStrValue=false;
        }
        byteC.setCharset(charset);
    }

    /** 
     * Sets the content to be a char[]
     *
     * @param c the bytes
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     */
    public void setChars( char[] c, int off, int len ) {
        charC.setChars( c, off, len );
        type=T_CHARS;
        hasStrValue=false;
        hasHashCode=false;
        hasIntValue=false;
        hasLongValue=false;
        hasDateValue=false; 
    }

    /** Remove the cached string value. Use it after a conversion on the
     *	byte[] or after the encoding is changed
     *  XXX Is this needed ?
     *  需要重新转换成字符串,即原来是char或者byte转换的字符串要重新转换
     */
    public void resetStringValue() {
	if( type != T_STR ) {
	    // If this was cread as a byte[] or char[], we remove
	    // the old string value
	    hasStrValue=false;
	    strValue=null;
	}
    }

    /** 
     * Set the content to be a string
     */
    public void setString( String s ) {
        strValue=s;
        hasHashCode=false;
        hasIntValue=false;
        hasLongValue=false;
        hasDateValue=false; 
        if (s == null) {
            hasStrValue=false;
            type=T_NULL;
        } else {
            hasStrValue=true;
            type=T_STR;
        }
    }

    // -------------------- Conversion and getters --------------------

    /** Compute the string value
     */
    public String toString() {
        if( hasStrValue ) return strValue;
        
        switch (type) {
        case T_CHARS:
            strValue=charC.toString();//去转换成字符串
            hasStrValue=true;//说明已经转换成功了
            return strValue;
        case T_BYTES:
            strValue=byteC.toString();
            hasStrValue=true;
            return strValue;
        }
        return null;
    }

    //----------------------------------------
    /** Return the type of the original content. Can be
     *  T_STR, T_BYTES, T_CHARS or T_NULL
     */
    public int getType() {
    	return type;
    }
    
    /**
     * Returns the byte chunk, representing the byte[] and offset/length.
     * Valid only if T_BYTES or after a conversion was made.
     */
    public ByteChunk getByteChunk() {
    	return byteC;
    }

    /**
     * Returns the char chunk, representing the char[] and offset/length.
     * Valid only if T_CHARS or after a conversion was made.
     */
    public CharChunk getCharChunk() {
    	return charC;
    }

    /**
     * Returns the string value.
     * Valid only if T_STR or after a conversion was made.
     */
    public String getString() {
    	return strValue;
    }

    /** Unimplemented yet. Do a char->byte conversion.
     * 最终无论是char、byte还是字符串,都最终让byte有内容
     */
    public void toBytes() {
        if( ! byteC.isNull() ) {//说明本身就是byte,直接返回即可
            type=T_BYTES;
            return;
        }
        toString();//先转换成字符串
        type=T_BYTES;
        byte bb[] = strValue.getBytes();//然后对字符串转换成字节数组
        byteC.setBytes(bb, 0, bb.length);//赋予给bytes
    }

    /** Convert to char[] and fill the CharChunk.
     *  XXX Not optimized - it converts to String first.
     *  最终无论是char、byte还是字符串,都最终让char有内容
     */
    public void toChars() {
		if( ! charC.isNull() ) {
	            type=T_CHARS;
		    return;
		}
		// inefficient
		toString();
	        type=T_CHARS;
		char cc[]=strValue.toCharArray();
		charC.setChars(cc, 0, cc.length);
    }
    

    /**
     * Returns the length of the original buffer.
     * Note that the length in bytes may be different from the length
     * in chars.
     * 有效字符长度
     */
    public int getLength() {
		if(type==T_BYTES)
		    return byteC.getLength();
		if(type==T_CHARS) {
		    return charC.getLength();
		}
		if(type==T_STR)
		    return strValue.length();
		toString();
		if( strValue==null ) return 0;
		return strValue.length();
    }

    // -------------------- equals --------------------

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(String s) {
		if( ! caseSensitive ) //说明不敏感大小写
		    return equalsIgnoreCase( s );
		switch (type) {
		case T_STR:
		    if( strValue==null && s!=null) return false;
		    return strValue.equals( s );
		case T_CHARS:
		    return charC.equals( s );
		case T_BYTES:
		    return byteC.equals( s );
		default:
		    return false;
		}
    }

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     * 忽略大小写
     */
    public boolean equalsIgnoreCase(String s) {
		switch (type) {
		case T_STR:
		    if( strValue==null && s!=null) return false;
		    return strValue.equalsIgnoreCase( s );
		case T_CHARS:
		    return charC.equalsIgnoreCase( s );
		case T_BYTES:
		    return byteC.equalsIgnoreCase( s );
		default:
		    return false;
		}
    }

    public boolean equals(MessageBytes mb) {
		switch (type) {
		case T_STR:
		    return mb.equals( strValue );
		}
	
		if( mb.type != T_CHARS &&
		    mb.type!= T_BYTES ) {
		    // it's a string or int/date string value
		    return equals( mb.toString() );
		}
	
		// mb is either CHARS or BYTES.
		// this is either CHARS or BYTES
		// Deal with the 4 cases ( in fact 3, one is simetric)
		
		if( mb.type == T_CHARS && type==T_CHARS ) {
		    return charC.equals( mb.charC );
		} 
		if( mb.type==T_BYTES && type== T_BYTES ) {
		    return byteC.equals( mb.byteC );
		}
		if( mb.type== T_CHARS && type== T_BYTES ) {
		    return byteC.equals( mb.charC );
		}
		if( mb.type== T_BYTES && type== T_CHARS ) {
		    return mb.byteC.equals( charC );
		}
		// can't happen
		return true;
    }

    
    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     */
    public boolean startsWith(String s) {
		switch (type) {
		case T_STR:
		    return strValue.startsWith( s );
		case T_CHARS:
		    return charC.startsWith( s );
		case T_BYTES:
		    return byteC.startsWith( s );
		default:
		    return false;
		}
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     * @param pos The start position
     * 忽略大小写,是否以s开头
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
		switch (type) {
		case T_STR:
		    if( strValue==null ) return false;
		    if( strValue.length() < pos + s.length() ) return false;
		    
		    for( int i=0; i<s.length(); i++ ) {
			if( Ascii.toLower( s.charAt( i ) ) !=
			    Ascii.toLower( strValue.charAt( pos + i ))) {
			    return false;
			}
		    }
		    return true;
		case T_CHARS:
		    return charC.startsWithIgnoreCase( s, pos );
		case T_BYTES:
		    return byteC.startsWithIgnoreCase( s, pos );
		default:
		    return false;
		}
    }

    

    // -------------------- Hash code  --------------------
    public  int hashCode() {
		if( hasHashCode ) return hashCode;
		int code = 0;
	
		if( caseSensitive ) 
		    code=hash(); 
		else
		    code=hashIgnoreCase();
		hashCode=code;
		hasHashCode=true;
		return code;
    }

    // normal hash. 
    private int hash() {
		int code=0;
		switch (type) {
		case T_STR:
		    // We need to use the same hash function
		    for (int i = 0; i < strValue.length(); i++) {
			code = code * 37 + strValue.charAt( i );
		    }
		    return code;
		case T_CHARS:
		    return charC.hash();
		case T_BYTES:
		    return byteC.hash();
		default:
		    return 0;
		}
    }

    // hash ignoring case
    private int hashIgnoreCase() {
		int code=0;
		switch (type) {
		case T_STR:
		    for (int i = 0; i < strValue.length(); i++) {
			code = code * 37 + Ascii.toLower(strValue.charAt( i ));
		    }
		    return code;
		case T_CHARS:
		    return charC.hashIgnoreCase();
		case T_BYTES:
		    return byteC.hashIgnoreCase();
		default:
		    return 0;
		}
    }

    // Inefficient initial implementation. Will be replaced on the next
    // round of tune-up
    //判断从starting开始之后,包含字符串s的位置是哪个
    public int indexOf(String s, int starting) {
		toString();
		return strValue.indexOf( s, starting );
    }
    
    // Inefficient initial implementation. Will be replaced on the next
    // round of tune-up
    //从0位置开始查找出现字符串参数的位置
    public int indexOf(String s) {
    	return indexOf( s, 0 );
    }
    
    //忽略大小写,判断从starting开始之后,包含字符串s的位置是哪个
    public int indexOfIgnoreCase(String s, int starting) {
		toString();//先不管什么方式,都转换成字符串
		String upper=strValue.toUpperCase();//对字符串进行大写
		String sU=s.toUpperCase();//对查找的字符串进行大写
		return upper.indexOf( sU, starting );//查找字符串内容
    }
    
    public int indexOf(char c) {
    	return indexOf( c, 0);
    }
    
    /**
     * Returns true if the message bytes starts with the specified string.
     * @param c the character
     * @param starting The start position
     * 判断从starting开始之后,包含c的位置是哪个
     */
    public int indexOf(char c, int starting) {
		switch (type) {
		case T_STR:
		    return strValue.indexOf( c, starting );
		case T_CHARS:
		    return charC.indexOf( c, starting);
		case T_BYTES:
		    return byteC.indexOf( c, starting );
		default:
		    return -1;
		}
    }

    /** Copy the src into this MessageBytes, allocating more space if
     *  needed
     *  将src的内容copy到本类中
     */
    public void duplicate( MessageBytes src ) throws IOException
    {
	switch( src.getType() ) {//查看src的类型
	case MessageBytes.T_BYTES://是byte
	    type=T_BYTES;//让本类也是byte
	    ByteChunk bc=src.getByteChunk();//获取是src的byte数组内容
	    byteC.allocate( 2 * bc.getLength(), -1 );//因为清空了原始的byteC内的数据
	    byteC.append( bc );//因此此时byteC的数据内容就是src的内容
	    break;
	case MessageBytes.T_CHARS:
	    type=T_CHARS;
	    CharChunk cc=src.getCharChunk();
	    charC.allocate( 2 * cc.getLength(), -1 );
	    charC.append( cc );
	    break;
	case MessageBytes.T_STR:
	    type=T_STR;
	    String sc=src.getString();
	    this.setString( sc );
	    break;
	}
    }

    // -------------------- Deprecated code --------------------
    // efficient int, long and date
    // XXX used only for headers - shouldn't be
    // stored here.
    //用于存储的内容就是int、long、Date之一的时候使用
    private int intValue;
    private boolean hasIntValue=false;
    private long longValue;
    private boolean hasLongValue=false;
    private Date dateValue;
    private boolean hasDateValue=false;
    
    /**
     *  @deprecated The buffer are general purpose, caching for headers should
     *  be done in headers. The second parameter allows us to pass a date format
     * instance to avoid synchronization problems.
     */
    public void setTime(long t, DateFormat df) {
    	// XXX replace it with a byte[] tool
		recycle();
		if( dateValue==null)
		    dateValue=new Date(t);
		else
		    dateValue.setTime(t);
		if( df==null )
		    strValue=DateTool.format1123(dateValue);
		else
		    strValue=DateTool.format1123(dateValue,df);
		hasStrValue=true;
		hasDateValue=true;
		type=T_STR;   
    }

    /**
     * @deprecated
     */
    public void setTime(long t) {
    	setTime( t, null );
    }

    /** Set the buffer to the representation of an int
     */
    public void setInt(int i) {
        byteC.allocate(16, 32);
        int current = i;
        byte[] buf = byteC.getBuffer();
        int start = 0;
        int end = 0;
        if (i == 0) {
            buf[end++] = (byte) '0';
        }
        if (i < 0) {
            current = -i;
            buf[end++] = (byte) '-';
        }
        while (current > 0) {
            int digit = current % 10;
            current = current / 10;
            buf[end++] = HexUtils.HEX[digit];
        }
        byteC.setOffset(0);
        byteC.setEnd(end);
        // Inverting buffer
        end--;
        if (i < 0) {
            start++;
        }
        while (end > start) {
            byte temp = buf[start];
            buf[start] = buf[end];
            buf[end] = temp;
            start++;
            end--;
        }
        intValue=i;
        hasStrValue=false;
        hasHashCode=false;
        hasIntValue=true;
        hasLongValue=false;
        hasDateValue=false; 
        type=T_BYTES;
    }

    /** Set the buffer to the representation of an long
     */
    public void setLong(long l) {
        byteC.allocate(32, 64);
        long current = l;
        byte[] buf = byteC.getBuffer();
        int start = 0;
        int end = 0;
        if (l == 0) {
            buf[end++] = (byte) '0';
        }
        if (l < 0) {
            current = -l;
            buf[end++] = (byte) '-';
        }
        while (current > 0) {
            int digit = (int) (current % 10);
            current = current / 10;
            buf[end++] = HexUtils.HEX[digit];
        }
        byteC.setOffset(0);
        byteC.setEnd(end);
        // Inverting buffer
        end--;
        if (l < 0) {
            start++;
        }
        while (end > start) {
            byte temp = buf[start];
            buf[start] = buf[end];
            buf[end] = temp;
            start++;
            end--;
        }
        longValue=l;
        hasStrValue=false;
        hasHashCode=false;
        hasIntValue=false;
        hasLongValue=true;
        hasDateValue=false; 
        type=T_BYTES;
    }

    /**
     *  @deprecated The buffer are general purpose, caching for headers should
     *  be done in headers
     *  说明存储的字符串其实是一个long类型的时间戳,因此将其转换成long类型的时间戳
     */
    public  long getTime()
    {
     	if( hasDateValue ) {//因为是date类型的,因此直接转换成时间戳
		    if( dateValue==null) return -1;
		    return dateValue.getTime();
     	}
	
     	long l=DateTool.parseDate( this );//将存储的内容转换成时间戳
     	if( dateValue==null)
     	    dateValue=new Date(l);
     	else
     	    dateValue.setTime(l);
     	hasDateValue=true;
     	return l;
    }
    

    // Used for headers conversion
    /** Convert the buffer to an int, cache the value
     * 说明存储的字符串其实是一个int类型的,因此将其转换成int类型的
     */ 
    public int getInt() 
    {
		if( hasIntValue )
		    return intValue;
		
		switch (type) {
		case T_BYTES:
		    intValue=byteC.getInt();
		    break;
		default:
		    intValue=Integer.parseInt(toString());
		}
		hasIntValue=true;
		return intValue;
    }

    // Used for headers conversion
    /** Convert the buffer to an long, cache the value
     * 说明存储的字符串其实是一个long类型的,因此将其转换成long类型的
     */ 
    public long getLong() {
        if( hasLongValue )
            return longValue;
        
        switch (type) {
        case T_BYTES:
            longValue=byteC.getLong();
            break;
        default:
            longValue=Long.parseLong(toString());
        }

        hasLongValue=true;
        return longValue;

     }

    // -------------------- Future may be different --------------------
    
    private static MessageBytesFactory factory=new MessageBytesFactory();

    public static void setFactory( MessageBytesFactory mbf ) {
	factory=mbf;
    }
    
    public static class MessageBytesFactory {
	protected MessageBytesFactory() {
	}
	public MessageBytes newInstance() {
	    return new MessageBytes();
	}
    }
}
