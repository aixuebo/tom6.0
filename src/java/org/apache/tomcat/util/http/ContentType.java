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

package org.apache.tomcat.util.http;

/**
 * Usefull methods for Content-Type processing
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class ContentType {

    /**
     * Parse the character encoding from the specified content type header.
     * If the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned.
     *
     * @param contentType a content type header  例如:text/html; charset=UTF-8 或者text/html; charset="UTF-8";
     * 从请求头获取编码，主要获取charset=开始--到;号为止的字符串，如果该字符串有双引号包裹，则去除双引号。
     */
    public static String getCharsetFromContentType(String contentType) {

        if (contentType == null)
            return (null);
        int start = contentType.indexOf("charset=");
        if (start < 0)
            return (null);
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0)
            encoding = encoding.substring(0, end);
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\"")))
            encoding = encoding.substring(1, encoding.length() - 1);
        return (encoding.trim());

    }


    /**
     * Returns true if the given content type contains a charset component,
     * false otherwise.
     *
     * @param type Content type
     * @return true if the given content type contains a charset component,
     * false otherwise
     * 该方法返回该请求头，是否包含字符编码，因为格式是文件格式+";"+字符编码。因此如果该字符串没有;符号，表示肯定没有字符编码。则返回false。
     * 
     */
    public static boolean hasCharset(String type) {

        boolean hasCharset = false;

        int len = type.length();
        int index = type.indexOf(';');
        while (index != -1) {
            index++;
            while (index < len && Character.isSpace(type.charAt(index))) {//去除;号之后和charset之间所有的空格。
                index++;
            }
            if (index+8 < len
                    && type.charAt(index) == 'c'
                    && type.charAt(index+1) == 'h'
                    && type.charAt(index+2) == 'a'
                    && type.charAt(index+3) == 'r'
                    && type.charAt(index+4) == 's'
                    && type.charAt(index+5) == 'e'
                    && type.charAt(index+6) == 't'
                    && type.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = type.indexOf(';', index);
        }

        return hasCharset;
    }

}
