'use strict';
import React, {
  NativeModules,
  Platform
} from 'react-native';

const { RNGoogleSpeechApi } = NativeModules;

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

  urgentStop() {
    RNGoogleSpeechApi.urgentCancelSpeech();
  }

}

module.exports = new RCTGoogleSpeechApi();
