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

package org.apache.coyote.http11.filters;

import java.io.EOFException;
import java.io.IOException;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.Constants;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * Chunked input filter. Parses chunked data according to
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1</a><br>
 * 
 * @author Remy Maucherat
 * @author Filip Hanik
 * 
 * Transfer-Encoding: chunked 使用trunked编码动态的提供body内容的长度的方式
 * 
Chunked编码一般使用若干个chunk串连而成，最后由一个标明长度为0的chunk标示结束。
每个chunk分为头部和正文两部分，头部内容指定下一段正文的字符总数（非零开头的十六进制的数字）和数量单位（一般不写,表示字节）.
正文部分就是指定长度的实际内容，两部分之间用回车换行(CRLF)隔开。在最后一个长度为0的chunk中的内容是称为footer的内容，是一些附加的Header信息（通常可以直接忽略）。

上述解释过于官方，简而言之，chunked编码的基本方法是将大块数据分解成多块小数据，每块都可以自指定长度，其具体格式如下(BNF文法)：

 */
public class ChunkedInputFilter implements InputFilter {


    // -------------------------------------------------------------- Constants


    protected static final String ENCODING_NAME = "chunked";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer


    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Next buffer in the pipeline.
     * 底层的基础流
     */
    protected InputBuffer buffer;


    /**
     * Number of bytes remaining in the current chunk.
     * 还剩余多少个字节没有读取
     */
    protected int remaining = 0;


    /**
     * Position in the buffer.
     */
    protected int pos = 0;


    /**
     * Last valid byte in the buffer.
     */
    protected int lastValid = 0;


    /**
     * Read bytes buffer.
     */
    protected byte[] buf = null;


    /**
     * Byte chunk used to read bytes.
     */
    protected ByteChunk readChunk = new ByteChunk();


    /**
     * Flag set to true when the end chunk has been read.
     * true表示chunk已经读取完毕
     */
    protected boolean endChunk = false;


    /**
     * Byte chunk used to store trailing headers.
     */
    protected ByteChunk trailingHeaders;//存储key的name
    
    //静态方法
    {
        trailingHeaders = new ByteChunk();
        if (org.apache.coyote.Constants.MAX_TRAILER_SIZE > 0) {
            trailingHeaders.setLimit(org.apache.coyote.Constants.MAX_TRAILER_SIZE);
        }
    }


    /**
     * Flag set to true if the next call to doRead() must parse a CRLF pair
     * before doing anything else.
     * true 表示在做任何事情之前要解析回车换行 
     */
    protected boolean needCRLFParse = false;


    /**
     * Request being parsed.
     */
    private Request request;
    
    // ------------------------------------------------------------- Properties


    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Read bytes.
     * 
     * @return If the filter does request length control, this value is
     * significant; it should be the number of bytes consumed from the buffer,
     * up until the end of the current request body, or the buffer length, 
     * whichever is greater. If the filter does not do request body length
     * control, the returned value should be -1.
     * 将数据读取到chunk中
     */
    public int doRead(ByteChunk chunk, Request req)
        throws IOException {

        if (endChunk) //true表示chunk已经读取完毕
            return -1;

        if(needCRLFParse) {
            needCRLFParse = false;
            parseCRLF(false);//去解析回车换行,即忽略掉回车换行
        }

        if (remaining <= 0) {
            if (!parseChunkHeader()) {//解析头信息,获取remaining信息
                throw new IOException("Invalid chunk header");
            }
            if (endChunk) {//true表示chunk已经读取完毕
                parseEndChunk();
                return -1;
            }
        }

        int result = 0;

        if (pos >= lastValid) {//说明要读取字节信息到缓冲区
            if (readBytes() < 0) {
                throw new IOException(
                        "Unexpected end of stream whilst reading request body");
            }
        }

        if (remaining > (lastValid - pos)) {//说明剩余的内容>缓冲区有的内容,因此先读取缓冲区的内容,然后在继续读取数据源的信息
            result = lastValid - pos;
            remaining = remaining - result;
            chunk.setBytes(buf, pos, result);//先存取buff中的数据
            pos = lastValid;
        } else {//说明缓冲区数据是够的
            result = remaining;
            chunk.setBytes(buf, pos, remaining);//直接将缓冲区中去除remain的数据
            pos = pos + remaining;
            remaining = 0;
            //we need a CRLF
            if ((pos+1) >= lastValid) {
                //if we call parseCRLF we overrun the buffer here
                //so we defer it to the next call BZ 11117
                needCRLFParse = true;
            } else {
                parseCRLF(false); //parse the CRLF immediately
            }
        }

        return result;

    }


    // ---------------------------------------------------- InputFilter Methods


    /**
     * Read the content length from the request.
     */
    public void setRequest(Request request) {
        this.request = request;
    }


    /**
     * End the current request.
     */
    public long end()
        throws IOException {

        // Consume extra bytes : parse the stream until the end chunk is found
        while (doRead(readChunk, null) >= 0) {//不断的读取数据
        }

        // Return the number of extra bytes which were consumed
        return (lastValid - pos);

    }


    /**
     * Amount of bytes still available in a buffer.
     * 剩余多少个字节
     */
    public int available() {
        return (lastValid - pos);
    }
    

    /**
     * Set the next buffer in the filter pipeline.
     */
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * Make the filter ready to process the next request.
     */
    public void recycle() {
        remaining = 0;
        pos = 0;
        lastValid = 0;
        endChunk = false;
        needCRLFParse = false;
        trailingHeaders.recycle();
    }


    /**
     * Return the name of the associated encoding; Here, the value is 
     * "identity".
     */
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Read bytes from the previous buffer.
     * 真正的从底层流中读取数据,存储到readChunk中
     */
    protected int readBytes()
        throws IOException {

        int nRead = buffer.doRead(readChunk, null);//真正的从底层流中读取数据,存储到readChunk中
        pos = readChunk.getStart();//设置开始位置
        lastValid = pos + nRead;//最终有效的位置
        buf = readChunk.getBytes();//返回读取的缓冲区

        return nRead;

    }


    /**
     * Parse the header of a chunk.
     * A chunk header can look like 
     * A10CRLF
     * F23;chunk-extension to be ignoredCRLF
     * The letters before CRLF but after the trailer mark, must be valid hex digits, 
     * we should not parse F23IAMGONNAMESSTHISUP34CRLF as a valid header
     * according to spec
     * 解析chunk的头信息,即该信息是说这一部分chunk有多少个字节
     * ;之前的数据是16进制的数字,表示需要多少个字节,然后以CRLF结尾
     */
    protected boolean parseChunkHeader()
        throws IOException {

        int result = 0;
        boolean eol = false;
        boolean readDigit = false;//true表示读取到了数字
        boolean trailer = false;//true表示遇见了;符号

        while (!eol) {//读取一行数据

            if (pos >= lastValid) {
                if (readBytes() <= 0)
                    return false;
            }

            if (buf[pos] == Constants.CR || buf[pos] == Constants.LF) {//说明是回车 或者 换行符号
                parseCRLF(false);//过滤掉回车换行
                eol = true;//说明结束循环
            } else if (buf[pos] == Constants.SEMI_COLON) {//;
                trailer = true;
            } else if (!trailer) {//说明没有遇见;符号
                //don't read data after the trailer
                int charValue = HexUtils.getDec(buf[pos]);//获取该位置
                if (charValue != -1) {
                    readDigit = true;
                    result *= 16;
                    result += charValue;
                } else {
                    //we shouldn't allow invalid, non hex characters
                    //in the chunked header
                    return false;
                }
            }

            // Parsing the CRLF increments pos
            if (!eol) {
                pos++;
            }

        }

        if (!readDigit) //说明没有读取到数字
            return false;

        if (result == 0) //说明结束了
            endChunk = true;

        remaining = result; //说明本次剩余多少字节要去读取
        if (remaining < 0)
            return false;

        return true;

    }


    /**
     * Parse CRLF at end of chunk.
     * @deprecated  Use {@link #parseCRLF(boolean)}
     */
    @Deprecated
    protected boolean parseCRLF() throws IOException {
        parseCRLF(false);
        return true;
    }

    /**
     * Parse CRLF at end of chunk.
     *
     * @param   tolerant    Should tolerant parsing (LF and CRLF) be used? This
     *                      is recommended (RFC2616, section 19.3) for message
     *                      headers. true表示宽容
     * 解析回车换行,即接下来一两个byte可能是回车换行,因此要过滤掉
     */
    protected void parseCRLF(boolean tolerant) throws IOException {

        boolean eol = false;
        boolean crfound = false;//true表示已经出现了\r

        while (!eol) {

            if (pos >= lastValid) {//不断的读取数据
                if (readBytes() <= 0)
                    throw new IOException("Invalid CRLF");
            }

            if (buf[pos] == Constants.CR) {//说明此时是回车
                if (crfound) throw new IOException("Invalid CRLF, two CR characters encountered.");//不能出现两次\r
                crfound = true;
            } else if (buf[pos] == Constants.LF) {//说明此时是换行
                if (!tolerant && !crfound) {//false表示不容忍,不容忍的意思是说,必须有哦\r\n,因此既然是不容忍,那么\r又不存在,则抛异常
                    throw new IOException("Invalid CRLF, no CR character encountered.");
                }
                eol = true;//说明有\r,因此解析成功
            } else {
                throw new IOException("Invalid CRLF");
            }

            pos++;

        }
    }


    /**
     * Parse end chunk data.
     */
    protected boolean parseEndChunk() throws IOException {

        // Handle optional trailer headers
        while (parseHeader()) {//每一行不断的解析,获取一个key=value的header
            // Loop until we run out of headers
        }
        return true;
    }

    //解析请求头的key=value
    private boolean parseHeader() throws IOException {

        MimeHeaders headers = request.getMimeHeaders();

        byte chr = 0;

        // Read new bytes if needed
        if (pos >= lastValid) {
            if (readBytes() <0)
                throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
        }
    
        chr = buf[pos];

        // CRLF terminates the request
        if (chr == Constants.CR || chr == Constants.LF) {
            parseCRLF(false);
            return false;
        }
    
        // Mark the current buffer position
        int start = trailingHeaders.getEnd();
    
        //
        // Reading the header name
        // Header name is always US-ASCII
        //
    
        boolean colon = false;
        while (!colon) {//找到:符号
    
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (readBytes() <0)
                    throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
            }
    
            chr = buf[pos];
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                chr = (byte) (chr - Constants.LC_OFFSET);
            }

            if (chr == Constants.COLON) {//说明此时是:符号
                colon = true;
            } else {
                trailingHeaders.append(chr);
            }
    
            pos++;
    
        }
        MessageBytes headerValue = headers.addValue(trailingHeaders.getBytes(),
                start, trailingHeaders.getEnd() - start);//通过key的name获取value对象
    
        // Mark the current buffer position
        start = trailingHeaders.getEnd();

        //
        // Reading the header value (which can be spanned over multiple lines)
        //
    
        boolean eol = false;
        boolean validLine = true;
        int lastSignificantChar = 0;
    
        while (validLine) {
    
            boolean space = true;
    
            // Skipping spaces
            while (space) {//过滤掉多余的空格
    
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (readBytes() <0)
                        throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
                }
    
                chr = buf[pos];
                if ((chr == Constants.SP) || (chr == Constants.HT)) {
                    pos++;
                } else {
                    space = false;
                }
    
            }
    
            // Reading bytes until the end of the line
            while (!eol) {//找到回车换行位置
    
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (readBytes() <0)
                        throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
                }
    
                chr = buf[pos];
                if (chr == Constants.CR || chr == Constants.LF) {
                    parseCRLF(true);
                    eol = true;
                } else if (chr == Constants.SP) {
                    trailingHeaders.append(chr);
                } else {
                    trailingHeaders.append(chr);
                    lastSignificantChar = trailingHeaders.getEnd();
                }
    
                if (!eol) {
                    pos++;
                }
            }
    
            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header
    
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (readBytes() <0)
                    throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
            }
    
            chr = buf[pos];
            if ((chr != Constants.SP) && (chr != Constants.HT)) {//说明另外一行依然是该key对应的value,即value分多行提交上来的
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                trailingHeaders.append(chr);
            }
    
        }
    
        // Set the header value
        headerValue.setBytes(trailingHeaders.getBytes(), start,
                lastSignificantChar - start);
    
        return true;
    }
}
