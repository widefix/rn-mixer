import * as React from 'react';
import { StyleSheet, View, Text, Button, NativeEventEmitter, Platform } from 'react-native';
import * as xmod from "armsaudio";
import TrackControl from './Slider';
import PlayBackSlider from './PlaybackSlider';

function Amplitudes({ amplitudes }: { amplitudes: { [key: string]: number } }) {
  return (
    <View>
      <View style={{ flexDirection: "row", justifyContent: "center", height: 120 }}>
        {Object.entries(amplitudes).map(([track, amplitude]) => (
          <View key={track + 'wrap'} style={{ flexDirection: "column-reverse", backgroundColor: "green", height: 100 }}>
            <View key={track} style={{ width: 20, height: amplitude * 100, backgroundColor: "red", margin: 1 }} />
          </View>
        ))}
      </View>
    </View>
  );
}

export default function App() {
  const armsaudioEmitter = new NativeEventEmitter(xmod.newAddon());
  const [tracks, setTracks] = React.useState<string[]>([]);
  const [amplitudes, setAmplitudes] = React.useState<{[key: string]: number}>({});
  const [progress, setProgress] = React.useState(0);
  const [playBackProgress, setplayBackProgress] = React.useState(0);
  const [errMessage, seterrMessage] = React.useState(":::error messages:::");
  const audioUrls: string[] = [
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/226/original/Way_Maker__0_-_E_-_Original_--_1-Click.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/228/original/Way_Maker__0_-_E_-_Original_--_2-Guide.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/236/original/Way_Maker__0_-_E_-_Original_--_11-Lead_Vox.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/227/original/Way_Maker__0_-_E_-_Original_--_3-Drums.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/229/original/Way_Maker__0_-_E_-_Original_--_4-Percussion.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/232/original/Way_Maker__0_-_E_-_Original_--_5-Bass.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/230/original/Way_Maker__0_-_E_-_Original_--_6-Acoustic.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/234/original/Way_Maker__0_-_E_-_Original_--_7-Electric_1.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/235/original/Way_Maker__0_-_E_-_Original_--_8-Electric_2.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/237/original/Way_Maker__0_-_E_-_Original_--_9-Main_Keys.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/231/original/Way_Maker__0_-_E_-_Original_--_10-Aux_Keys.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/238/original/Way_Maker__0_-_E_-_Original_--_12-Soprano_Vox.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/239/original/Way_Maker__0_-_E_-_Original_--_13-Tenor_Vox.m4a",
    "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/233/original/Way_Maker__0_-_E_-_Original_--_14-Choir.m4a"
  ];

  React.useEffect(() => {
    const subscription = armsaudioEmitter.addListener('DownloadProgress', (event) => {
      console.log(event);
      setProgress(event.progress);
    });

    const subscriptionTracks = armsaudioEmitter.addListener('DownloadComplete', (event) => {
      console.log(event);
      setTracks(event);
    });

    const subscriptionPlaybackUpdate = armsaudioEmitter.addListener('PlaybackProgress', (event) => {
      // setplayBackProgress(event);
      setplayBackProgress(event.progress);
    });

    const subscriptionDownloadErrorMessage = armsaudioEmitter.addListener('DownloadErrors', (event) => {
      console.log(event);
      seterrMessage(event);
    });

    const subscriptionTracksAmplitude = armsaudioEmitter.addListener('TracksAmplitudes', (event) => {
      console.log(event);
      setAmplitudes(event.amplitudes);
    });

    const subscriptionDownloadStart = armsaudioEmitter.addListener('DownloadStart', (event) => {
      console.log(event); // for download just use event
    });

    let subscriptionTestLibrary;
    if (Platform.OS === 'android') {
      subscriptionTestLibrary = armsaudioEmitter.addListener('Library link: true', (event) => {
        console.log(event);
      });
      xmod.newAddon().testLibrary();
    };

    // Cleanup the subscription on unmount
    return () => {
      subscription.remove();
      subscriptionTracks.remove();
      subscriptionPlaybackUpdate.remove();
      subscriptionDownloadErrorMessage.remove();
      subscriptionTracksAmplitude.remove();
      subscriptionDownloadStart.remove();
      if (subscriptionTestLibrary) {
        subscriptionTestLibrary.remove();
      }
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

      <View style={{ height: 20, marginVertical: 3 }}></View>
      <Button title='Used Download files' onPress={() => xmod.newAddon().downloadAudioFiles(audioUrls)}></Button>
      <View style={{ height: 20, marginVertical: 3 }}></View>
      <Button title='Play Mix' onPress={() => xmod.newAddon().playAudio()}></Button>
      <View style={{ height: 20, marginVertical: 3 }}></View>
      <Button title='Pause/Resume' onPress={() => xmod.newAddon().pauseResumeMix()}></Button>
      <View style={{ height: 20, marginVertical: 3 }}></View>
      <Text style={{ color: "grey" }}>Playback & Tracks: </Text>
      <PlayBackSlider
        initialValue={playBackProgress}
        onSlidingStart={handleSlidingStart}
        onSlidingComplete={handleSlidingComplete} />
      <Amplitudes amplitudes={amplitudes} />
      <TrackControl
        items={tracks}
        setVolumeX={(v, t) => xmod.newAddon().setVolume(v, t)}
        setPanX={(p, t) => xmod.newAddon().setPan(p, t)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
