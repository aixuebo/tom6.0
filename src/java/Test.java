import java.util.Hashtable;
import java.util.Iterator;

import javax.naming.Context;
import javax.naming.InitialContext;


public class Test {
	public static void main(String[] args) throws Exception{
		Hashtable<String, String> env=new Hashtable<String,String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY,"org.apache.naming.java.javaURLContextFactory");
        env.put(Context.PROVIDER_URL, "");
        env.put("maming", "mam"); 
        Context ctx = new InitialContext(env);
        
        //例如数据源name的查找 通过jndi服务
        String s = new String("hello world");
        ctx.bind("nb", s);
        System.out.println(ctx.lookup("nb"));
        
        //环境查看--带值的必要的环境属性
        Hashtable table = ctx.getEnvironment();
        Iterator<Object> iter = table.keySet().iterator();
        while(iter.hasNext()){
        	Object o = iter.next();
        	System.out.println(o+":"+table.get(o));
        }
        //
        
		
	}

}
