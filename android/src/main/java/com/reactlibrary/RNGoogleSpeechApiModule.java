
package com.reactlibrary;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

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
  private String language;
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
      mSpeechService.addListener(mSpeechServiceListener, apiKey, language);
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
    if (mSpeechService != null) {
      reactContext.unbindService(mServiceConnection);
    }
    Intent serviceIntent = new Intent(reactContext, SpeechService.class);
    reactContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
  }
  
  @ReactMethod
  public void setLanguage(String language) {
    this.language = language;
    if (mSpeechService != null) {
      mSpeechService.setLanguage(language);
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
