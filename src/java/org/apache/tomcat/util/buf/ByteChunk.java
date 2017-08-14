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

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/*
 * In a server it is very important to be able to operate on
 * the original byte[] without converting everything to chars.
 * Some protocols are ASCII only, and some allow different
 * non-UNICODE encodings. The encoding is not known beforehand,
 * and can even change during the execution of the protocol.
 * ( for example a multipart message may have parts with different
 *  encoding )
 *
 * For HTTP it is not very clear how the encoding of RequestURI
 * and mime values can be determined, but it is a great advantage
 * to be able to parse the request without converting to string.
 */

// TODO: This class could either extend ByteBuffer, or better a ByteBuffer inside
// this way it could provide the search/etc on ByteBuffer, as a helper.

/**
 * This class is used to represent a chunk of bytes, and
 * utilities to manipulate byte[].
 *
 * The buffer can be modified and used for both input and output.
 *
 * There are 2 modes: The chunk can be associated with a sink - ByteInputChannel or ByteOutputChannel,
 * which will be used when the buffer is empty ( on input ) or filled ( on output ).
 * For output, it can also grow. This operating mode is selected by calling setLimit() or
 * allocate(initial, limit) with limit != -1.
 *
 * Various search and append method are defined - similar with String and StringBuffer, but
 * operating on bytes.
 *
 * This is important because it allows processing the http headers directly on the received bytes,
 * without converting to chars and Strings until the strings are needed. In addition, the charset
 * is determined later, from headers or user code.
 *
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public final class ByteChunk implements Cloneable, Serializable {

    /** Input interface, used when the buffer is emptiy
     *
     * Same as java.nio.channel.ReadableByteChannel
     */
    public static interface ByteInputChannel {
        /** 
         * Read new bytes ( usually the internal conversion buffer ).
         * The implementation is allowed to ignore the parameters, 
         * and mutate the chunk if it wishes to implement its own buffering.
         */
        public int realReadBytes(byte cbuf[], int off, int len)
            throws IOException;
    }

    /** Same as java.nio.channel.WrittableByteChannel.
     */
    public static interface ByteOutputChannel {
        /** 
         * Send the bytes ( usually the internal conversion buffer ).
         * Expect 8k output if the buffer is full.
         */
        public void realWriteBytes(byte cbuf[], int off, int len)
            throws IOException;
    }

    // --------------------

    /** Default encoding used to convert to strings. It should be UTF8,
        as most standards seem to converge, but the servlet API requires
        8859_1, and this object is used mostly for servlets. 
    */
    public static final Charset DEFAULT_CHARSET;

    static {
        Charset c = null;
        try {
            c = B2CConverter.getCharset("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            // Should never happen since all JVMs must support ISO-8859-1
        }
        DEFAULT_CHARSET = c;
    }

    // byte[]
    private byte[] buff;//存数字节的字节数组

    private int start=0;//有效字节的开始位置
    private int end;//有效字节的最后一个位置
    // How much can it grow, when data is added
    private int limit=-1;//最大值,表示使用缓冲区的最大位置,不能超过该位置
    
    private Charset charset;

    private boolean isSet=false; // XXX 一旦初始化了，则该buff就有缓存了，因此设置isSet为true

    private ByteInputChannel in = null;
    private ByteOutputChannel out = null;

    private boolean optimizedWrite=true;
    
    /**
     * Creates a new, uninitialized ByteChunk object.
     */
    public ByteChunk() {
    }

    /**
     * 初始化buffer大小为initial个
     */
    public ByteChunk( int initial ) {
        allocate( initial, -1 );
    }

    //--------------------
    public ByteChunk getClone() {
        try {
            return (ByteChunk)this.clone();
        } catch( Exception ex) {
            return null;
        }
    }

    /**
     * buff是否有缓存
     * true表示没有缓存,因为isSet是当buffer有内容的时候,就设置为true,因此
     */
    public boolean isNull() {
        return ! isSet; // buff==null;
    }
    
    /**
     * Resets the message buff to an uninitialized state.
     * 回收buff,只要将buffer中start和end设置为0,那么buffer继续可以重新使用
     */
    public void recycle() {
        //        buff = null;
        charset=null;
        start=0;
        end=0;
        isSet=false;
    }

    /**
     * 重置,只是将buff设置为null即可
     */
    public void reset() {
        buff=null;
    }

    // -------------------- Setup --------------------
    /**
     * 初始化buffer大小为initial个
     */
    public void allocate( int initial, int limit  ) {
        if( buff==null || buff.length < initial ) {
            buff=new byte[initial];
        }    
        this.limit=limit;
        start=0;
        end=0;
        isSet=true;
    }

    /**
     * Sets the message bytes to the specified subarray of bytes.
     * 
     * @param b the ascii bytes
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     */
    public void setBytes(byte[] b, int off, int len) {
        buff = b;
        start = off;
        end = start+ len;
        isSet=true;
    }

    public void setOptimizedWrite(boolean optimizedWrite) {
        this.optimizedWrite = optimizedWrite;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public Charset getCharset() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        return charset;
    }

    /**
     * Returns the message bytes.
     * 返回整个buffer缓冲区,但是注意里面的内容并不是全都有用,因为buffer的start位置和end位置之间的字节才是有用的
     */
    public byte[] getBytes() {
        return getBuffer();
    }

    /**
     * Returns the message bytes.
     * 返回整个buffer缓冲区,但是注意里面的内容并不是全都有用,因为buffer的start位置和end位置之间的字节才是有用的
     */
    public byte[] getBuffer() {
        return buff;
    }

    /**
     * Returns the start offset of the bytes.
     * For output this is the end of the buffer.
     */
    public int getStart() {
        return start;
    }

    public int getOffset() {
        return start;
    }

    /**
     * 设置开始位置
     */
    public void setOffset(int off) {
        if (end < off ) end=off;
        start=off;
    }

    /**
     * Returns the length of the bytes.
     * XXX need to clean this up
     * 获得缓冲字节的有效长度,即end-start位置间隔
     */
    public int getLength() {
        return end-start;
    }

    /** Maximum amount of data in this buffer.
     *
     *  If -1 or not set, the buffer will grow undefinitely.
     *  Can be smaller than the current buffer size ( which will not shrink ).
     *  When the limit is reached, the buffer will be flushed ( if out is set )
     *  or throw exception.
     */
    public void setLimit(int limit) {
        this.limit=limit;
    }
    
    public int getLimit() {
        return limit;
    }

    /**
     * When the buffer is empty, read the data from the input channel.
     */
    public void setByteInputChannel(ByteInputChannel in) {
        this.in = in;
    }

    /** When the buffer is full, write the data to the output channel.
     *         Also used when large amount of data is appended.
     *
     *  If not set, the buffer will grow to the limit.
     */
    public void setByteOutputChannel(ByteOutputChannel out) {
        this.out=out;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd( int i ) {
        end=i;
    }

    // -------------------- Adding data to the buffer --------------------
    /** Append a char, by casting it to byte. This IS NOT intended for unicode.
     *
     * @param c
     * @throws IOException
     */
    public void append( char c )
        throws IOException
    {
        append( (byte)c);
    }

    /**
     * 追加一个字节
     * 1.首先扩大一个字节空间。
     * 2.将该字节存放到end位置
     * 3.将end位置追加+ 
     */
    public void append( byte b )
        throws IOException
    {
        makeSpace( 1 );

        // couldn't make space
        /**
         * 如果发现扩展后，end依然大于limit位置，表示不能继续添加信息，因此将目前buffer数组中的信息，输出到流中，然后end和start都设置为0.因此buffer就足够添加新数据了
         * 扩容buffer
         */
        if( limit >0 && end >= limit ) {
            flushBuffer();
        }
        buff[end++]=b;
    }

    /**
     * 添加若干个字节，添加长度是length个
     * @param src
     * @throws IOException
     */
    public void append( ByteChunk src )
        throws IOException
    {
        append( src.getBytes(), src.getStart(), src.getLength());
    }

    /** Add data to the buffer
     */
    public void append( byte src[], int off, int len )
        throws IOException
    {
        // will grow, up to limit 扩展长度个
        makeSpace( len );

        // if we don't have limit: makeSpace can grow as it wants
        if( limit < 0 ) {//如果没有limit限制，则将所有信息都添加到buffer的end之后的位置，同时设置end位置为end+length
            // assert: makeSpace made enough space
            System.arraycopy( src, off, buff, end, len );
            end+=len;
            return;
        }

        /**
         * 如果limit有限制
         */
        // Optimize on a common case.
        // If the buffer is empty and the source is going to fill up all the
        // space in buffer, may as well write it directly to the output,
        // and avoid an extra copy
        /**
         * 做了优化处理，即如果做了优化处理、如果buffer是空的，即end=start，同时将要添加的信息正好可以填满整个buffer空间，则将其内容直接写到流中即可，避免了额外的copy过程开销。
         */
        if ( optimizedWrite && len == limit && end == start && out != null ) {
            out.realWriteBytes( src, off, len );
            return;
        }
        /**
         * 如果限制的limit长度与目前数组的end长度相减，即得到还能存储的长度。还能存储的长度比待存储的长度多，则直接copy信息到buffer中即可。
         */
        // if we have limit and we're below
        if( len <= limit - end ) {
            // makeSpace will grow the buffer to the limit,
            // so we have space
            System.arraycopy( src, off, buff, end, len );
            end+=len;
            return;
        }

        /**
         * 如果剩余buffer的长度不足添加新的len字节长度。则：
         * 1.获得还剩余buffer长度。
         * 2.copy剩余字节的长度信息到buffer中。同时end=end+添加的字节数。
         * 3.将buffer信息写入输出流中。
         * 4.计算待添加的长度还剩余多少。
         * 5.将剩余的信息添加到输出流中或者添加到buffer数组中
         */
        // need more space than we can afford, need to flush
        // buffer

        // the buffer is already at ( or bigger than ) limit

        // We chunk the data into slices fitting in the buffer limit, although
        // if the data is written directly if it doesn't fit

        int avail=limit-end;
        System.arraycopy(src, off, buff, end, avail);
        end += avail;

        flushBuffer();//end和start都是0位置

        int remain = len - avail;

        while (remain > (limit - end)) {
            out.realWriteBytes( src, (off + len) - remain, limit - end );// (off + len) - remain表示当前待添加的数组的剩余起始位置， limit - end表示还能添加的长度
            remain = remain - (limit - end);
        }

        System.arraycopy(src, (off + len) - remain, buff, end, remain);
        end += remain;

    }


    // -------------------- Removing data from the buffer --------------------

    public int substract()
        throws IOException {

        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadBytes( buff, 0, buff.length );
            if (n < 0)
                return -1;
        }

        return (buff[start++] & 0xFF);

    }

    public int substract(ByteChunk src)
        throws IOException {

        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadBytes( buff, 0, buff.length );
            if (n < 0)
                return -1;
        }

        int len = getLength();
        src.append(buff, start, len);
        start = end;
        return len;

    }

    public int substract( byte src[], int off, int len )
        throws IOException {

        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadBytes( buff, 0, buff.length );
            if (n < 0)
                return -1;
        }

        int n = len;
        if (len > getLength()) {
            n = getLength();
        }
        System.arraycopy(buff, start, src, off, n);
        start += n;
        return n;

    }


    /** Send the buffer to the sink. Called by append() when the limit is reached.
     *  You can also call it explicitely to force the data to be written.
     *
     * @throws IOException
     */
    public void flushBuffer()
        throws IOException
    {
        //assert out!=null
        if( out==null ) {
            throw new IOException( "Buffer overflow, no sink " + limit + " " +
                                   buff.length  );
        }
        out.realWriteBytes( buff, start, end-start );
        end=start;
    }

    /** Make space for len chars. If len is small, allocate
     *        a reserve space too. Never grow bigger than limit.
     扩展空间，扩展的空间绝对不能超过limit
     a.用目前数组的最后一个位置，即end与待添加的字节个数相加，得到未来数组大小。
     b.判断未来大小是否大于limit。如果大于limit，则只能存储limit大小。
     c.如果数组为null,则初始化数组，最小初始化为256个字节。
     d.如果目前数组存在，并且数组长度大于待添加的长度，因此直接返回，表示不用扩展。
     e.如果数组不够大小，则扩展。将原来数组中从start位置开始复制，复制长度为edn-start个。
     f.重新设置start为0，end为end-start
     */
    private void makeSpace(int count)
    {
        byte[] tmp = null;

        int newSize;
        int desiredSize=end + count;

        // Can't grow above the limit
        if( limit > 0 && desiredSize > limit) {
            desiredSize=limit;
        }

        if( buff==null ) {
            if( desiredSize < 256 ) desiredSize=256; // take a minimum
            buff=new byte[desiredSize];
        }
        
        // limit < buf.length ( the buffer is already big )
        // or we already have space XXX
        if( desiredSize <= buff.length ) {
            return;
        }
        // grow in larger chunks
        if( desiredSize < 2 * buff.length ) {
            newSize= buff.length * 2;
            if( limit >0 && newSize > limit ) newSize=limit;
            tmp=new byte[newSize];
        } else {
            newSize= buff.length * 2 + count ;
            if( limit > 0 &&
                newSize > limit ) newSize=limit;
            tmp=new byte[newSize];
        }
        
        System.arraycopy(buff, start, tmp, 0, end-start);
        buff = tmp;
        tmp = null;
        end=end-start;
        start=0;
    }
    
    // -------------------- Conversion and getters --------------------

    public String toString() {
        if (null == buff) {
            return null;
        } else if (end-start == 0) {
            return "";
        }
        return StringCache.toString(this);
    }
    
    public String toStringInternal() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        // new String(byte[], int, int, Charset) takes a defensive copy of the
        // entire byte array. This is expensive if only a small subset of the
        // bytes will be used. The code below is from Apache Harmony.
        CharBuffer cb;
        cb = charset.decode(ByteBuffer.wrap(buff, start, end-start));
        return new String(cb.array(), cb.arrayOffset(), cb.length());
    }
/**
 * 将buffer中从start到end之间的信息转换成int值，他们之间所有的信息必须都是整数，否则抛异常
 * @return
 */
    public int getInt()
    {
        return Ascii.parseInt(buff, start,end-start);
    }

    public long getLong() {
        return Ascii.parseLong(buff, start,end-start);
    }


    // -------------------- equals --------------------

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     * 比较是否相同，如果长度不相同，则肯定不相同。
     * 该方法貌似只是比较ascii，如果是汉字或者其他字符，不能用byte和char比较
     */
    public boolean equals(String s) {
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!
        
        byte[] b = buff;
        int blen = end-start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(String s) {
        byte[] b = buff;
        int blen = end-start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (Ascii.toLower(b[boff++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean equals( ByteChunk bb ) {
        return equals( bb.getBytes(), bb.getStart(), bb.getLength());
    }
    
    public boolean equals( byte b2[], int off2, int len2) {
        byte b1[]=buff;
        if( b1==null && b2==null ) return true;

        int len=end-start;
        if ( len2 != len || b1==null || b2==null ) 
            return false;
                
        int off1 = start;

        while ( len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
                return false;
            }
        }
        return true;
    }

    public boolean equals( CharChunk cc ) {
        return equals( cc.getChars(), cc.getStart(), cc.getLength());
    }
    
    public boolean equals( char c2[], int off2, int len2) {
        // XXX works only for enc compatible with ASCII/UTF !!!
        byte b1[]=buff;
        if( c2==null && b1==null ) return true;
        
        if (b1== null || c2==null || end-start != len2 ) {
            return false;
        }
        int off1 = start;
        int len=end-start;
        
        while ( len-- > 0) {
            if ( (char)b1[off1++] != c2[off2++]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     * 判断该buffer是否是以String开头的。如果String参数的长度比buffer还大，则肯定有问题。
     * 该方法貌似也只支持byte
     */
    public boolean startsWith(String s) {
        // Works only if enc==UTF
        byte[] b = buff;
        int blen = s.length();
        if (b == null || blen > end-start) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /* Returns true if the message bytes start with the specified byte array */
    public boolean startsWith(byte[] b2) {
        byte[] b1 = buff;
        if (b1 == null && b2 == null) {
            return true;
        }

        int len = end - start;
        if (b1 == null || b2 == null || b2.length > len) {
            return false;
        }
        for (int i = start, j = 0; i < end && j < b2.length; ) {
            if (b1[i++] != b2[j++]) 
                return false;
        }
        return true;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     * @param pos The position
     * buffer移动pos位置之后，是否以string开头
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        byte[] b = buff;
        int len = s.length();
        if (b == null || len+pos > end-start) {
            return false;
        }
        int off = start+pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower( b[off++] ) != Ascii.toLower( s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public int indexOf( String src, int srcOff, int srcLen, int myOff ) {
        char first=src.charAt( srcOff );

        // Look for first char 
        int srcEnd = srcOff + srcLen;
        
        mainLoop:
        for( int i=myOff+start; i <= (end - srcLen); i++ ) {
            if( buff[i] != first ) continue;
            // found first char, now look for a match
            int myPos=i+1;
            for( int srcPos=srcOff + 1; srcPos< srcEnd; ) {
                if( buff[myPos++] != src.charAt( srcPos++ ))
                    continue mainLoop;
            }
            return i-start; // found it
        }
        return -1;
    }

    // -------------------- Hash code  --------------------

    // normal hash. 
    public int hash() {
        return hashBytes( buff, start, end-start);
    }

    /**
     * 忽略大小写的hash计算
     */
    // hash ignoring case
    public int hashIgnoreCase() {
        return hashBytesIC( buff, start, end-start );
    }

    private static int hashBytes( byte buff[], int start, int bytesLen ) {
        int max=start+bytesLen;
        byte bb[]=buff;
        int code=0;
        for (int i = start; i < max ; i++) {
            code = code * 37 + bb[i];
        }
        return code;
    }

    /**
     * 忽略大小写的hash计算
     */
    private static int hashBytesIC(byte bytes[], int start,int bytesLen){
        int max=start+bytesLen;
        byte bb[]=bytes;
        int code=0;
        for (int i = start; i < max ; i++) {
            code = code * 37 + Ascii.toLower(bb[i]);
        }
        return code;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param c the character
     * @param starting The start position
     * 在全局的buff中查找c字符位置,第二个参数是偏移量,基于start位置的偏移量
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf( buff, start+starting, end, c);
        return (ret >= start) ? ret - start : -1;
    }
    
    /**
     * 从off位置开始循环bytes数组,直到end位置,找到bytes数组中包含第四个参数qq字符的位置
     */
    public static int  indexOf( byte bytes[], int off, int end, char qq )
    {
        // Works only for UTF 
        while( off < end ) {
            byte b=bytes[off];
            if( b==qq )
                return off;
            off++;
        }
        return -1;
    }

    /**
     * Returns the first instance of any of the given bytes in the byte array
     * between the specified start and end.
     * 
     * @param bytes The byte array to search
     * @param start The point to start searching from in the byte array
     * @param end   The point to stop searching in the byte array
     * @param b     The array of bytes to search for 
     * @return      The position of the first instance of the byte or -1 if the
     *                  byte is not found.
     *从start位置开始循环bytes数组,直到end结束,如果bytes字节中某一个字节在b数组中,则返回该位置                  
     */
    public static int findBytes(byte bytes[], int start, int end, byte b[]) {
        int blen = b.length;
        int offset = start;
        while (offset < end) {
            for (int i = 0;  i < blen; i++) 
                if (bytes[offset] == b[i]) {
                    return offset;
                }
            offset++;
        }
        return -1;
    }

    /** Find a character, no side effects.
     *  @return index of char if found, -1 if not
     *  从start位置循环查找buf数组,一直到end位置,找到buf数组中等于char为c的字节位置
     */
    public static int findChar( byte buf[], int start, int end, char c ) {
        byte b=(byte)c;
        int offset = start;
        while (offset < end) {
            if (buf[offset] == b) {//判断buf中offset位置是否与c相同,如果相同,则返回该位置
                return offset;
            }
            offset++;
        }
        return -1;
    }

    /** Find a character, no side effects.
     *  @return index of char if found, -1 if not
     *  从start位置开始循环buf数组,一直到end位置,找到buf数组中第一个在c数组出现的字符位置
     */
    public static int findChars( byte buf[], int start, int end, byte c[] ) {
        int clen=c.length;
        int offset = start;
        while (offset < end) {//从start位置开始循环,一直到end位置结束
            for( int i=0; i<clen; i++ ) 
                if (buf[offset] == c[i]) {//查看buf的offset位置是否在c数组中,如果在,则返回该位置,否则累加offset,继续查找下一个buf字节是否在c数组中
                    return offset;
                }
            offset++;
        }
        return -1;
    }

    /** Find the first character != c 
     *  @return index of char if found, -1 if not
     *  从start开始循环buf数组,一直到end位置，找到该数组中，第一个没有在c数组中出现的位置
     */
    public static int findNotChars( byte buf[], int start, int end, byte c[] )
    {
        int clen=c.length;
        int offset = start;
        boolean found;
                
        while (offset < end) {
            found=true;
            for( int i=0; i<clen; i++ ) {
                if (buf[offset] == c[i]) {//只要buf数组中的offset位置在c中出现,则退出循环,累加offset++,继续查找,直到buf中offset位置的字符不再c数组中,则返回offset位置
                    found=false;
                    break;
                }
            }
            if( found ) { // buf[offset] != c[0..len]
                return offset;
            }
            offset++;
        }
        return -1;
    }


    /**
     * Convert specified String to a byte array. This ONLY WORKS for ascii, UTF
     * chars will be truncated.
     * 
     * @param value to convert to byte array
     * @return the byte array value
     * 把字符串中每一个字节提取出来组装成字节数组返回,注意这种方式仅仅工作与ascii
     */
    public static final byte[] convertToBytes(String value) {
        byte[] result = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            result[i] = (byte) value.charAt(i);
        }
        return result;
    }
    
    
}
