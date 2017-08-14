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


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;


/**
 * <p>High level API for processing file uploads.</p>
 *
 * <p>This class handles multiple files per single HTML widget, sent using
 * <code>multipart/mixed</code> encoding type, as specified by
 * <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>.  Use {@link
 * #parseRequest(HttpServletRequest)} to acquire a list of {@link
 * org.apache.tomcat.util.http.fileupload.FileItem}s associated with a given HTML
 * widget.</p>
 *
 * <p>How the data for individual parts is stored is determined by the factory
 * used to create them; a given part may be in memory, on disk, or somewhere
 * else.</p>
 *
 * @author <a href="mailto:Rafal.Krzewski@e-point.pl">Rafal Krzewski</a>
 * @author <a href="mailto:dlr@collab.net">Daniel Rall</a>
 * @author <a href="mailto:jvanzyl@apache.org">Jason van Zyl</a>
 * @author <a href="mailto:jmcnally@collab.net">John McNally</a>
 * @author <a href="mailto:martinc@apache.org">Martin Cooper</a>
 * @author Sean C. Sullivan
 *
 * @version $Id: FileUploadBase.java 467222 2006-10-24 03:17:11Z markt $
 */
public abstract class FileUploadBase
{

    // ---------------------------------------------------------- Class methods


    /**
     * Utility method that determines whether the request contains multipart
     * content.
     *
     * @param req The servlet request to be evaluated. Must be non-null.
     *
     * @return <code>true</code> if the request is multipart;
     *         <code>false</code> otherwise.
     *  true表示contentType中值是以multipart开头，该方法用来表示是否是上传
     */
    public static final boolean isMultipartContent(HttpServletRequest req)
    {
        String contentType = req.getHeader(CONTENT_TYPE);//获取Content-type的值
        if (contentType == null)
        {
            return false;
        }
        if (contentType.startsWith(MULTIPART))//是否以multipart/开头
        {
            return true;
        }
        return false;
    }


    // ----------------------------------------------------- Manifest constants


    /**
     * HTTP content type header name.
     */
    public static final String CONTENT_TYPE = "Content-type";


    /**
     * HTTP content disposition header name.
     */
    public static final String CONTENT_DISPOSITION = "Content-disposition";


    /**
     * Content-disposition value for form data.
     */
    public static final String FORM_DATA = "form-data";


    /**
     * Content-disposition value for file attachment.描述附件名称
     */
    public static final String ATTACHMENT = "attachment";


    /**
     * Part of HTTP content type header.
     */
    public static final String MULTIPART = "multipart/";


    /**
     * HTTP content type header for multipart forms.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";


    /**
     * HTTP content type header for multiple uploads.
     */
    public static final String MULTIPART_MIXED = "multipart/mixed";


    /**
     * The maximum length of a single header line that will be parsed
     * (1024 bytes).
     */
    public static final int MAX_HEADER_SIZE = 1024;


    // ----------------------------------------------------------- Data members


    /**
     * The maximum size permitted for an uploaded file. A value of -1 indicates
     * no maximum.
     * 允许上传的文件最大大小
     */
    private long sizeMax = -1;


    /**
     * The content encoding to use when reading part headers.
     * 上传的文件编码
     */
    private String headerEncoding;


    // ----------------------------------------------------- Property accessors


    /**
     * Returns the factory class used when creating file items.
     *
     * @return The factory class for new file items.
     */
    public abstract FileItemFactory getFileItemFactory();


    /**
     * Sets the factory class to use when creating file items.
     *
     * @param factory The factory class for new file items.
     */
    public abstract void setFileItemFactory(FileItemFactory factory);


    /**
     * Returns the maximum allowed upload size.
     *
     * @return The maximum allowed size, in bytes.
     *
     * @see #setSizeMax(long)
     * 
     *
     */
    public long getSizeMax()
    {
        return sizeMax;
    }


    /**
     * Sets the maximum allowed upload size. If negative, there is no maximum.
     *
     * @param sizeMax The maximum allowed size, in bytes, or -1 for no maximum.
     *
     * @see #getSizeMax()
     *
     */
    public void setSizeMax(long sizeMax)
    {
        this.sizeMax = sizeMax;
    }


    /**
     * Retrieves the character encoding used when reading the headers of an
     * individual part. When not specified, or <code>null</code>, the platform
     * default encoding is used.
     *
     * @return The encoding used to read part headers
     * 上传的文件编码
     * 
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


    // --------------------------------------------------------- Public methods


    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream. If files are stored
     * on disk, the path is given by <code>getRepository()</code>.
     *
     * @param req The servlet request to be parsed.
     *
     * @return A list of <code>FileItem</code> instances parsed from the
     *         request, in the order that they were transmitted.
     *
     * @exception FileUploadException if there are problems reading/parsing
     *                                the request or storing files.
     * 解析请求中上传form表单的内容。
     * 1.该请求的contentType必须以multipart/开头，并且该内容中包含boundary=字符串。否则抛出异常。
     * 2.必须req.getContentLength();有值。并且该值小于允许文件的最大字节数。否则都抛出异常。
     * 3.
     */
    public List /* FileItem */ parseRequest(HttpServletRequest req)
        throws FileUploadException
    {
        if (null == req)
        {
            throw new NullPointerException("req parameter");
        }

        ArrayList items = new ArrayList();
        String contentType = req.getHeader(CONTENT_TYPE);//获得Content-type字符串

        if ((null == contentType) || (!contentType.startsWith(MULTIPART)))//如果不是multipart/开头，则抛出异常
        {
            throw new InvalidContentTypeException(
                "the request doesn't contain a "
                + MULTIPART_FORM_DATA
                + " or "
                + MULTIPART_MIXED
                + " stream, content type header is "
                + contentType);
        }
        int requestSize = req.getContentLength();

        if (requestSize == -1)
        {
            throw new UnknownSizeException(
                "the request was rejected because it's size is unknown");
        }

        if (sizeMax >= 0 && requestSize > sizeMax)
        {
            throw new SizeLimitExceededException(
                "the request was rejected because "
                + "it's size exceeds allowed range");
        }

        try
        {
            int boundaryIndex = contentType.indexOf("boundary=");//boundary=---------------------------7db15a14291cce
            if (boundaryIndex < 0)
            {
                throw new FileUploadException(
                        "the request was rejected because "
                        + "no multipart boundary was found");
            }
            byte[] boundary = contentType.substring(
                    boundaryIndex + 9).getBytes();//contentType内容中从boundary=之后的字符串，拆分成字节数组,即---------------------------7db15a14291cce拆分成字节数组

            InputStream input = req.getInputStream();

            MultipartStream multi = new MultipartStream(input, boundary);
            multi.setHeaderEncoding(headerEncoding);

            boolean nextPart = multi.skipPreamble();
            while (nextPart)
            {
                Map headers = parseHeaders(multi.readHeaders());//解析HTTP请求头，返回Map对象。其中multi.readHeaders()得到请求头部分字节
                String fieldName = getFieldName(headers);//获取域名称
                if (fieldName != null)//域名称存在，则进行上传处理
                {
                    String subContentType = getHeader(headers, CONTENT_TYPE);//获得Content-type的值
                    if (subContentType != null && subContentType
                                                .startsWith(MULTIPART_MIXED))//如果该值是以multipart/mixed开头
                    {
                        // Multiple files.
                        byte[] subBoundary =
                            subContentType.substring(
                                subContentType
                                .indexOf("boundary=") + 9).getBytes();
                        multi.setBoundary(subBoundary);
                        boolean nextSubPart = multi.skipPreamble();
                        while (nextSubPart)
                        {
                            headers = parseHeaders(multi.readHeaders());
                            if (getFileName(headers) != null)
                            {
                                FileItem item =
                                        createItem(headers, false);
                                OutputStream os = item.getOutputStream();
                                try
                                {
                                    multi.readBodyData(os);
                                }
                                finally
                                {
                                    os.close();
                                }
                                items.add(item);
                            }
                            else
                            {
                                // Ignore anything but files inside
                                // multipart/mixed.
                                multi.discardBodyData();
                            }
                            nextSubPart = multi.readBoundary();
                        }
                        multi.setBoundary(boundary);
                    }
                    else
                    {
                        if (getFileName(headers) != null)
                        {
                            // A single file.
                            FileItem item = createItem(headers, false);
                            OutputStream os = item.getOutputStream();
                            try
                            {
                                multi.readBodyData(os);
                            }
                            finally
                            {
                                os.close();
                            }
                            items.add(item);
                        }
                        else
                        {
                            // A form field.
                            FileItem item = createItem(headers, true);
                            OutputStream os = item.getOutputStream();
                            try
                            {
                                multi.readBodyData(os);
                            }
                            finally
                            {
                                os.close();
                            }
                            items.add(item);
                        }
                    }
                }
                else
                {
                    // Skip this part. 跳过该部分
                    multi.discardBodyData();
                }
                nextPart = multi.readBoundary();
            }
        }
        catch (IOException e)
        {
            throw new FileUploadException(
                "Processing of " + MULTIPART_FORM_DATA
                    + " request failed. " + e.getMessage());
        }

        return items;
    }


    // ------------------------------------------------------ Protected methods


    /**
     * Retrieves the file name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     *
     * @return The file name for the current <code>encapsulation</code>.
     * 获取上传的文件名称
     */
    protected String getFileName(Map /* String, String */ headers)
    {
        String fileName = null;
        String cd = getHeader(headers, CONTENT_DISPOSITION);//获取Content-disposition的值
        if (cd.startsWith(FORM_DATA) || cd.startsWith(ATTACHMENT))//该值中必须以form-data开头。或者以attachment开头。其中有filename="" 这其中夹杂的就是上传的文件名称
        {
            int start = cd.indexOf("filename=\"");
            int end = cd.indexOf('"', start + 10);
            if (start != -1 && end != -1)
            {
                fileName = cd.substring(start + 10, end).trim();
            }
        }
        return fileName;
    }


    /**
     * Retrieves the field name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     *
     * @return The field name for the current <code>encapsulation</code>.
     * 通过请求头，得到上传的域名称
     */
    protected String getFieldName(Map /* String, String */ headers)
    {
        String fieldName = null;
        String cd = getHeader(headers, CONTENT_DISPOSITION);//获取Content-disposition的值
        if (cd != null && cd.startsWith(FORM_DATA))//该值中必须以form-data开头。其中有name="" 这其中夹杂的就是上传的域名称
        {
            int start = cd.indexOf("name=\"");
            int end = cd.indexOf('"', start + 6);
            if (start != -1 && end != -1)
            {
                fieldName = cd.substring(start + 6, end);
            }
        }
        return fieldName;
    }


    /**
     * Creates a new {@link FileItem} instance.
     *
     * @param headers       A <code>Map</code> containing the HTTP request
     *                      headers.
     * @param isFormField   Whether or not this item is a form field, as
     *                      opposed to a file.
     *
     * @return A newly created <code>FileItem</code> instance.
     *
     * @exception FileUploadException if an error occurs.
     */
    protected FileItem createItem(Map /* String, String */ headers,
                                  boolean isFormField)
        throws FileUploadException
    {
        return getFileItemFactory().createItem(getFieldName(headers),
                getHeader(headers, CONTENT_TYPE),
                isFormField,
                getFileName(headers));
    }


    /**
     * <p> Parses the <code>header-part</code> and returns as key/value
     * pairs.
     *
     * <p> If there are multiple headers of the same names, the name
     * will map to a comma-separated list containing the values.
     *
     * @param headerPart The <code>header-part</code> of the current
     *                   <code>encapsulation</code>.
     *
     * @return A <code>Map</code> containing the parsed HTTP request headers.
     * 解析http头，将请求头存放到Map中返回
     */
    protected Map /* String, String */ parseHeaders(String headerPart)
    {
        Map headers = new HashMap();
        char buffer[] = new char[MAX_HEADER_SIZE];
        boolean done = false;
        int j = 0;
        int i;
        String header, headerName, headerValue;
        try
        {
            while (!done)
            {
                i = 0;
                // Copy a single line of characters into the buffer,
                // omitting trailing CRLF.
                while (i < 2 || buffer[i - 2] != '\r' || buffer[i - 1] != '\n')//一次读取一行信息，直到回车换行为止，都要存放到buffer中
                {
                    buffer[i++] = headerPart.charAt(j++);
                }
                header = new String(buffer, 0, i - 2);//将一行信息转换为header字符串
                if (header.equals(""))//如果字符串为空，根据HTTP协议,说明解析已经结束了。
                {
                    done = true;
                }
                else
                {//对请求头解析，按照:进行拆分，name是之前的值，value是之后的值
                    if (header.indexOf(':') == -1)
                    {
                        // This header line is malformed, skip it.
                        continue;
                    }
                    headerName = header.substring(0, header.indexOf(':'))
                        .trim().toLowerCase();
                    headerValue =
                        header.substring(header.indexOf(':') + 1).trim();
                    if (getHeader(headers, headerName) != null)//如果name不存在，则将其添加，如果存在，则将其与原来存在的值用逗号分隔
                    {
                        // More that one heder of that name exists,
                        // append to the list.
                        headers.put(headerName,
                                    getHeader(headers, headerName) + ','
                                        + headerValue);
                    }
                    else
                    {
                        headers.put(headerName, headerValue);
                    }
                }
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            // Headers were malformed. continue with all that was
            // parsed.
        }
        return headers;
    }


    /**
     * Returns the header with the specified name from the supplied map. The
     * header lookup is case-insensitive.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     * @param name    The name of the header to return.
     *
     * @return The value of specified header, or a comma-separated list if
     *         there were multiple headers of that name.
     *         根据请求头name，得到请求头值
     */
    protected final String getHeader(Map /* String, String */ headers,
                                     String name)
    {
        return (String) headers.get(name.toLowerCase());
    }


    /**
     * Thrown to indicate that the request is not a multipart request.
     */
    public static class InvalidContentTypeException
        extends FileUploadException
    {
        /**
         * Constructs a <code>InvalidContentTypeException</code> with no
         * detail message.
         */
        public InvalidContentTypeException()
        {
            super();
        }

        /**
         * Constructs an <code>InvalidContentTypeException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public InvalidContentTypeException(String message)
        {
            super(message);
        }
    }


    /**
     * Thrown to indicate that the request size is not specified.
     */
    public static class UnknownSizeException
        extends FileUploadException
    {
        /**
         * Constructs a <code>UnknownSizeException</code> with no
         * detail message.
         */
        public UnknownSizeException()
        {
            super();
        }

        /**
         * Constructs an <code>UnknownSizeException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public UnknownSizeException(String message)
        {
            super(message);
        }
    }


    /**
     * Thrown to indicate that the request size exceeds the configured maximum.
     */
    public static class SizeLimitExceededException
        extends FileUploadException
    {
        /**
         * Constructs a <code>SizeExceededException</code> with no
         * detail message.
         */
        public SizeLimitExceededException()
        {
            super();
        }

        /**
         * Constructs an <code>SizeExceededException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public SizeLimitExceededException(String message)
        {
            super(message);
        }
    }

}
