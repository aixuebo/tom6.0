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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Util to process the "Accept-Language" header. Used by facade to implement
 * getLocale() and by StaticInterceptor.
 *
 * Not optimized - it's very slow.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class AcceptLanguage {

	/**
	 * 该类是一个粗糙的类，没有根据q打分去排列顺序，只是获取第一个语言为最终Locale对象。
	 * @param args
	 */
	public static void main(String[] args) {
		AcceptLanguage test = new AcceptLanguage();
		String acceptLanguage = "zh;q=0.8,zh-CN";
		System.out.println(test.getLocale(acceptLanguage));
	}
	/**
	 * 如果参数为null,返回本地编码，例如zh_CN
	 * @param acceptLanguage http协议中传递的Accept-Language的值：例如:zh-CN,zh;q=0.8
	 * @return
	 */
    public static Locale getLocale(String acceptLanguage) {
        if( acceptLanguage == null ) return Locale.getDefault();//如果参数为null,返回本地编码，例如zh_CN

        Hashtable<String,Vector<String>> languages = new Hashtable<String,Vector<String>>();//key表示分数，value表示相同分数的语言集合
            
        Vector<Double> quality = new Vector<Double>();//表示整个语言中，不重复的q的集合。
        processAcceptLanguage(acceptLanguage, languages, quality);//解析请求头，将其所有分数添加到quality集合中，将每个分数对应的语言集合添加到languages集合中。

        if (languages.size() == 0) return Locale.getDefault();//如果没有任何语言，则返回本地语言。

        Vector<Locale> l = new Vector<Locale>();
        extractLocales( languages,quality, l);

        return (Locale)l.elementAt(0);
    }

    public static Enumeration getLocales(String acceptLanguage) {
            // Short circuit with an empty enumeration if null header
        if (acceptLanguage == null) {
            Vector<Locale> v = new Vector<Locale>();
            v.addElement(Locale.getDefault());
            return v.elements();
        }
        
        Hashtable<String,Vector<String>> languages =
            new Hashtable<String,Vector<String>>();
        Vector<Double> quality=new Vector<Double>();
            processAcceptLanguage(acceptLanguage, languages , quality);

        if (languages.size() == 0) {
            Vector<Locale> v = new Vector<Locale>();
            v.addElement(Locale.getDefault());
            return v.elements();
        }
            Vector<Locale> l = new Vector<Locale>();
            extractLocales( languages, quality , l);
            return l.elements();
    }

    private static void processAcceptLanguage( String acceptLanguage,
            Hashtable<String,Vector<String>> languages, Vector<Double> q)
    {
        StringTokenizer languageTokenizer =
            new StringTokenizer(acceptLanguage, ",");

        while (languageTokenizer.hasMoreTokens()) {
            String language = languageTokenizer.nextToken().trim();//比如language = zh;q=0.8
            int qValueIndex = language.indexOf(';');
            int qIndex = language.indexOf('q');
            int equalIndex = language.indexOf('=');
            Double qValue = new Double(1);//q的默认值为1

            if (qValueIndex > -1 &&
                    qValueIndex < qIndex &&
                    qIndex < equalIndex) {
                    String qValueStr = language.substring(qValueIndex + 1);//;号之后的内容，例如：q=0.8
                language = language.substring(0, qValueIndex);//例如zh
                qValueStr = qValueStr.trim().toLowerCase();
                qValueIndex = qValueStr.indexOf('=');
                qValue = new Double(0);
                if (qValueStr.startsWith("q") &&
                    qValueIndex > -1) {
                    qValueStr = qValueStr.substring(qValueIndex + 1);//例如0.8
                    try {
                        qValue = new Double(qValueStr.trim());//赋值成double类型的值
                    } catch (NumberFormatException nfe) {
                    }
                }
            }

            // XXX
            // may need to handle "*" at some point in time

            if (! language.equals("*")) {
                String key = qValue.toString();//得到打分
                Vector<String> v;
                if (languages.containsKey(key)) {//是否包含该打分。languages的key是分数，value是相同分数的语言队列集合。
                    v = languages.get(key) ;
                } else {//如果没包含，则生成一个空集合，将q的值添加到集合汇总。
                    v= new Vector<String>();
                    q.addElement(qValue);//表示不重复出现的分数集合
                }
                v.addElement(language);
                languages.put(key, v);
            }
        }
    }

    /**
     * 摘了语言
     * @param languages
     * @param q
     * @param l
     */
    private static void extractLocales(Hashtable languages, Vector q,
            Vector<Locale> l)
    {
        // XXX We will need to order by q value Vector in the Future ?
        Enumeration e = q.elements();
        while (e.hasMoreElements()) {
            Vector v =
                (Vector)languages.get(((Double)e.nextElement()).toString());
            Enumeration le = v.elements();
            while (le.hasMoreElements()) {
                    String language = (String)le.nextElement();
                        String country = "";
                        int countryIndex = language.indexOf("-");
                if (countryIndex > -1) {
                    country = language.substring(countryIndex + 1).trim();
                    language = language.substring(0, countryIndex).trim();
                }
                l.addElement(new Locale(language, country));
            }
        }
    }


}
