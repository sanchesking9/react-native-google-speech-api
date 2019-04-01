
package com.reactlibrary;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

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
  private static final int SAMPLING_RATE = 16000;
  private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
  private static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
  private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
  private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT);
  private static final String AUDIO_RECORDING_FILE_NAME = Environment.getExternalStorageDirectory() + "/recording.raw";
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
    new Thread(new Runnable() {
      @Override
      public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        byte audioData[] = new byte[BUFFER_SIZE];
        AudioRecord recorder = new AudioRecord(AUDIO_SOURCE,
                SAMPLING_RATE, CHANNEL_IN_CONFIG,
                AUDIO_FORMAT, BUFFER_SIZE);
        recorder.startRecording();

        BufferedOutputStream os = null;
        try {
          os = new BufferedOutputStream(new FileOutputStream(AUDIO_RECORDING_FILE_NAME));
        } catch (FileNotFoundException e) {
          Log.e("evert", "File not found for recording ", e);
        }

        while (!mStop) {
          int status = recorder.read(audioData, 0, audioData.length);
          double sumLevel = 0;

          for(int i = 0; i < status; i++) {
            sumLevel += audioData[i];
          }

          int level = (int) Math.abs((sumLevel / status));

          if(level >= 2) {
            WritableMap params = Arguments.createMap();
            params.putInt("noiseLevel", level + 6);
            sendEvent(reactContext, "onSpeechToTextCustom", params);
          }

          if (status == AudioRecord.ERROR_INVALID_OPERATION ||
                  status == AudioRecord.ERROR_BAD_VALUE) {
            return;
          }

          try {
            os.write(audioData, 0, status);
          } catch (IOException e) {
            return;
          }
        }

        try {
          os.close();
          recorder.stop();
          recorder.release();
          mStop = false;
        } catch (IOException e) {
          Log.e("evert", "Error when releasing", e);
        }
      }
    }).start();
  }

  @ReactMethod
  private void cancelSpeech(Callback result, Callback error) {
    if (!mStop) {
      mStop = true;
      try {
        InputStream stream = reactContext.getContentResolver()
                .openInputStream(Uri.fromFile(new File(AUDIO_RECORDING_FILE_NAME)));
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
                   jsonConfig.put("encoding", "LINEAR16");
                   jsonConfig.put("sampleRateHertz", 16000);
                   jsonConfig.put("languageCode", "en-GB");
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