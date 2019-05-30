
package com.reactlibrary;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

//import org.apache.commons.io.IOUtils;

public class RNGoogleSpeechApiModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  private String apiKey;
  private boolean isStop = false;
//  private MediaRecorder mediaRecorder;
//  private String fileName = Environment.getExternalStorageDirectory() + "/record.3gp";
//  private Handler handler = new Handler(Looper.getMainLooper());
//  private boolean mStop = false;

  private SpeechService mSpeechService;
  private VoiceRecorder mVoiceRecorder;

  public RNGoogleSpeechApiModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  private final ServiceConnection mServiceConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder binder) {
      mSpeechService = SpeechService.from(binder);
      mSpeechService.addApi(apiKey);
      mSpeechService.addListener(mSpeechServiceListener);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
      mSpeechService = null;
    }

  };

  @ReactMethod
  public void startSpeech() {
    startVoiceRecorder();
  }

  @ReactMethod
  private void urgentCancelSpeech() {
    stopVoiceRecorder();

    if(mSpeechService != null) {
      // Stop Cloud Speech API
      mSpeechService.removeListener(mSpeechServiceListener);
      reactContext.unbindService(mServiceConnection);
      mSpeechService = null;
    }
  }

  @ReactMethod
  private void cancelSpeech() {
    stopVoiceRecorder();
  }

  private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

    @Override
    public void onVoiceStart() {
      if (mSpeechService != null) {
        try {
          mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
        } catch (Exception e) {
          mSpeechService.startRecognizing(16000);
        }
      }
    }

    @Override
    public void onVoice(byte[] data, int size) {
      if (mSpeechService != null) {
        mSpeechService.recognize(data, size);
      }
    }

    @Override
    public void onVoiceEnd() {
      if (mSpeechService != null) {
        mSpeechService.finishRecognizing();
      }
    }

  };

  private void startVoiceRecorder() {
    if (mVoiceRecorder != null) {
      mVoiceRecorder.stop();
    }
    isStop = false;
    mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
    mVoiceRecorder.start();
  }

  private void stopVoiceRecorder() {
    if (mVoiceRecorder != null) {
      isStop = true;
      mVoiceRecorder.stop();
      mVoiceRecorder = null;
    }
  }

  private final SpeechService.Listener mSpeechServiceListener =
          new SpeechService.Listener() {
            @Override
            public void onSpeechRecognized(final String text, final boolean isFinal) {
              if(!isStop) {
                WritableMap params = Arguments.createMap();

                if (!TextUtils.isEmpty(text)) {
                  params.putString("text", text);
                } else {
                  params.putString("text", "");
                }

                params.putBoolean("isFinal", isFinal);
                sendEvent(reactContext, "onSpeechToTextCustom", params);

                if (isFinal) {
                  if (mVoiceRecorder != null) {
                    mVoiceRecorder.dismiss();
                    stopVoiceRecorder();
                  }
                }
              }
            }
          };

  @ReactMethod
  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
    reactContext.bindService(new Intent(reactContext, SpeechService.class),
            mServiceConnection, Context.BIND_AUTO_CREATE);
  }

//  @ReactMethod
//  public void startSpeech() {
//    try {
//        mediaRecorder = new MediaRecorder();
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//        mediaRecorder.setOutputFile(fileName);
//        mediaRecorder.prepare();
//        mediaRecorder.start();
//        mStop = false;
//        handler.postDelayed(pollTask, 10);
//    } catch (IOException e) {
//        e.printStackTrace();
//    }
//  }

//  @ReactMethod
//  private void cancelSpeech(Callback result, Callback error) {
//    if (!mStop) {
//      mStop = true;
//      mediaRecorder.stop();
//      try {
//        InputStream stream = reactContext.getContentResolver()
//                .openInputStream(Uri.fromFile(new File(fileName)));
//        byte[] audioData = IOUtils.toByteArray(stream);
//        stream.close();
//
//        String base64EncodedData =
//                Base64.encodeBase64String(audioData);
//
//        sendPost(result, error, base64EncodedData);
//      } catch (FileNotFoundException e) {
//        error.invoke(String.valueOf(e));
//      } catch (IOException e) {
//        error.invoke(String.valueOf(e));
//      }
//    }
//  }

//  @ReactMethod
//  private void urgentCancelSpeech() {
//    if (!mStop) {
//      mStop = true;
//      mediaRecorder.stop();
//    }
//  }

//  private Runnable pollTask = new Runnable() {
//      @Override
//      public void run() {
//          WritableMap params = Arguments.createMap();
//      	  params.putInt("noiseLevel", getAmplitude() + 4);
//      	  sendEvent(reactContext, "onSpeechToTextCustom", params);
//          if(!mStop) {
//            handler.postDelayed(pollTask, 100);
//          }
//      }
//  };

//  private int getAmplitude() {
//      if (mediaRecorder != null)
//          return mediaRecorder.getMaxAmplitude() / 1300;
//      else
//          return 0;
//  }

//  private void sendPost(final Callback result, final Callback error, final String base64EncodedData) {
//       Thread thread = new Thread(new Runnable() {
//           @Override
//           public void run() {
//               try {
//                   URL url = new URL("https://speech.googleapis.com/v1/speech:recognize?key=" + apiKey);
//                   HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                   conn.setRequestMethod("POST");
//                   conn.setRequestProperty("Content-Type", "application/json");
//                   conn.setDoOutput(true);
//                   conn.setDoInput(true);
//
//                   JSONObject jsonConfig = new JSONObject();
//                   jsonConfig.put("encoding", "AMR");
//                   jsonConfig.put("sampleRateHertz", 8000);
//                   jsonConfig.put("languageCode", "en-US");
//                   jsonConfig.put("maxAlternatives", 1);
//
//                   JSONObject jsonAudio = new JSONObject();
//                   jsonAudio.put("content", base64EncodedData);
//
//                   JSONObject json = new JSONObject();
//                   json.put("config", jsonConfig);
//                   json.put("audio", jsonAudio);
//
//                   DataOutputStream os = new DataOutputStream(conn.getOutputStream());
//                   os.writeBytes(json.toString());
//                   os.flush();
//                   os.close();
//
//
//                   String reply;
//                   InputStream in = conn.getInputStream();
//                   StringBuffer sb = new StringBuffer();
//                   try {
//                       int chr;
//                       while ((chr = in.read()) != -1) {
//                           sb.append((char) chr);
//                       }
//                       reply = sb.toString();
//                   } finally {
//                       in.close();
//                   }
//
//                   if(reply.equals("{}")) {
//                       error.invoke("Error");
//                   } else {
//                       result.invoke(reply);
//                   }
//
//                   conn.disconnect();
//               } catch (Exception e) {
//                   error.invoke(String.valueOf(e));
//               }
//           }
//       });
//
//       thread.start();
//  }

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