import SwiftUI

struct ContentView: View {
    @StateObject private var audioManager = AudioManager()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack(spacing: 20) {
                    Button("Pick Audio Files") {
                        audioManager.pickAudioFile()
                    }
                    .padding()

                    Button("Play Mix") {
                        audioManager.playAudio()
                        
                    }
                    .padding()
                    .disabled(!audioManager.isMixBtnClicked)
                }

                ForEach(audioManager.audioProperties, id: \.self) { fileName in
                    VStack(spacing: 20) {
                        Text(fileName)
                            .padding(20)

                        if let volume = audioManager.audiosVolumesSliderValues[fileName] {
                            Slider(value: Binding(
                                get: {
                                    volume
                                },
                                set: { newValue in
                                    audioManager.audiosVolumesSliderValues[fileName] = newValue
                                    audioManager.editVolumeX(volume: Float(newValue), fileName: fileName)
                                }
                            ), in: 0...1, step: 0.01)
                            .padding(.horizontal)
                            .accentColor(.blue)
                            
                            
                        }
                        AudioVisualizer(amplitudes: Binding(
                                                  get: { audioManager.audioAmplitudes[fileName] ?? [] },
                                                  set: { _ in }
                                              ))
                                              .frame(height: 100)
                                              .padding(.horizontal)
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
        .padding()
        .navigationTitle("Audio Properties")
    }
}

struct PropView: View {
    var properties: [String: Any]

    var body: some View {
        VStack(alignment: .leading) {
            ForEach(properties.keys.sorted(), id: \.self) { key in
                if let value = properties[key] {
                    HStack {
                        Text(key)
                        Spacer()
                        Text("\(value)")
                    }
                }
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
