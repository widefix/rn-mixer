#ifdef __cplusplus
#import "react-native-armnativesaudio.h"
#endif

#ifdef RCT_NEW_ARCH_ENABLED
#import "RNArmnativesaudioSpec.h"

@interface Armnativesaudio : NSObject <NativeArmnativesaudioSpec>
#else
#import <React/RCTBridgeModule.h>

@interface Armnativesaudio : NSObject <RCTBridgeModule>
#endif

@end
