import SwiftUI

struct ContentView: View {
    @StateObject private var audioManager = AudioManager()
    @State private var isDownloadButtonClicked = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if audioManager.downloadProgress < 1.0 {
                    if !isDownloadButtonClicked {
                        Button("Download Audio Files") {
                            audioManager.downloadAudioFiles()
                            isDownloadButtonClicked = true
                        }
                        .padding()
                    }
                   if isDownloadButtonClicked {Text("Downloading... \(Int(audioManager.downloadProgress * 100))%")}
                    ProgressView(value: audioManager.downloadProgress)
                        .progressViewStyle(LinearProgressViewStyle())
                        .padding()
                } else {
                    VStack {
                        if audioManager.isMasterControlShowing {
                            Text("Master control").bold().padding()
                            HStack {
                                Button(!audioManager.isMixPaused ? "Pause Mix" : "Resume Mix") {
                                    audioManager.pauseResumeMix()
                                }
                            }.padding(15)
                            
                            Text("Song Progress")
                            
                            Slider(value: Binding(
                                get: {
                                    audioManager.audioProgressValues
                                },
                                set: { newValue in
                                    audioManager.audioProgressValues = newValue
                                    audioManager.setAudioProgress(point: newValue)
                                }
                            ), in: 0...1, step: 0.01,
                            onEditingChanged: { isEditing in
                                if !isEditing {
                                    audioManager.audioSliderChanged(point: audioManager.audioProgressValues)
                                }
                            })
                            .padding(.horizontal)
                            .accentColor(.blue)
                        }
                    }
                    
                    HStack(spacing: 20) {
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
                            
                            VStack {
                                HStack {
                                    Text("Pan: ")
                                        .padding(5)
                                    if let pan = audioManager.audiosPanSliderValues[fileName] {
                                        Slider(value: Binding(
                                            get: {
                                                pan
                                            },
                                            set: { newValue in
                                                audioManager.audiosPanSliderValues[fileName] = newValue
                                                audioManager.editPanX(pan: Float(newValue), fileName: fileName)
                                            }
                                        ), in: 0...1, step: 0.01)
                                        .padding(.horizontal)
                                        .accentColor(.blue)
                                    }
                                }
                                
                                HStack {
                                    Text("Vol: ")
                                        .padding(5)
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
                                }
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
