package kyi.andwebsrv;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.http.conn.util.InetAddressUtils;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private boolean inProcessing = false;
	SdxServer webServer = null;
	static boolean in_processing;
	VideoFrame videoFrame_ = new VideoFrame(640*480*2);
	public byte[] preview_byte;
	Camera mcam;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		System.loadLibrary("natpmp");
		
		initCam();
		
		boolean init_chk = initWebServer();
		if(init_chk == false)
			Toast.makeText(this, "Init Server failed", Toast.LENGTH_SHORT).show();
		else
			Toast.makeText(this, "Init Server OK!", Toast.LENGTH_SHORT).show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		Log.i("KYI", "OnPause.");
		
		mcam.stopPreview();
		mcam.release();
		webServer.stop();
		
		super.onPause();
	}

	private void initCam(){
		mcam = Camera.open(1);
		
		// Setup parameter to get jpeg
		PreviewCallback preview_cb = new PreviewCallback(){

			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				// TODO Auto-generated method stub
				preview_byte = data;
			}
			
		};
		mcam.setPreviewCallback(preview_cb);
		////////////// preview end /////////////////////////////
		
		Camera.Parameters pram = mcam.getParameters();
		pram.setPreviewSize(640, 480);
		mcam.setParameters(pram);
		mcam.startPreview();
		Log.i("KYI", "Init Camera");
	}
	
	private boolean initWebServer() {
        final String ipAddr = getLocalIpAddress();
        if ( ipAddr != null ) {	// 웹서버 설정
            try{
                webServer = new SdxServer(8080, this); 
                webServer.registerCGI("/stream/live", doCapture);
            }catch (IOException e){
                webServer = null;
            }
        }
        if ( webServer != null) { // Show IP Addr
            final TextView txtv = (TextView)findViewById(R.id.testview);
            txtv.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					if(txtv.getText().equals(""))
						txtv.setText("http://" + ipAddr  + ":8080");
					else
						txtv.setText("");
				}
			});
			
            NatPMPClient natQuery = new NatPMPClient();
            natQuery.start();  // Thread of Webserver start (no loop thread)
            Log.d("KYI", "Init WebServer Success.");  
            return true;
        } else {
            Log.e("KYI", "Webserver connection Error");
            return false;
        }
    }
	
	public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(inetAddress.getHostAddress()) ) {
                        String ipAddr = inetAddress.getHostAddress();
                        return ipAddr;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.w("KYI", ex.toString());
        }
        return null;
    }  

    private SdxServer.CommonGatewayInterface doCapture = new SdxServer.CommonGatewayInterface () {
    	@Override
        public String run(Properties parms) {
           return null;
        }   
        
        @Override 
        public InputStream streaming(Properties parms) {
            // return 503 internal error
            if ( videoFrame_ == null) {
                Log.w("KYI", "No free videoFrame found!");
                return null;
            }

            // compress yuv to jpeg
            int picWidth = 640; ///////////////////////////////////////
            int picHeight = 480;
            Log.d("KYI", "doCapture, width : " + picWidth + ", height : " + picHeight);

            YuvImage newImage = new YuvImage(preview_byte, ImageFormat.NV21, picWidth, picHeight, null);
            videoFrame_.reset();
            boolean ret;
            inProcessing = true;
            try{
            	ret = newImage.compressToJpeg( new Rect(0,0,picWidth,picHeight), 30, videoFrame_);
            } catch (Exception ex) {
            	ret = false;    
            } 
            inProcessing = false;

            // compress success, return ok
            if ( ret == true)  {
            	parms.setProperty("mime", "image/mjpeg");
            	InputStream ins = videoFrame_.getInputStream();
            	return ins;
            }
            // send 503 error
            videoFrame_.release();

            return null;
        }
    };
	
	static private native String nativeQueryInternet();    
    private class NatPMPClient extends Thread {
        String queryResult;
        Handler handleQueryResult = new Handler(getMainLooper());  
        @Override
        public void run(){
            queryResult = nativeQueryInternet();
            if ( queryResult.startsWith("error:")) {
                handleQueryResult.post( new Runnable() {
                    @Override
                    public void run() {
                    	Log.i("KYI", "nativeQueryInternet run.");                      
                    }
                });
            } else {
                handleQueryResult.post( new Runnable() {
                    @Override
                    public void run() {
                        Log.i("KYI", "Access Internet : " + queryResult );
                    }
                });
            }
        }    
    }
}
