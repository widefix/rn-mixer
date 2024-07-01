# AudioBridge Module Documentation

## Overview

The `Armsaudio` module is a React Native module that provides audio playback, mixing, and control functionalities for iOS applications. It allows users to pick audio files, download audio tracks, play, pause, resume, and control various audio properties such as volume and pan.

## Table of Contents

1. [Installation](#installation)
2. [API Reference](#api-reference)
    - [Methods](#methods)
    - [Events](#events)
3. [Usage](#usage)
   
4. [Contributing](#contributing)
5. [License](#license)

## Installation

To install the `Armsaudio` module, follow these steps:

1. Ensure you have a React Native project setup.
2. Install the module via npm or yarn:
    ```bash
    npm install armsaudio
    ```
    or
    ```bash
    yarn add armsaudio
    ```
3. Link the native modules with:
    ```bash
    npx react-native link armsaudio
    ```

## API Reference

### Methods

-  **pickAudioFile**: Opens the document picker to select audio files.
  ```javascript
  //@depricated
  xmod.newAddon().pickAudioFile();
  ```

-  **resetApp**: Resets the application, stopping all audio and clearing data, and brings every variable to their default state.
  ```javascript
  xmod.newAddon().resetApp();
  ```
-  **playAudio**: As the name suggests its used to play the audio, and should be called when the audio download is completed.
  ```javascript
  xmod.newAddon().playAudio();
  ```
-  **pauseResumeMix**: This is used to pause & resume the mix
  ```javascript
  xmod.newAddon().pauseResumeMix();
  ```
-  **setVolume**: This is used to control the volume and it takes a float (volume) and string (file name) argument
  ```javascript
  xmod.newAddon().setVolume(0.5);
  ```
-  **setPan**: This is used to control the pan and it takes a float (volume) and string (file name) argument
  ```javascript
  xmod.newAddon().setPan(0.5);
  ```
-  **setAudioProgress**: This takes a double argument, when you start sliding, the point of departure should be called with function, this helps the library take note of the point the pointer was picked from and the current audio progress.
  ```javascript
  xmod.newAddon().setAudioProgress(0.5);
  ```
-  **audioSliderChanged**: This takesa double argument and its called with the point of arrival of your slider, this helps the library plays the song to the right exact postion you want to start playing
  ```javascript
  xmod.newAddon().audioSliderChanged(0.85);
  ```
-  **downloadAudioFiles**: This is function is called with a list of song url's and it will download the songs and stream them without persisiting to disk.
  ```javascript
  //@depricated
  xmod.newAddon().downloadAudioFiles(["https://<song-url>"]);
  ```

### Events

-  **DownloadProgress**: This event is used to get the progress of the files as the library downloads them internally 
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('DownloadProgress', (event) => {
      setProgress(event.progress); // 0...10%...55%...100%
    });
    ...
  ```

-  **DownloadComplete**: This event is used to notify you when the download has completed and also give you the list of the name of the files it was able to download
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('DownloadComplete', (event) => {
      setTracks(event.fileNames);
    });
    ...
  ```

-  **PlaybackProgress**: This event is used to keep track of the mix progress as it's playing, so you can use it to update your playback slider or whatever playback UI you have.
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('PlaybackProgress', (event) => {
      setplayBackProgress(event.progress); 
    });
    ...
  ```

-  **DownloadErrors**: This event is used to notify you of potential errors that may occur during the audio file download.
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('DownloadErrors', (event) => {
      seterrMessage(event.errMsg); 
    });
    ...
  ```
-  **TracksAmplitudes**: This event is used to notify you of the amplitude of each songs which you can to animate your visualise. This events are a dictionary of floating points streamed in real time per track as it plays
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('TracksAmplitudes', (event) => {
      setAmps(event.amplitudes); 
    });
    ...
  ```
-  **DownloadStart**: This event is used to notify when the download algorithms is invoked
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('DownloadStart', (event) => {
      setDwnStart(event.status); 
    });
    ...
  ```
-  **AppErrorsX**: This event is used to notify of all the errors in the library as they occur in real time.
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('AppErrorsX', (event) => {
      setGeneralErrors(event.errMsg); 
    });
    ...
  ```
-  **AppReset**: This event is used to notify you when the app is reset in the background
  ```javascript
  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('AppReset', (event) => {
      setAppReset(event.status); 
    });
    ...
  ```


### Usage

```javascript

import * as React from 'react';
import { StyleSheet, View, Text, Button, NativeEventEmitter } from 'react-native';
import * as xmod from "armsaudio";
import TrackControl from './Slider';
import PlayBackSlider from './PlaybackSlider';


export default function App() {
  const armsaudioEmitter = new NativeEventEmitter(xmod.newAddon());
  const [tracks, setTracks] = React.useState<string[]>([]);
  const [progress, setProgress] = React.useState(0);
  const [playBackProgress, setplayBackProgress] = React.useState(0);
  const [errMessage, seterrMessage] = React.useState(":::error messages:::");
  const audioUrls: string[] = [
    // "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/226/original/Way_Maker__0_-_E_-_Original_--_1-Click.m4a",
    // "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/228/original/Way_Maker__0_-_E_-_Original_--_2-Guide.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/236/original/Way_Maker__0_-_E_-_Original_--_11-Lead_Vox.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/227/original/Way_Maker__0_-_E_-_Original_--_3-Drums.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/229/original/Way_Maker__0_-_E_-_Original_--_4-Percussion.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/232/original/Way_Maker__0_-_E_-_Original_--_5-Bass.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/230/original/Way_Maker__0_-_E_-_Original_--_6-Acoustic.m4a",
    // "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/234/original/Way_Maker__0_-_E_-_Original_--_7-Electric_1.m4a",
    // "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/235/original/Way_Maker__0_-_E_-_Original_--_8-Electric_2.m4a",
    // "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/237/original/Way_Maker__0_-_E_-_Original_--_9-Main_Keys.m4a",
    // "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/231/original/Way_Maker__0_-_E_-_Original_--_10-Aux_Keys.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/238/original/Way_Maker__0_-_E_-_Original_--_12-Soprano_Vox.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/239/original/Way_Maker__0_-_E_-_Original_--_13-Tenor_Vox.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/233/original/Way_Maker__0_-_E_-_Original_--_14-Choir.m4a"
  ];

  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('DownloadProgress', (event) => {
      setProgress(event.progress);
    });

    const subscriptionTracks = armsaudioEmitter.addListener('DownloadComplete', (event) => {
      setTracks(event.fileNames);
    });

    const subscriptionPlaybackUpdate = armsaudioEmitter.addListener('PlaybackProgress', (event) => {
      setplayBackProgress(event.progress);
    });

    const subscriptionDownloadErrorMessage = armsaudioEmitter.addListener('DownloadErrors', (event) => {
      seterrMessage(event.errMsg);
    });

    const subscriptionTracksAmplitude = armsaudioEmitter.addListener('TracksAmplitudes', (event) => {
      console.log(event.amplitudes);
    });

    const subscriptionDownloadStart = armsaudioEmitter.addListener('DownloadStart', (event) => {
      console.log(event.status);
    });


    // Cleanup the subscription on unmount
    return () => {
      subscription.remove();
      subscriptionTracks.remove();
      subscriptionPlaybackUpdate.remove();
      subscriptionDownloadErrorMessage.remove();
      subscriptionTracksAmplitude.remove();
      subscriptionDownloadStart.remove();
    };
  }, []);

  const handleSlidingStart = (value: number) => {
    console.log("Sliding started at: " + value);
    xmod.newAddon().setAudioProgress(value)
  };

  const handleSlidingComplete = (value: number) => {
    console.log("Sliding completed at: " + value);
    xmod.newAddon().audioSliderChanged(value)
  };

  return (
    <View style={styles.container}>
      <Text style={{
        marginTop: 15, color: "#fff", backgroundColor: "grey",
        borderRadius: 20, padding: 10
      }}> {errMessage} </Text>
      <Text style={{ color: "grey" }}>Download Progress: {progress * 100}%</Text>
      <Button title='Open file' onPress={() => xmod.newAddon().pickAudioFile()}></Button>

      <Button title='Used Download files' onPress={() => xmod.newAddon().downloadAudioFiles(audioUrls)}></Button>

      <Button title='Play Mix' onPress={() => xmod.newAddon().playAudio()}></Button>

      <Button title='Pause/Resume' onPress={() => xmod.newAddon().pauseResumeMix()}></Button>

      <Text style={{ color: "grey" }}>Playback & Tracks: </Text>
      <PlayBackSlider
        initialValue={playBackProgress}
        onSlidingStart={handleSlidingStart}
        onSlidingComplete={handleSlidingComplete} />
      <TrackControl
        items={tracks}
        setVolumeX={(v, t) => xmod.newAddon().setVolume(v, t)}
        setPanX={(p, t) => xmod.newAddon().setPan(p, t)}
      />
    </View>
  );
}




```

### Contributing

- [Ndukwe Armstrong](https://www.linkedin.com/in/ndukwearmstrong/)