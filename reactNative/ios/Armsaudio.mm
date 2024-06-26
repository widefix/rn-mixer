#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(Armsaudio, RCTEventEmitter)

RCT_EXTERN_METHOD(pickAudioFile)

RCT_EXTERN_METHOD(playAudio)

RCT_EXTERN_METHOD(resetApp)

RCT_EXTERN_METHOD(pauseResumeMix)

RCT_EXTERN_METHOD(setVolume:(float)volume forFileName:(NSString *)fileName)

RCT_EXTERN_METHOD(setPan:(float)pan forFileName:(NSString *)fileName)

RCT_EXTERN_METHOD(downloadAudioFiles:(NSArray<NSString *> *)urlStrings)

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

@end
