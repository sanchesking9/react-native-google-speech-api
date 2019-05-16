'use strict';
import React, {
  NativeModules,
  Platform,
  NativeEventEmitter
} from 'react-native';

const { RNGoogleSpeechApi } = NativeModules;
const eventEmitter = new NativeEventEmitter(RNGoogleSpeechApi);

class RCTGoogleSpeechApi {

  setApiKey(apiKey) {
    RNGoogleSpeechApi.setApiKey(apiKey);
  }

  start() {
    RNGoogleSpeechApi.startSpeech();
  }

  stop() {
    if(Platform.OS === 'ios') {
      return new Promise((resolve, reject) => {
        RNGoogleSpeechApi.cancelSpeech((error, data) => {
          if (error) {
            reject(new Error(error));
          } else {
            resolve(data);
          }
        });
      });
    } else {
      return new Promise((resolve, reject) => {
        RNGoogleSpeechApi.cancelSpeech(data => {
          resolve(data);
        }, error => {
          reject(new Error(error));
        });
      });
    }
  }

  addGoogleSpeechApiEventListener(listener) {
    eventEmitter.addListener('onSpeechToTextCustom', listener);
  }

  removeGoogleSpeechApiEventListener() {
    eventEmitter.removeAllListeners('onSpeechToTextCustom');
  }

  urgentStop() {
    RNGoogleSpeechApi.urgentCancelSpeech();
  }

}

module.exports = new RCTGoogleSpeechApi();
