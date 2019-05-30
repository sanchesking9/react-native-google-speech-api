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
    RNGoogleSpeechApi.cancelSpeech();
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
