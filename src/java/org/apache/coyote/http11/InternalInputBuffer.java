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


package org.apache.coyote.http11;

import java.io.IOException;
import java.io.InputStream;
import java.io.EOFException;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * 
 * 对request需要的请求头进行解析,因此构造函数需要传递一个request对象
 */
public class InternalInputBuffer implements InputBuffer {


    // -------------------------------------------------------------- Constants


    // ----------------------------------------------------------- Constructors

    /**
     * Default constructor.
     */
    public InternalInputBuffer(Request request) {
        this(request, Constants.DEFAULT_HTTP_HEADER_BUFFER_SIZE);
    }


    /**
     * Alternate constructor.
     * headerBufferSize 表示header最大的字节大小
     */
    public InternalInputBuffer(Request request, int headerBufferSize) {

        this.request = request;
        headers = request.getMimeHeaders();

        buf = new byte[headerBufferSize];

        inputStreamInputBuffer = new InputStreamInputBuffer();

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        swallowInput = true;

    }


    // -------------------------------------------------------------- Variables


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated Coyote request.
     * request对象,解析socket的信息都要存储在request对象中
     */
    protected Request request;


    /**
     * Headers of the associated request.
     * request类中的MimeHeaders对象,存储所有请求头
     */
    protected MimeHeaders headers;


    /**
     * State.解析完header之后,设置为false,true表示header正在解析
     */
    protected boolean parsingHeader;


    /**
     * Swallow input ? (in the case of an expectation)
     */
    protected boolean swallowInput;


    /**
     * Pointer to the current read buffer.
     * 每次从socket读取字节流的缓冲区大小
     */
    protected byte[] buf;


    /**
     * Last valid byte.最后一个有效字节
     */
    protected int lastValid;


    /**
     * Position in the buffer.在buffer中当前位置
     */
    protected int pos;


    /**
     * Pos of the end of the header in the buffer, which is also the
     * start of the body.
     * 在buffer中最后的位置，也是body的起始位置。
     */
    protected int end;


    /**
     * Underlying input stream.socket的inputStream
     * socket原生流
     */
    protected InputStream inputStream;


    /**
     * Underlying input buffer.
     */
    protected InputBuffer inputStreamInputBuffer;


    /**
     * Filter library.
     * Note: Filter[0] is always the "chunked" filter.
     * 过滤器集合
     */
    protected InputFilter[] filterLibrary;


    /**
     * Active filters (in order).
     * 按照顺序将活动的Filter添加到数组中
     */
    protected InputFilter[] activeFilters;


    /**
     * Index of the last active filter.
     * 最后一个活动的Filter在数组中的位置
     */
    protected int lastActiveFilter;


    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket input stream.
     */
    public void setInputStream(InputStream inputStream) {

        // FIXME: Check for null ?

        this.inputStream = inputStream;

    }


    /**
     * Get the underlying socket input stream.
     */
    public InputStream getInputStream() {

        return inputStream;

    }


    /**
     * Add an input filter to the filter library.
     */
    public void addFilter(InputFilter filter) {

        // FIXME: Check for null ?

        InputFilter[] newFilterLibrary = 
            new InputFilter[filterLibrary.length + 1];
        for (int i = 0; i < filterLibrary.length; i++) {
            newFilterLibrary[i] = filterLibrary[i];
        }
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new InputFilter[filterLibrary.length];

    }


    /**
     * Get filters.
     */
    public InputFilter[] getFilters() {

        return filterLibrary;

    }


    /**
     * Clear filters.
     */
    public void clearFilters() {

        filterLibrary = new InputFilter[0];
        lastActiveFilter = -1;

    }


    /**
     * Add an input filter to the filter library.
     * 添加一个活跃的过滤器,让过滤器产生一个链表
     */
    public void addActiveFilter(InputFilter filter) {

        if (lastActiveFilter == -1) {
            filter.setBuffer(inputStreamInputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {//校验是否该filter已经添加过了
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);//设置该filter的流
        }

        activeFilters[++lastActiveFilter] = filter;//添加活跃的filter

        filter.setRequest(request);//为该filter设置request

    }


    /**
     * Set the swallow input flag.
     */
    public void setSwallowInput(boolean swallowInput) {
        this.swallowInput = swallowInput;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Recycle the input buffer. This should be called when closing the 
     * connection.
     */
    public void recycle() {

        // Recycle Request object
        request.recycle();

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        inputStream = null;
        lastValid = 0;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already 
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     * 该方法是说明当前请求已经处理完成了
     * 当前请求的所有字节应该已经消费完成,这个方法仅仅重置了一些参数,方便我们解析下一个http请求 
     */
    public void nextRequest() {

        // Recycle Request object
        request.recycle();

        // Copy leftover bytes to the beginning of the buffer
        if (lastValid - pos > 0) {//说明还有信息
            int npos = 0;
            int opos = pos;
            while (lastValid - opos > opos - npos) {
                System.arraycopy(buf, opos, buf, npos, opos - npos);//将剩余的内容存储到buf的0位置开始
                npos += pos;
                opos += pos;
            }
            System.arraycopy(buf, opos, buf, npos, lastValid - opos);
        }

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // Reset pointers
        lastValid = lastValid - pos;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

    }


    /**
     * End request (consumes leftover bytes).
     * 
     * @throws IOException an undelying I/O error occured
     */
    public void endRequest()
        throws IOException {

        if (swallowInput && (lastActiveFilter != -1)) {
            int extraBytes = (int) activeFilters[lastActiveFilter].end();
            pos = pos - extraBytes;
        }

    }


    /**
     * Read the request line. This function is meant to be used during the 
     * HTTP request header parsing. Do NOT attempt to read the request body 
     * using it.
     *
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accomodate
     * the whole line.
     * 解析http的第一行请求,例如:get /aa.html?key=value http/1.0
     * 注意:没有host内容,只有uri
     */
    public void parseRequestLine()
        throws IOException {

        int start = 0;

        //
        // Skipping blank lines
        //跳过空白行,可能请求发过来的时候开头有空白行
        //
        byte chr = 0;
        do {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos++];

        } while ((chr == Constants.CR) || (chr == Constants.LF));

        pos--;

        // Mark the current buffer position
        start = pos;//找到跳出空白行的第一个字符

        //
        // Reading the method name
        // Method name is always US-ASCII

        //结构 method  url HTTP/1.1
        boolean space = false;//true表示发现了空格

        while (!space) {//找到第一个空格位置没,即获取get

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says no CR or LF in method name
            if (buf[pos] == Constants.CR || buf[pos] == Constants.LF) {
                throw new IllegalArgumentException(
                        sm.getString("iib.invalidmethod"));
            }
            // Spec says single SP but it also says be tolerant of HT
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {//寻找空格或者\t字符
                space = true;
                request.method().setBytes(buf, start, pos - start);//将get赋值给method属性
            }

            pos++;

        }

        
        // Spec says single SP but also says be tolerant of multiple and/or HT
        //过滤掉get后面多余的空格,原则上只有一个空格,但是可能会更多，所以过滤掉空格
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {//说明遇见空格或者\t
                pos++;
            } else {//说明空格已经解析结束
                space = false;
            }
        }

        // Mark the current buffer position
        //解析uri
        start = pos;
        int end = 0;
        int questionPos = -1;//请求头的uri中?号位置

        //
        // Reading the URI
        //

        boolean eol = false;//true表示遇见了回车换行

        //接续url
        while (!space) {//找到下一个空格为止,结束

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says single SP but it also says be tolerant of HT
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {//只要不是空格或者\t
                space = true;//找到空格了,结束循环
                end = pos;
            } else if ((buf[pos] == Constants.CR) 
                       || (buf[pos] == Constants.LF)) {
                // HTTP/0.9 style request
                eol = true;//HTTP/0.9版本没有最后一个关于协议的部分,因此解析uri的时候是到回车结束
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.QUESTION) //说明遇见了?字符
                       && (questionPos == -1)) {//如果uri中有?号,则标示出该问号的位置,并且以第一次出现?号为主
                questionPos = pos;
            }

            pos++;

        }
        //所有的uri部分都存放到unparsedURI中,即/aa.html?key=value
        request.unparsedURI().setBytes(buf, start, end - start);
        if (questionPos >= 0) {//如果有问号
            request.queryString().setBytes(buf, questionPos + 1, 
                                           end - questionPos - 1);///key=value
            request.requestURI().setBytes(buf, start, questionPos - start);///aa.html
        } else {
            request.requestURI().setBytes(buf, start, end - start);
        }

        //继续过滤多余空格
        // Spec says single SP but also says be tolerant of multiple and/or HT 过滤剩余空格
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }

        // Mark the current buffer position
        start = pos;
        end = 0;

        //
        // Reading the protocol
        // Protocol is always US-ASCII
        //
        //解析协议部分
        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.CR) {
                end = pos;
            } else if (buf[pos] == Constants.LF) {
                if (end == 0)
                    end = pos;
                eol = true;
            }

            pos++;

        }

        if ((end - start) > 0) {
            request.protocol().setBytes(buf, start, end - start);
        } else {
            request.protocol().setString("");
        }

    }


    /**
     * Parse the HTTP headers.
     * 解析请求头信息,即http的第二行开始,key:value形式存储内容,对该内容解析
     */
    public void parseHeaders()
        throws IOException {

        while (parseHeader()) {//不断的循环每一个请求头，将其存放到headers对象中
        }

        parsingHeader = false;//解析完header之后,设置为false
        end = pos;

    }


    /**
     * Parse an HTTP header.
     * 
     * @return false after reading a blank line (which indicates that the
     * HTTP header parsing is done
     * 每次调用该方法,解析得到一个header的key:value
     * 
     * 设置一个header的key=value内容
     */
    public boolean parseHeader()
        throws IOException {

        //
        // Check for blank line
        //
        byte chr = 0;
        while (true) {//过滤开始前很多\r信息

            // Read new bytes if needed 跳过多余空格,直到非空格为止,注意:如果遇到回车,则直接返回,说明已经没有header了
            if (pos >= lastValid) {
                if (!fill()) //读取数据
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];

            if ((chr == Constants.CR) || (chr == Constants.LF)) {//是\r \n
                if (chr == Constants.LF) {//是\n
                    pos++;
                    return false;
                }
            } else {//说明此时该字符是正常字符,因此退出循环,处理该字符,因为现在在做准备阶段,是在做前期过滤 若干个回车换行操作
                break;
            }

            pos++;

        }

        // Mark the current buffer position 设置key的pos位置为start
        int start = pos;

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        boolean colon = false;
        MessageBytes headerValue = null;//此时该对象表示header的value要对应的对象

        while (!colon) {//查找key:value中的:号位置

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.COLON) {//找到:号,则退出循环,冒号前的字符为start开始,pos-start长度,生成key,同时返回该key对应的value对象引用
                colon = true;
                //创建一个key-value形式的header头对象，将name值填充后，将空的value对象返回。后续会将其赋值。
                headerValue = headers.addValue(buf, start, pos - start);//说明获取header的头name,即创建一个name,返回该value对应的对象
            }
            chr = buf[pos];
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {//大小字母转成小写字母
                buf[pos] = (byte) (chr - Constants.LC_OFFSET);
            }

            pos++;

        }
        //此时已经找到了:号位置,因此start就是pos冒号位置

        // Mark the current buffer position 重新记录start位置为:号后面的value第一个字节位置
        start = pos;
        int realPos = pos;

        //
        //读取header对应的value值，该值可以跨越多行
        //当跨越一行的时候，接下来的就是空格或者tab，如果不是则说明该值已经结束了
        // Reading the header value (which can be spanned over multiple lines)
        //

        boolean eol = false;
        boolean validLine = true;

        while (validLine) {//是否为有效行,每一行都要进行如此循环处理

        	/**
        	 * 1.首先过滤所有空格,因为冒号后面可能追加了很多空格,小过滤掉这部分
        	 */
            boolean space = true;

            // Skipping spaces
            while (space) {//跳跃空格，一旦没有空格了，则设置为false。

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if ((buf[pos] == Constants.SP) || (buf[pos] == Constants.HT)) {//说明此时一直是
                    pos++;
                } else {
                    space = false;
                }

            }
            /**
             * 2.为了节省空间，采用同一个buffer缓冲处理，realPos表示真正value值的位置变化
             */
            int lastSignificantChar = realPos;

            // Reading bytes until the end of the line
            while (!eol) {//只要没有到行的最后，该eol都是false

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if (buf[pos] == Constants.CR) {
                } else if (buf[pos] == Constants.LF) {//遇到换行，则说明到行的最后了，设置eol为true,即表示退出循环
                    eol = true;
                } else if (buf[pos] == Constants.SP) {//如果遇到空格，则将pos位置的值，赋值给realPos，同时realPos位置向后移动。
                    buf[realPos] = buf[pos];
                    realPos++;
                } else {//如果不是空格，则设置pos位置给realPos，同时realPos移动一位，同时将realPos位置赋值给lastSignificantChar。前面没有赋值lastSignificantChar，是因为该方法起到了去空格的作用。
                    buf[realPos] = buf[pos];
                    realPos++;
                    lastSignificantChar = realPos;
                }

                pos++;

            }
//经过上一个while循环，得到了该行最后一个有效字符的位置，即lastSignificantChar赋值给真是的位置值realPos
            realPos = lastSignificantChar;

            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header

            // Read new bytes if needed
            /**
             * 3.继续读取数据，判断接下来的一行是否是空格或者\t，如果是，则说明下一行依然是有效行，即继续循环。
             * 如果不是，说明下一行已经不是有效行了，因此直接设置validLine = false;，退出while循环
             */
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];
            if ((chr != Constants.SP) && (chr != Constants.HT)) {//说明下一行不是属于该key:value的范围了,则退出该循环,说明该key:value已经结束了
                validLine = false;
            } else {//继续读下一行,也就是说下一行也是该key的内容,该key的value值分多行传输过来的
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                buf[realPos] = chr;
                realPos++;
            }

        }
//找到一行的所有的信息后，将其设置为value的值，即赋值过程
        // Set the header value
        headerValue.setBytes(buf, start, realPos - start);//设置value的具体信息内容

        return true;

    }


    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Read some bytes.
     */
    public int doRead(ByteChunk chunk, Request req) 
        throws IOException {

        if (lastActiveFilter == -1) //说明没有获取的filter
            return inputStreamInputBuffer.doRead(chunk, req);
        else
            return activeFilters[lastActiveFilter].doRead(chunk,req);//从最后一个过滤器开始过滤数据

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Fill the internal buffer using data from the undelying input stream.
     * 
     * @return false if at end of stream
     * 从输入流中读取字符到字节数组中
     */
    protected boolean fill()
        throws IOException {

        int nRead = 0;

        if (parsingHeader) {//说明正在解析header,而header的长度是http协议规定死的,因此不能超出范围

            if (lastValid == buf.length) {//说明已经读取到buf的结尾了
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }

            nRead = inputStream.read(buf, pos, buf.length - lastValid);//读取数据写到buf中,从pos位置开始写
            if (nRead > 0) {
                lastValid = pos + nRead;
            }

        } else {

            if (buf.length - end < 4500) {
                // In this case, the request header was really large, so we allocate a 
                // brand new one; the old one will get GCed when subsequent requests
                // clear all references
                buf = new byte[buf.length];
                end = 0;
            }
            pos = end;
            lastValid = pos;
            nRead = inputStream.read(buf, pos, buf.length - lastValid);//读取数据,写入到buf中,从pos位置开始写,读取大小就是buf剩余的长度
            if (nRead > 0) {
                lastValid = pos + nRead;//说明读取完成
            }

        }

        return (nRead > 0);

    }


    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class InputStreamInputBuffer 
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         * 读取缓存buf的内容,设置到chunk上
         */
        public int doRead(ByteChunk chunk, Request req ) 
            throws IOException {

            if (pos >= lastValid) {//说明buf没有数据了,要继续读取数据填充buf
                if (!fill())
                    return -1;
            }

            int length = lastValid - pos;//buf中还有多少个内容
            chunk.setBytes(buf, pos, length);//将剩余buf内容赋值给chunk
            pos = lastValid;

            return (length);
        }
    }

}
