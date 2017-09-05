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

import java.io.IOException;
import org.apache.coyote.Request;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Input filter responsible for reading and buffering the request body, so that
 * it does not interfere with client SSL handshake messages.
 * 该类当request设置后,就已经读取完数据,后续真正读取的是那时候缓存的内容
 */
public class BufferedInputFilter implements InputFilter {

    // -------------------------------------------------------------- Constants

    private static final String ENCODING_NAME = "buffered";//具体的编码类型字符串形式
    private static final ByteChunk ENCODING = new ByteChunk();//编码类型


    // ----------------------------------------------------- Instance Variables

    private ByteChunk buffered = null;//最终存储buffer底层流的所有数据
    private ByteChunk tempRead = new ByteChunk(1024);//临时存储数据
    private InputBuffer buffer;//在什么流基础上进行的包装
    private boolean hasRead = false;


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Set the buffering limit. This should be reset every time the buffer is
     * used.
     */
    public void setLimit(int limit) {
        if (buffered == null) {
            buffered = new ByteChunk(4048);
            buffered.setLimit(limit);
        }
    }


    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Reads the request body and buffers it.
     */
    public void setRequest(Request request) {
        // save off the Request body
        try {
            while (buffer.doRead(tempRead, request) >= 0) {//不断的从buffer中获取信息,存储在临时的tempRead中
                buffered.append(tempRead);//将临时存储的内容写出到buffered中
                tempRead.recycle();
            }
        } catch(IOException iex) {
            // Ignore
        }
    }

    /**
     * Fills the given ByteChunk with the buffered request body.
     * 因为在setRequest方法的时候,已经将底层的内容缓存到buffered了,因此不需要再进行doRead调用,而是直接从buffered中获取
     */
    public int doRead(ByteChunk chunk, Request request) throws IOException {
        if (hasRead || buffered.getLength() <= 0) {
            return -1;
        } else {
            chunk.setBytes(buffered.getBytes(), buffered.getStart(),
                           buffered.getLength());//直接从buffered中获取数据,追加到chunk中
            hasRead = true;//设置说明已经读取完了
        }
        return chunk.getLength();
    }

    //设置该filter的基础流
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }

    public void recycle() {
        if (buffered != null) {
            if (buffered.getBuffer().length > 65536) {//说明缓冲区的内容太多了,因此设置为null,下次重新在创建新的
                buffered = null;
            } else {
                buffered.recycle();//清空缓冲流信息
            }
        }
        tempRead.recycle();
        hasRead = false;
        buffer = null;
    }

    public ByteChunk getEncodingName() {
        return ENCODING;
    }

    public long end() throws IOException {
        return 0;
    }

    //表示缓冲区还有多少字节未用
    public int available() {
        return buffered.getLength();
    }
    
}
