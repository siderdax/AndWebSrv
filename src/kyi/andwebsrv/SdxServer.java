package kyi.andwebsrv;

import java.io.*;
import java.util.*;
import android.content.Context;
import android.util.Log;

public class SdxServer extends NanoHTTPD
{    
    public SdxServer(int port, Context ctx) throws IOException {
        super(port, ctx.getAssets());
    }
    
    public SdxServer(int port, String wwwroot) throws IOException {
        super(port, new File(wwwroot).getAbsoluteFile() );
    }

    @Override
    public Response serve( String uri, String method, Properties header, Properties parms, Properties files ) {
        Log.i("KYI", "httpd request >>" + method + " '" + uri + "' " + "   " + parms);
    
        if ( uri.startsWith("/cgi/") ) {
            return serveCGI(uri, method, header, parms, files);
        } else if ( uri.startsWith("/stream/") ) {
            return serveStream(uri, method, header, parms, files);
        } else {
            return super.serve(uri, method, header, parms, files); 
        }
	}

    public Response serveStream( String uri, String method, Properties header, Properties parms, Properties files ) {
    	Log.i("KYI", "ScxServer:serveStream");
        CommonGatewayInterface cgi = cgiEntries.get(uri);
        if ( cgi == null)
            return null;

        InputStream ins;
        ins = cgi.streaming(parms); //InputStream!
        if ( ins == null)
            return null;

        Random rnd = new Random();
        String etag = Integer.toHexString( rnd.nextInt() );
        String mime = parms.getProperty("mime");
        if ( mime == null)
            mime = "application/octet-stream";
        
        Response res = new Response( HTTP_OK, mime, ins);
        res.addHeader( "ETag", etag);
        res.isStreaming = true; 
        
        return res;
    }

    public Response serveCGI( String uri, String method, Properties header, Properties parms, Properties files ) {
        CommonGatewayInterface cgi = cgiEntries.get(uri);
        if ( cgi == null)
            return null;
        
        String msg = cgi.run(parms);
        if ( msg == null)
            return null;

        Response res = new Response( HTTP_OK, MIME_PLAINTEXT, msg);
        return res;
    }
    
    @Override
    public void serveDone(Response r) {
       try{
            if ( r.isStreaming ) { 
                r.data.close();
            }
       } catch(IOException ex) {
       }
    } 

    public static interface CommonGatewayInterface {
        public String run(Properties parms); 
        public InputStream streaming(Properties parms);
    }
    
    private HashMap<String, CommonGatewayInterface> cgiEntries = 
    		new HashMap<String, CommonGatewayInterface>();
    
    public void registerCGI(String uri, CommonGatewayInterface cgi) {
        if ( cgi != null)
			cgiEntries.put(uri, cgi);
    }

}
