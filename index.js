'use strict';
import React, {
  NativeModules
} from 'react-native';

const { RNGoogleSpeechApi } = NativeModules;

class RCTGoogleSpeechApi {

  setApiKey(apiKey) {
    RNGoogleSpeechApi.setApiKey(apiKey);
  }

  start() {
    return new Promise((resolve, reject) => {
      RNGoogleSpeechApi.startSpeech();
    });
  }

  stop() {
    return new Promise((resolve, reject) => {
      RNGoogleSpeechApi.cancelSpeech((error, data) => {
        if (error) {
          reject(new Error(error));
        } else {
          resolve(data);
        }
      });
    });
  }

}

module.exports = new RCTGoogleSpeechApi();
