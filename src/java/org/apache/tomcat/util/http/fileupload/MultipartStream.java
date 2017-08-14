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


package org.apache.tomcat.util.http.fileupload;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;


/**
 * <p> Low level API for processing file uploads.
 *
 * <p> This class can be used to process data streams conforming to MIME
 * 'multipart' format as defined in
 * <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>. Arbitrarily
 * large amounts of data in the stream can be processed under constant
 * memory usage.
 *
 * <p> The format of the stream is defined in the following way:<br>
 *  格式：1.---------------------------7db15a14291cce 表示该请求的分隔符。
 *  2.以---------------------------7db15a14291cce--结尾，结尾要多两个--。
 *  3.每一个具体的域都是比标准的---------------------------7db15a14291cce在前面多两个--字符
 *  
 * <code>
 *   multipart-body := preamble 1*encapsulation close-delimiter epilogue<br>
 *   encapsulation := delimiter body CRLF<br>
 *   delimiter := "--" boundary CRLF<br>
 *   close-delimiter := "--" boudary "--"<br>
 *   preamble := &lt;ignore&gt;<br>
 *   epilogue := &lt;ignore&gt;<br>
 *   body := header-part CRLF body-part<br>
 *   header-part := 1*header CRLF<br>
 *   header := header-name ":" header-value<br>
 *   header-name := &lt;printable ascii characters except ":"&gt;<br>
 *   header-value := &lt;any ascii characters except CR & LF&gt;<br>
 *   body-data := &lt;arbitrary data&gt;<br>
 * </code>
 *
 * <p>Note that body-data can contain another mulipart entity.  There
 * is limited support for single pass processing of such nested
 * streams.  The nested stream is <strong>required</strong> to have a
 * boundary token of the same length as the parent stream (see {@link
 * #setBoundary(byte[])}).
 *
 * <p>Here is an exaple of usage of this class.<br>
 *
 * <pre>
 *    try {
 *        MultipartStream multipartStream = new MultipartStream(input,
 *                                                              boundary);
 *        boolean nextPart = malitPartStream.skipPreamble();
 *        OutputStream output;
 *        while(nextPart) {
 *            header = chunks.readHeader();
 *            // process headers
 *            // create some output stream
 *            multipartStream.readBodyPart(output);
 *            nextPart = multipartStream.readBoundary();
 *        }
 *    } catch(MultipartStream.MalformedStreamException e) {
 *          // the stream failed to follow required syntax
 *    } catch(IOException) {
 *          // a read or write error occurred
 *    }
 *
 * </pre>
 *
 * @author <a href="mailto:Rafal.Krzewski@e-point.pl">Rafal Krzewski</a>
 * @author <a href="mailto:martinc@apache.org">Martin Cooper</a>
 * @author Sean C. Sullivan
 *
 * @version $Id: MultipartStream.java 467222 2006-10-24 03:17:11Z markt $
 */
public class MultipartStream
{

    // ----------------------------------------------------- Manifest constants


    /**
     * The maximum length of <code>header-part</code> that will be
     * processed (10 kilobytes = 10240 bytes.).
     * 请求头最多允许的字节长度
     */
    public static final int HEADER_PART_SIZE_MAX = 10240;


    /**
     * The default length of the buffer used for processing a request.
     */
    protected static final int DEFAULT_BUFSIZE = 4096;


    /**
     * A byte sequence that marks the end of <code>header-part</code>
     * (<code>CRLFCRLF</code>).
     * 回车 换行 回车 换行 作为请求头的结尾标示
     */
    protected static final byte[] HEADER_SEPARATOR = {0x0D, 0x0A, 0x0D, 0x0A};


    /**
     * A byte sequence that that follows a delimiter that will be
     * followed by an encapsulation (<code>CRLF</code>).
     * 回车和换行字符
     */
    protected static final byte[] FIELD_SEPARATOR = { 0x0D, 0x0A };


    /**
     * A byte sequence that that follows a delimiter of the last
     * encapsulation in the stream (<code>--</code>).
     *  - - 字节数组
     */
    protected static final byte[] STREAM_TERMINATOR = { 0x2D, 0x2D };


    // ----------------------------------------------------------- Data members


    /**
     * The input stream from which data is read.
     */
    private InputStream input;


    /**
     * The length of the boundary token plus the leading <code>CRLF--</code>.
     */
    private int boundaryLength;


    /**
     * The amount of data, in bytes, that must be kept in the buffer in order
     * to detect delimiters reliably.
     * 为了保证buffer中必须有boundary，因此设置buffer中必须保留的字节数
     */
    private int keepRegion;


    /**
     * The byte sequence that partitions the stream.
     */
    private byte[] boundary;


    /**
     * The length of the buffer used for processing the request.
     */
    private int bufSize;


    /**
     * The buffer used for processing the request.
     * 用来缓存输入流中一部分数据
     */
    private byte[] buffer;


    /**
     * The index of first valid character in the buffer.
     * <br>
     * 0 <= head < bufSize
     * buffer中缓存的数据，目前要读取的位置
     */
    private int head;


    /**
     * The index of last valid characer in the buffer + 1.
     * <br>
     * 0 <= tail <= bufSize
     * 目前buffer中缓存数据最终缓存的位置
     */
    private int tail;


    /**
     * The content encoding to use when reading headers.
     */
    private String headerEncoding;


    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor.
     *
     * @see #MultipartStream(InputStream, byte[], int)
     * @see #MultipartStream(InputStream, byte[])
     *
     */
    public MultipartStream()
    {
    }


    /**
     * <p> Constructs a <code>MultipartStream</code> with a custom size buffer.
     *
     * <p> Note that the buffer must be at least big enough to contain the
     * boundary string, plus 4 characters for CR/LF and double dash, plus at
     * least one byte of data.  Too small a buffer size setting will degrade
     * performance.
     *
     * @param input    The <code>InputStream</code> to serve as a data source.
     * @param boundary The token used for dividing the stream into
     *                 <code>encapsulations</code>.
     * @param bufSize  The size of the buffer to be used, in bytes.
     *
     *
     * @see #MultipartStream()
     * @see #MultipartStream(InputStream, byte[])
     *1.设置输入流。根据缓存大小，创建空的缓存字节数组对象。
     *2.根据传递进来的---------------------------7db15a14291cce 代表的字节数组，长度扩展4个字节，在前四个字节存储回车、换行、-、-字符。
     *3.设置boundaryLength长度为传进来的字节长度+4。
     *  设置keepRegion的长度为传进来的字节长度+3.
     *4.设置类属性boundary，就是4个字符+传进来的boundary
     *5.设置head和tail为0.
     */
    public MultipartStream(InputStream input,
                           byte[] boundary,
                           int bufSize)
    {
        this.input = input;
        this.bufSize = bufSize;
        this.buffer = new byte[bufSize];

        // We prepend CR/LF to the boundary to chop trailng CR/LF from
        // body-data tokens.
        this.boundary = new byte[boundary.length + 4];
        this.boundaryLength = boundary.length + 4;
        this.keepRegion = boundary.length + 3;
        this.boundary[0] = 0x0D;//13 回车
        this.boundary[1] = 0x0A;//10 换行
        this.boundary[2] = 0x2D;//45 -
        this.boundary[3] = 0x2D;//45 -
        System.arraycopy(boundary, 0, this.boundary, 4, boundary.length);

        head = 0;
        tail = 0;
    }


    /**
     * <p> Constructs a <code>MultipartStream</code> with a default size buffer.
     *
     * @param input    The <code>InputStream</code> to serve as a data source.
     * @param boundary The token used for dividing the stream into
     *                 <code>encapsulations</code>.
     *
     * @exception IOException when an error occurs.
     *
     * @see #MultipartStream()
     * @see #MultipartStream(InputStream, byte[], int)
     *
     */
    public MultipartStream(InputStream input,
                           byte[] boundary)
        throws IOException
    {
        this(input, boundary, DEFAULT_BUFSIZE);
    }


    // --------------------------------------------------------- Public methods


    /**
     * Retrieves the character encoding used when reading the headers of an
     * individual part. When not specified, or <code>null</code>, the platform
     * default encoding is used.

     *
     * @return The encoding used to read part headers.
     */
    public String getHeaderEncoding()
    {
        return headerEncoding;
    }


    /**
     * Specifies the character encoding to be used when reading the headers of
     * individual parts. When not specified, or <code>null</code>, the platform
     * default encoding is used.
     *
     * @param encoding The encoding used to read part headers.
     */
    public void setHeaderEncoding(String encoding)
    {
        headerEncoding = encoding;
    }


    /**
     * 读取一个字节从buffer中，如果buffer中已经没有数据可以读取了，则从输入流中读取数据，填满buffer集合
     * Reads a byte from the <code>buffer</code>, and refills it as
     * necessary.
     *
     * @return The next byte from the input stream.
     *
     * @exception IOException if there is no more data available.
     * 从buffer缓存中读取一个字节
     */
    public byte readByte()
        throws IOException
    {
        // Buffer depleted ?//buffer是否填满了，当头和尾相同时，表示已经填满了
        if (head == tail)
        {//如果填满了，则设置头为0，然后读取输入流中信息，读取到buffer中，tail返回总共读取了多少内容
            head = 0;
            // Refill.
            tail = input.read(buffer, head, bufSize);
            if (tail == -1)
            {//表示已经没有数据可以读出来了
                // No more data available.
                throw new IOException("No more data is available");
            }
        }
        //buffer中还存在缓存的数据可以读取，则读取head位置数据，同时将head向后移动一位
        return buffer[head++];
    }


    /**
     * Skips a <code>boundary</code> token, and checks whether more
     * <code>encapsulations</code> are contained in the stream.
     *
     * @return <code>true</code> if there are more encapsulations in
     *         this stream; <code>false</code> otherwise.
     *
     * @exception MalformedStreamException if the stream ends unexpecetedly or
     *                                     fails to follow required syntax.
     *先跳过boundary字节数组信息，然后看后两个字节内容，判断是否还继续存在其他包装信息
     */
    public boolean readBoundary()
        throws MalformedStreamException
    {
        byte[] marker = new byte[2];
        boolean nextChunk = false;

        head += boundaryLength;
        try
        {
            marker[0] = readByte();
            marker[1] = readByte();
            if (arrayequals(marker, STREAM_TERMINATOR, 2))//比较marker的两个字节是否都是-，如果是，则nextChunk=false，根据HTTP协议，说明该上传Form表单已经结束，该结束是以boundary+--结束的
            {
                nextChunk = false;
            }
            else if (arrayequals(marker, FIELD_SEPARATOR, 2))//比较marker的两个字节是否是回车、换行。如果是则说明nextChunk=true。
            {
                nextChunk = true;
            }
            else
            {
                throw new MalformedStreamException(
                        "Unexpected characters follow a boundary");
            }
        }
        catch (IOException e)
        {
            throw new MalformedStreamException("Stream ended unexpectedly");
        }
        return nextChunk;
    }


    /**
     * <p>Changes the boundary token used for partitioning the stream.
     *
     * <p>This method allows single pass processing of nested multipart
     * streams.
     *
     * <p>The boundary token of the nested stream is <code>required</code>
     * to be of the same length as the boundary token in parent stream.
     *
     * <p>Restoring the parent stream boundary token after processing of a
     * nested stream is left to the application.
     *
     * @param boundary The boundary to be used for parsing of the nested
     *                 stream.
     *
     * @exception IllegalBoundaryException if the <code>boundary</code>
     *                                     has a different length than the one
     *                                     being currently parsed.
     * 重新设置boundary内容。注意重新设置时，必须保证整个一次请求中boundary的字节长度是相同的
     */
    public void setBoundary(byte[] boundary)
        throws IllegalBoundaryException
    {
        if (boundary.length != boundaryLength - 4)
        {
            throw new IllegalBoundaryException(
                    "The length of a boundary token can not be changed");
        }
        System.arraycopy(boundary, 0, this.boundary, 4, boundary.length);
    }


    /**
     * <p>Reads the <code>header-part</code> of the current
     * <code>encapsulation</code>.
     *
     * <p>Headers are returned verbatim to the input stream, including the
     * trailing <code>CRLF</code> marker. Parsing is left to the
     * application.
     *
     * <p><strong>TODO</strong> allow limiting maximum header size to
     * protect against abuse.
     *
     * @return The <code>header-part</code> of the current encapsulation.
     *
     * @exception MalformedStreamException if the stream ends unexpecetedly.
     * 解析请求头字节，最终返回请求头字符串
     */
    public String readHeaders()
        throws MalformedStreamException
    {
        int i = 0;
        byte b[] = new byte[1];
        // to support multi-byte characters
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int sizeMax = HEADER_PART_SIZE_MAX;
        int size = 0;
        while (i < 4)//读取请求头中所有信息到baos输出流中
        {
            try
            {
                b[0] = readByte();//读取一个字节
            }
            catch (IOException e)
            {
                throw new MalformedStreamException("Stream ended unexpectedly");
            }
            size++;//读取字节长度累加1
            if (b[0] == HEADER_SEPARATOR[i])//判断该字节是否是结束分隔符匹配，如果是，则i++。如果不是，则i=0，说明一旦遇见不是连续的回车、换行、回车、换行时，都要重新计算。
            {
                i++;
            }
            else
            {
                i = 0;
            }
            if (size <= sizeMax)//如果目前读取的字节数小于最大允许的请求头字节数。则向输出流中写入该字节。
            {
                baos.write(b[0]);
            }
        }//读取请求头完毕。要么全部请求头读取完成，要么读取到请求头允许的最大字节数满为止。

        String headers = null;//根据请求编码，将请求头输出流中信息转换成字符串
        if (headerEncoding != null)
        {
            try
            {
                headers = baos.toString(headerEncoding);
            }
            catch (UnsupportedEncodingException e)
            {
                // Fall back to platform default if specified encoding is not
                // supported.不支持该字符编码，则直接按照默认字符编码生成字符串
                headers = baos.toString();
            }
        }
        else
        {
            headers = baos.toString();
        }
//最终返回请求头字符串
        return headers;
    }


    /**
     * <p>Reads <code>body-data</code> from the current
     * <code>encapsulation</code> and writes its contents into the
     * output <code>Stream</code>.
     *
     * <p>Arbitrary large amounts of data can be processed by this
     * method using a constant size buffer. (see {@link
     * #MultipartStream(InputStream,byte[],int) constructor}).
     * 任意数量的数据都会被处理掉。
     *
     * @param output The <code>Stream</code> to write data into.
     *
     * @return the amount of data written.
     *
     * @exception MalformedStreamException if the stream ends unexpectedly.
     * @exception IOException              if an i/o error occurs.
     * 读取body数据，并且写入到参数对应的输出流中。
     * 返回写入的总数
     */
    public int readBodyData(OutputStream output)
        throws MalformedStreamException,
               IOException
    {
        boolean done = false;//是否解析完成
        int pad;
        int pos;
        int bytesRead;
        int total = 0;//写入的字节数量
        while (!done)//只要没解析完成，都要继续解析
        {
            // Is boundary token present somewere in the buffer?
            pos = findSeparator();//查看是否还有boundary分隔信息。如果存在，则找到该位置
            if (pos != -1)
            {//存在，则将其内容全部填充到输出流中，结束循环
                // Write the rest of the data before the boundary.
                output.write(buffer, head, pos - head);
                total += pos - head;
                head = pos;
                done = true;
            }
            else
            {//如果不存在，则继续读取信息，然后再填充到输出流中。直到全部输入流都读取完为止。
                // Determine how much data should be kept in the
                // buffer.
                if (tail - head > keepRegion)
                {
                    pad = keepRegion;
                }
                else
                {
                    pad = tail - head;
                }
                // Write out the data belonging to the body-data.
                output.write(buffer, head, tail - head - pad);

                // Move the data to the beging of the buffer.
                total += tail - head - pad;
                System.arraycopy(buffer, tail - pad, buffer, 0, pad);

                // Refill buffer with new data.
                head = 0;
                bytesRead = input.read(buffer, pad, bufSize - pad);

                // [pprrrrrrr]
                if (bytesRead != -1)
                {
                    tail = pad + bytesRead;
                }
                else
                {
                    // The last pad amount is left in the buffer.
                    // Boundary can't be in there so write out the
                    // data you have and signal an error condition.
                    output.write(buffer, 0, pad);
                    output.flush();
                    total += pad;
                    throw new MalformedStreamException(
                            "Stream ended unexpectedly");
                }
            }
        }
        output.flush();
        return total;
    }


    /**
     * <p> Reads <code>body-data</code> from the current
     * <code>encapsulation</code> and discards it.
     *
     * <p>Use this method to skip encapsulations you don't need or don't
     * understand.
     *
     * @return The amount of data discarded.
     *
     * @exception MalformedStreamException if the stream ends unexpectedly.
     * @exception IOException              if an i/o error occurs.
     * 跳过一些字节，直接到boundary开始处
     */
    public int discardBodyData()
        throws MalformedStreamException,
               IOException
    {
        boolean done = false;
        int pad;
        int pos;
        int bytesRead;
        int total = 0;
        while (!done)
        {
            // Is boundary token present somewere in the buffer?
            pos = findSeparator();//是否有boundary
            if (pos != -1)//存在boundary
            {
                // Write the rest of the data before the boundary.
                total += pos - head;//total就是待跳过的字节数，即pos位置-头位置
                head = pos;//设置头为pos位置，即boundary位置
                done = true;//结束循环
            }
            else
            {//如果没有找到boundary
                // Determine how much data should be kept in the
                // buffer.
            	//如果buffer中剩余的字节数比最终保留的字节数要大，因此最终保留字符就是keepRegion个，将其赋值给pad
                if (tail - head > keepRegion)
                {
                    pad = keepRegion;
                }
                else
                {//如果剩余字节数比最终保留的字节数要小，则剩余的数全都都赋值给pad
                    pad = tail - head;
                }
                //最终将buffer内容过滤掉无用的，无用的就是tail-head-待保留的(pad)
                total += tail - head - pad;
//将buffer只保留剩余的pad
                // Move the data to the beging of the buffer.
                System.arraycopy(buffer, tail - pad, buffer, 0, pad);

                // Refill buffer with new data.
                head = 0;
                //再次读取输入流，填充buffer
                bytesRead = input.read(buffer, pad, bufSize - pad);

                // [pprrrrrrr]
                if (bytesRead != -1)
                {//有内容从输入流中读出，因此tail=读出的个数+原有剩余的pad个数
                    tail = pad + bytesRead;
                }
                else
                {//没有数据能读出，因此total再加上pad，即全部过滤掉
                    // The last pad amount is left in the buffer.
                    // Boundary can't be in there so signal an error
                    // condition.
                    total += pad;
                    throw new MalformedStreamException(
                            "Stream ended unexpectedly");
                }
            }
        }
        return total;
    }


    /**
     * Finds the beginning of the first <code>encapsulation</code>.
     *
     * @return <code>true</code> if an <code>encapsulation</code> was found in
     *         the stream.
     *
     * @exception IOException if an i/o error occurs.
     * 查看是否可以读取到下一个boundary，true表示可以
     */
    public boolean skipPreamble()
        throws IOException
    {
        // First delimiter may be not preceeded with a CRLF.
    	/**
    	 * 重新对boundary赋值，将前四位中的前两位，即回车换行去除。剩余--+boundary
    	 */
        System.arraycopy(boundary, 2, boundary, 0, boundary.length - 2);
        //boundary的长度，即boundaryLength，就是boundary内容的长度
        boundaryLength = boundary.length - 2;
        try
        {
            // Discard all data up to the delimiter.
            discardBodyData();//跳过一些字节，直接到boundary开始处

            // Read boundary - if succeded, the stream contains an
            // encapsulation.
            return readBoundary();//查看是否可以读取到下一个boundary，true表示可以
        }
        catch (MalformedStreamException e)
        {
            return false;
        }
        finally
        {
        	//重新赋值boundary，内容为4个字节+boundary
            // Restore delimiter.
            System.arraycopy(boundary, 0, boundary, 2, boundary.length - 2);
            boundaryLength = boundary.length;
            boundary[0] = 0x0D;
            boundary[1] = 0x0A;
        }
    }


    /**
     * Compares <code>count</code> first bytes in the arrays
     * <code>a</code> and <code>b</code>.
     *
     * @param a     The first array to compare.
     * @param b     The second array to compare.
     * @param count How many bytes should be compared.
     *
     * @return <code>true</code> if <code>count</code> first bytes in arrays
     *         <code>a</code> and <code>b</code> are equal.
     * 查看字节数组a和字节数组b的前count位是否相同，true表示相同，false表示不相同
     */
    public static boolean arrayequals(byte[] a,
                                      byte[] b,
                                      int count)
    {
        for (int i = 0; i < count; i++)
        {
            if (a[i] != b[i])
            {
                return false;
            }
        }
        return true;
    }


    /**
     * Searches for a byte of specified value in the <code>buffer</code>,
     * starting at the specified <code>position</code>.
     *
     * @param value The value to find.
     * @param pos   The starting position for searching.
     *
     * @return The position of byte found, counting from beginning of the
     *         <code>buffer</code>, or <code>-1</code> if not found.
     * 在buffer中从pos位置开始查找，一直到tail结束。找到第一个与value相同的字节位置。
     * 找不到则返回-1.
     */
    protected int findByte(byte value,
                           int pos)
    {
        for (int i = pos; i < tail; i++)
        {
            if (buffer[i] == value)
            {
                return i;
            }
        }

        return -1;
    }


    /**
     * Searches for the <code>boundary</code> in the <code>buffer</code>
     * region delimited by <code>head</code> and <code>tail</code>.
     *
     * @return The position of the boundary found, counting from the
     *         beginning of the <code>buffer</code>, or <code>-1</code> if
     *         not found.
     * 在buffer中寻找找到boundary，如果找到了则返回该位置，如果找不到，则返回-1
     */
    protected int findSeparator()
    {
        int first;
        int match = 0;//已经比配的长度
        int maxpos = tail - boundaryLength;
        for (first = head;
             (first <= maxpos) && (match != boundaryLength);
             first++)//从head开始循环，每次循环都都要head向后移动一位,同时只要符合目前匹配的数量与boundaryLength不同，则继续循环
        {
            first = findByte(boundary[0], first);//从first位置开始查找回车字符
            if (first == -1 || (first > maxpos))//没有找到，或者该位置已经大于maxpos，则都说明没有找到
            {
                return -1;
            }
            /**
             * 如果找到了开始字符。则从1开始循环，一直到boundary的长度。只要每一个buffer中字符都匹配上就可以，如果一旦不匹配，则跳出循环
             */
            for (match = 1; match < boundaryLength; match++)
            {
                if (buffer[first + match] != boundary[match])
                {
                    break;
                }
            }
        }
        if (match == boundaryLength)
        {//如果最终匹配的数量与boundaryLength长度相同，说明存在boundary，返回该boundary的位置，即first开始位置倒推一个。
            return first - 1;
        }
        return -1;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return The string representation of this object.
     */
    public String toString()
    {
        StringBuffer sbTemp = new StringBuffer();
        sbTemp.append("boundary='");
        sbTemp.append(String.valueOf(boundary));
        sbTemp.append("'\nbufSize=");
        sbTemp.append(bufSize);
        return sbTemp.toString();
    }

    /**
     * Thrown to indicate that the input stream fails to follow the
     * required syntax.
     */
    public class MalformedStreamException
        extends IOException
    {
        /**
         * Constructs a <code>MalformedStreamException</code> with no
         * detail message.
         */
        public MalformedStreamException()
        {
            super();
        }

        /**
         * Constructs an <code>MalformedStreamException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public MalformedStreamException(String message)
        {
            super(message);
        }
    }


    /**
     * Thrown upon attempt of setting an invalid boundary token.
     */
    public class IllegalBoundaryException
        extends IOException
    {
        /**
         * Constructs an <code>IllegalBoundaryException</code> with no
         * detail message.
         */
        public IllegalBoundaryException()
        {
            super();
        }

        /**
         * Constructs an <code>IllegalBoundaryException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public IllegalBoundaryException(String message)
        {
            super(message);
        }
    }


    // ------------------------------------------------------ Debugging methods


    // These are the methods that were used to debug this stuff.
    /*

    // Dump data.
    protected void dump()
    {
        System.out.println("01234567890");
        byte[] temp = new byte[buffer.length];
        for(int i=0; i<buffer.length; i++)
        {
            if (buffer[i] == 0x0D || buffer[i] == 0x0A)
            {
                temp[i] = 0x21;
            }
            else
            {
                temp[i] = buffer[i];
            }
        }
        System.out.println(new String(temp));
        int i;
        for (i=0; i<head; i++)
            System.out.print(" ");
        System.out.println("h");
        for (i=0; i<tail; i++)
            System.out.print(" ");
        System.out.println("t");
        System.out.flush();
    }

    // Main routine, for testing purposes only.
    //
    // @param args A String[] with the command line arguments.
    // @exception Exception, a generic exception.
    public static void main( String[] args )
        throws Exception
    {
        File boundaryFile = new File("boundary.dat");
        int boundarySize = (int)boundaryFile.length();
        byte[] boundary = new byte[boundarySize];
        FileInputStream input = new FileInputStream(boundaryFile);
        input.read(boundary,0,boundarySize);

        input = new FileInputStream("multipart.dat");
        MultipartStream chunks = new MultipartStream(input, boundary);

        int i = 0;
        String header;
        OutputStream output;
        boolean nextChunk = chunks.skipPreamble();
        while (nextChunk)
        {
            header = chunks.readHeaders();
            System.out.println("!"+header+"!");
            System.out.println("wrote part"+i+".dat");
            output = new FileOutputStream("part"+(i++)+".dat");
            chunks.readBodyData(output);
            nextChunk = chunks.readBoundary();
        }
    }

    */
}
