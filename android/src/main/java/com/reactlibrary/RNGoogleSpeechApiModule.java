
package com.reactlibrary;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.speech.v1beta1.Speech;
import com.google.api.services.speech.v1beta1.SpeechRequestInitializer;
import com.google.api.services.speech.v1beta1.model.RecognitionAudio;
import com.google.api.services.speech.v1beta1.model.RecognitionConfig;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeRequest;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeResponse;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RNGoogleSpeechApiModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  private String apiKey;
  private MediaRecorder mediaRecorder;
  private String fileName = Environment.getExternalStorageDirectory() + "/record.3gp";
  private Handler handler = new Handler(Looper.getMainLooper());
  private boolean mStop = false;

  public RNGoogleSpeechApiModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @ReactMethod
  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  @ReactMethod
  public void startSpeech() {
    try {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(fileName);
        mediaRecorder.prepare();
        mediaRecorder.start();
        mStop = false;
        handler.postDelayed(pollTask, 10);
    } catch (IOException e) {
        e.printStackTrace();
    }
  }

  @ReactMethod
  private void cancelSpeech(Callback result, Callback error) {
    if (!mStop) {
      mStop = true;
      mediaRecorder.stop();
      try {
        InputStream stream = reactContext.getContentResolver()
                .openInputStream(Uri.fromFile(new File(fileName)));
        byte[] audioData = IOUtils.toByteArray(stream);
        stream.close();

        String base64EncodedData =
                Base64.encodeBase64String(audioData);

        sendPost(result, error, base64EncodedData);
      } catch (FileNotFoundException e) {
        error.invoke(String.valueOf(e));
      } catch (IOException e) {
        error.invoke(String.valueOf(e));
      }
    }
  }

  @ReactMethod
  private void urgentCancelSpeech() {
    if (!mStop) {
      mStop = true;
      mediaRecorder.stop();
    }
  }

  private Runnable pollTask = new Runnable() {
      @Override
      public void run() {
          WritableMap params = Arguments.createMap();
      	  params.putInt("noiseLevel", getAmplitude() + 4);
      	  sendEvent(reactContext, "onSpeechToTextCustom", params);
          if(!mStop) {
            handler.postDelayed(pollTask, 100);
          }
      }
  };

  private int getAmplitude() {
      if (mediaRecorder != null)
          return mediaRecorder.getMaxAmplitude() / 1300;
      else
          return 0;
  }

  private void sendPost(final Callback result, final Callback error, final String base64EncodedData) {
       Thread thread = new Thread(new Runnable() {
           @Override
           public void run() {
               try {
                   URL url = new URL("https://speech.googleapis.com/v1/speech:recognize?key=" + apiKey);
                   HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                   conn.setRequestMethod("POST");
                   conn.setRequestProperty("Content-Type", "application/json");
                   conn.setDoOutput(true);
                   conn.setDoInput(true);

                   JSONObject jsonConfig = new JSONObject();
                   jsonConfig.put("encoding", "AMR");
                   jsonConfig.put("sampleRateHertz", 8000);
                   jsonConfig.put("languageCode", "en-US");
                   jsonConfig.put("maxAlternatives", 1);

                   JSONObject jsonAudio = new JSONObject();
                   jsonAudio.put("content", base64EncodedData);

                   JSONObject json = new JSONObject();
                   json.put("config", jsonConfig);
                   json.put("audio", jsonAudio);

                   DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                   os.writeBytes(json.toString());
                   os.flush();
                   os.close();


                   String reply;
                   InputStream in = conn.getInputStream();
                   StringBuffer sb = new StringBuffer();
                   try {
                       int chr;
                       while ((chr = in.read()) != -1) {
                           sb.append((char) chr);
                       }
                       reply = sb.toString();
                   } finally {
                       in.close();
                   }

                   if(reply.equals("{}")) {
                       error.invoke("Error");
                   } else {
                       result.invoke(reply);
                   }

                   conn.disconnect();
               } catch (Exception e) {
                   error.invoke(String.valueOf(e));
               }
           }
       });

       thread.start();
  }

  @Override
  public String getName() {
    return "RNGoogleSpeechApi";
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }
}