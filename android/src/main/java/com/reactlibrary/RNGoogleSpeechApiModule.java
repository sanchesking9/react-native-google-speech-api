
package com.reactlibrary;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

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
  private static final int SAMPLING_RATE = 8000;
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

          if (status == AudioRecord.ERROR_INVALID_OPERATION ||
                  status == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("evert", "Error reading audio data!");
            return;
          }

          try {
            int bufferSize = 0;
            double average = 0.0;
            for (short s : audioData) {
              if(s>0) {
                average += Math.abs(s);
              } else {
                bufferSize--;
              }
            }
            int x = (int) Math.abs((average/bufferSize) / 2);
            WritableMap params = Arguments.createMap();
            params.putInt("noiseLevel", x);
            sendEvent(reactContext, "onSpeechToTextCustom", params);
            os.write(audioData, 0, status);
          } catch (IOException e) {
            Log.e("evert", "Error saving recording ", e);
            return;
          }
        }

        try {
          os.close();

          recorder.stop();
          recorder.release();

          Log.v("evert", "Recording done…");
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

        Speech speechService = new Speech.Builder(
                AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(),
                null
        ).setSpeechRequestInitializer(
                new SpeechRequestInitializer(apiKey))
                .build();
        RecognitionConfig recognitionConfig = new RecognitionConfig();
        recognitionConfig.setLanguageCode("en-GB");
        recognitionConfig.setSampleRate(8000);
        recognitionConfig.setEncoding("LINEAR16");
        recognitionConfig.setMaxAlternatives(1);
        RecognitionAudio recognitionAudio = new RecognitionAudio();
        recognitionAudio.setContent(base64EncodedData);

        SyncRecognizeRequest request = new SyncRecognizeRequest();
        request.setConfig(recognitionConfig);
        request.setAudio(recognitionAudio);

        SyncRecognizeResponse response = speechService.speech()
                .syncrecognize(request)
                .execute();
        if(String.valueOf(response).equals("{}")) {
          error.invoke("Error");
        } else {
          result.invoke(String.valueOf(response));
        }
      } catch (FileNotFoundException e) {
        error.invoke(String.valueOf(e));
      } catch (IOException e) {
        error.invoke(String.valueOf(e));
      }
    }
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