// DO NOT something to effect to global AVAudioSession like setCategory.
// It is highly recommended to do this in JS to avoid conflict with other native modules.

#import <AVFoundation/AVFoundation.h>

#import "RNGoogleSpeechApi.h"

#define SAMPLE_RATE 16000.0f

@interface RNGoogleSpeechApi () <AVAudioRecorderDelegate, AVAudioPlayerDelegate>

@property (strong, nonatomic) AVAudioRecorder *audioRecorder;
@property (strong, nonatomic) AVAudioSession *audioSession;
@property (strong, nonatomic) NSString *apiKey;

@end

@implementation RNGoogleSpeechApi

RCT_EXPORT_MODULE();

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"onSpeechPartialResults", @"onSpeechResults", @"onSpeechError"];
}

- (NSString *) soundFilePath {
    NSArray *dirPaths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *docsDir = dirPaths[0];
    return [docsDir stringByAppendingPathComponent:@"sound.caf"];
}

RCT_EXPORT_METHOD(setApiKey:(NSString *)apiKey) {
    _apiKey = apiKey;
    NSURL *soundFileURL = [NSURL fileURLWithPath:[self soundFilePath]];
    NSDictionary *recordSettings = @{AVEncoderAudioQualityKey:@(AVAudioQualityMax),
                                     AVEncoderBitRateKey: @16,
                                     AVNumberOfChannelsKey: @1,
                                     AVSampleRateKey: @(SAMPLE_RATE)};
    NSError *error;
    _audioRecorder = [[AVAudioRecorder alloc]
                      initWithURL:soundFileURL
                      settings:recordSettings
                      error:&error];
    if (error) {
        NSLog(@"error: %@", error.localizedDescription);
    }
}

- (void)stopAudio {
    if (_audioRecorder.recording) {
        [_audioRecorder stop];
    }
}

RCT_EXPORT_METHOD(startSpeech) {
    _audioSession = [AVAudioSession sharedInstance];
    [_audioSession setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];
    [_audioRecorder record];
}

RCT_EXPORT_METHOD(cancelSpeech:(RCTResponseSenderBlock)callback) {
    [self stopAudio];

    NSString *service = @"https://speech.googleapis.com/v1/speech:recognize";
    service = [service stringByAppendingString:@"?key="];
    service = [service stringByAppendingString:_apiKey];

    NSData *audioData = [NSData dataWithContentsOfFile:[self soundFilePath]];
    NSDictionary *configRequest = @{@"encoding":@"LINEAR16",
                                    @"sampleRateHertz":@(SAMPLE_RATE),
                                    @"languageCode":@"en-GB",
                                    @"maxAlternatives":@30};
    NSDictionary *audioRequest = @{@"content":[audioData base64EncodedStringWithOptions:0]};
    NSDictionary *requestDictionary = @{@"config":configRequest,
                                        @"audio":audioRequest};
    NSError *error;
    NSData *requestData = [NSJSONSerialization dataWithJSONObject:requestDictionary
                                                          options:0
                                                            error:&error];

    NSString *path = service;
    NSURL *URL = [NSURL URLWithString:path];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:URL];
    // if your API key has a bundle ID restriction, specify the bundle ID like this:
    [request addValue:[[NSBundle mainBundle] bundleIdentifier] forHTTPHeaderField:@"X-Ios-Bundle-Identifier"];
    NSString *contentType = @"application/json";
    [request addValue:contentType forHTTPHeaderField:@"Content-Type"];
    [request setHTTPBody:requestData];
    [request setHTTPMethod:@"POST"];

    NSURLSessionTask *task =
    [[NSURLSession sharedSession]
     dataTaskWithRequest:request
     completionHandler:
     ^(NSData *data, NSURLResponse *response, NSError *error) {
         dispatch_async(dispatch_get_main_queue(),
                        ^{
                            NSString *stringResult = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
                            NSArray *events = @[stringResult];
                            callback(@[[NSNull null], events]);
                        });
     }];
    [task resume];

    [_audioSession setCategory:AVAudioSessionCategoryPlayback error:nil];
}

@end