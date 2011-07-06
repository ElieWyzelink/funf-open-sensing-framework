package edu.mit.media.hd.funf.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import android.util.Log;

/**
 * Archives a file to the url specified using POST HTTP method.
 * 
 * NOTE: not complete or tested
 *
 */
public class HttpArchive implements RemoteArchive {

	public static final String TAG = HttpArchive.class.getName();
	
	private String uploadUrl;
	private String mimeType;
	
	public HttpArchive(final String uploadUrl) {
		this(uploadUrl, "application/x-binary");
	}
	
	public HttpArchive(final String uploadUrl, final String mimeType) {
		this.uploadUrl = uploadUrl;
		this.mimeType = mimeType;
	}
	
	public boolean add(File file) {
		/*
		HttpClient httpclient = new DefaultHttpClient();
		try {
		    httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		    HttpPost httppost = new HttpPost(uploadUrl);
		    
		    httppost.setEntity(new FileEntity(file, mimeType));
	
		    Log.i(TAG, "executing request " + httppost.getRequestLine());
		    HttpResponse response = httpclient.execute(httppost);
	
		    HttpEntity resEntity = response.getEntity();
		    if (resEntity == null) {
		    	Log.i(TAG, "Null response entity.");
		    	return false;
		    }
		    Log.i(TAG, "Response " + response.getStatusLine().getStatusCode() + ": " 
		    		+ IOUtils.inputStreamToString(resEntity.getContent(), "UTF-8"));
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.getLocalizedMessage());
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			Log.e(TAG, e.getLocalizedMessage());
			e.printStackTrace();
			return false;
		} finally {
		    httpclient.getConnectionManager().shutdown();
		}
	    return true;
		*/
		return uploadFile(file, uploadUrl);
	}
	
	
	/**
	 * Copied (and slightly modified) from Friends and Family
	 * @param file
	 * @param uploadurl
	 * @return
	 */
	public static boolean uploadFile(File file,String uploadurl) {
		HttpURLConnection conn = null; 
		DataOutputStream dos = null; 
		DataInputStream inStream = null; 

		String lineEnd = "\r\n"; 
		String twoHyphens = "--"; 
		String boundary =  "*****"; 


		int bytesRead, bytesAvailable, bufferSize; 
		byte[] buffer; 
		int maxBufferSize = 64*1024; //old value 1024*1024 

		boolean isSuccess = true;
		try 
		{ 
			//------------------ CLIENT REQUEST 
			FileInputStream fileInputStream = null;
			//Log.i("FNF","UploadService Runnable: 1"); 
			try {
				fileInputStream = new FileInputStream(file); 
			}catch (FileNotFoundException e) {
				e.printStackTrace();
				Log.e(TAG, "file not found");
			}
			// open a URL connection to the Servlet 
			URL url = new URL(uploadurl); 
			// Open a HTTP connection to the URL 
			conn = (HttpURLConnection) url.openConnection(); 
			// Allow Inputs 
			conn.setDoInput(true); 
			// Allow Outputs 
			conn.setDoOutput(true); 
			// Don't use a cached copy. 
			conn.setUseCaches(false); 
			// set timeout
			conn.setConnectTimeout(60000);
			conn.setReadTimeout(60000);
			// Use a post method. 
			conn.setRequestMethod("POST"); 
			conn.setRequestProperty("Connection", "Keep-Alive"); 
			conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary); 

			dos = new DataOutputStream( conn.getOutputStream() ); 
			dos.writeBytes(twoHyphens + boundary + lineEnd); 
			dos.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + file.getName() +"\"" + lineEnd); 
			dos.writeBytes(lineEnd); 

			//Log.i("FNF","UploadService Runnable:Headers are written"); 

			// create a buffer of maximum size 
			bytesAvailable = fileInputStream.available(); 
			bufferSize = Math.min(bytesAvailable, maxBufferSize); 
			buffer = new byte[bufferSize]; 

			// read file and write it into form... 
			bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
			while (bytesRead > 0) 
			{ 
				dos.write(buffer, 0, bufferSize); 
				bytesAvailable = fileInputStream.available(); 
				bufferSize = Math.min(bytesAvailable, maxBufferSize); 
				bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
			} 

			// send multipart form data necesssary after file data... 
			dos.writeBytes(lineEnd); 
			dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd); 

			// close streams 
			//Log.i("FNF","UploadService Runnable:File is written"); 
			fileInputStream.close(); 
			dos.flush(); 
			dos.close(); 
		} 
		catch (Exception e) 
		{ 
			Log.e("FNF", "UploadService Runnable:Client Request error", e);
			isSuccess = false;
		} 

		//------------------ read the SERVER RESPONSE 
		try {
			if (conn.getResponseCode() != 200) {
				isSuccess = false;
			}
		} catch (IOException e) {
			Log.e("FNF", "Connection error", e);
			isSuccess = false;
		}

		return isSuccess;
	}
}