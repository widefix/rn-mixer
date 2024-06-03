import AVFoundation
import SwiftUI
import MobileCoreServices
import UIKit
import Combine

class AudioManager: NSObject, ObservableObject, UIDocumentPickerDelegate, AVAudioPlayerDelegate {
    @Published var audioPlayers: [String: AVAudioPlayer] = [:]
    @Published var audioFileURLs: [String: URL] = [:]
    @Published var audioProperties: [String] = []
    @Published var audiosVolumesSliderValues: [String: Double] = [:]
    @Published var audioAmplitudes: [String: [Float]] = [:]
    @Published var isMixBtnClicked:Bool = false

    private var cancellables = Set<AnyCancellable>()
    private var amplitudeTimers: [String: Timer] = [:]
    private var audioEngine = AVAudioEngine()
    
    override init() {
           super.init()// Set up observer for playback end
        // Set up observer for app lifecycle events
                NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(appWillTerminate), name: UIApplication.willTerminateNotification, object: nil)
       }
    
    // Function to pick audio files
    func pickAudioFile() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return
        }

        let documentPicker = UIDocumentPickerViewController(forOpeningContentTypes: [UTType.audio], asCopy: true)
        documentPicker.delegate = self
        documentPicker.allowsMultipleSelection = true
        windowScene.windows.first?.rootViewController?.present(documentPicker, animated: true, completion: nil)
    }

    // UIDocumentPickerDelegate method
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        for url in urls {
            let fileName = url.lastPathComponent
            audioFileURLs[fileName] = url
            getAudioProperties(for: url)
        }
        
        isMixBtnClicked = true
    }

    // Function to get properties of the audio file
    func getAudioProperties(for url: URL) {
        let asset = AVAsset(url: url)
        var properties: [String: Any] = [:]

        Task {
            do {
                let duration = try await asset.load(.duration)
                let durationInSeconds = CMTimeGetSeconds(duration)
                DispatchQueue.main.async {
                    properties["duration"] = durationInSeconds
                    // Fetch the file size
                    if let fileSize = try? FileManager.default.attributesOfItem(atPath: url.path)[FileAttributeKey.size] as? Int {
                        properties["fileSize"] = fileSize
                    }
                    properties["fileName"] = url.lastPathComponent
                    let fileName = url.lastPathComponent
                    self.audioProperties.append(fileName)
                    self.audiosVolumesSliderValues[fileName] = 0.5
                    self.audioAmplitudes[fileName] = Array(repeating: 0.0, count: 10)
                }
            } catch {
                print("Error loading duration: \(error.localizedDescription)")
            }
        }
    }
    
    func playAudio() {
        isMixBtnClicked = false
        DispatchQueue.global(qos: .userInitiated).async {
            var players: [String: AVAudioPlayer] = [:]
            let dispatchGroup = DispatchGroup()

            // Create and prepare AVAudioPlayer instances
            for (fileName, url) in self.audioFileURLs {
                dispatchGroup.enter()

                do {
                    let player = try AVAudioPlayer(contentsOf: url)
                    player.numberOfLoops = 0
                    player.isMeteringEnabled = true
                    player.delegate = self
                    players[fileName] = player
                    player.prepareToPlay()
                    dispatchGroup.leave()
                } catch {
                    print("Error creating audio player for \(fileName): \(error.localizedDescription)")
                    dispatchGroup.leave()
                }
            }

            // Ensure all players are prepared before starting playback
            dispatchGroup.notify(queue: .main) {
                let startDelay: TimeInterval = 0.5 // Delay to ensure all players are ready
                          let startTime = players.values.first?.deviceCurrentTime ?? 0 + startDelay
                          
                          // Start all players simultaneously
                          players.forEach { (fileName, player) in
                              player.play(atTime: startTime)
                              self.audioPlayers[fileName] = player
                              self.startAmplitudeUpdate(for: fileName)
                          }
            }
        }
    }


    func startAmplitudeUpdate(for fileName: String) {
        amplitudeTimers[fileName]?.invalidate() // Invalidate any existing timer
        amplitudeTimers[fileName] = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.updateAmplitude(for: fileName)
        }
    }

    func updateAmplitude(for fileName: String) {
        guard let player = audioPlayers[fileName] else { return }
        player.updateMeters()
        let averagePower = player.averagePower(forChannel: 0)
        let normalizedPower = max(0.0, (averagePower + 160) / 160)

        let volume = Float(audiosVolumesSliderValues[fileName] ?? 0.5)
        let adjustedPower = normalizedPower * volume

        var amplitudes = audioAmplitudes[fileName] ?? Array(repeating: 0.0, count: 10)
        amplitudes.removeFirst()
        amplitudes.append(adjustedPower)

        DispatchQueue.main.async {
            self.audioAmplitudes[fileName] = amplitudes
        }
    }

    func editVolumeX(volume: Float, fileName: String) {
        guard let player = audioPlayers[fileName] else {
            print("Player does not exist for \(fileName)")
            return
        }

        DispatchQueue.global(qos: .userInitiated).async {
            player.volume = volume
        }
    }
    
    // Function to reset the entire app to default state
        func resetApp() {
            audioPlayers.values.forEach { $0.stop() }
            amplitudeTimers.values.forEach { $0.invalidate() }

            audioPlayers.removeAll()
            audioFileURLs.removeAll()
            audioProperties.removeAll()
            audiosVolumesSliderValues.removeAll()
            audioAmplitudes.removeAll()

            isMixBtnClicked = false
        }

    // AVAudioPlayerDelegate method to handle playback end
        func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
            // Call resetApp after a delay of 1.5 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
                self.resetApp()
            }
        }

        // App lifecycle event handlers
        @objc func appDidEnterBackground() {
            resetApp()
        }

        @objc func appWillTerminate() {
            resetApp()
        }
}
