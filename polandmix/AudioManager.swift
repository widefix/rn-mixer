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
    @Published var audiosPanSliderValues: [String: Double] = [:]
    @Published var audioProgressValues: Double = 0
    @Published var audioValues: [String: Double] = [:]
    @Published var audioAmplitudes: [String: [Float]] = [:]
    @Published var isMixBtnClicked: Bool = false
    @Published var isMixPaused: Bool = false
    @Published var isMasterControlShowing: Bool = false
    private var players: [String: AVAudioPlayer] = [:]
    private var pausedTime:TimeInterval = 0.0
    private var playerDeviceCurrTime:TimeInterval = 0.0
    private var progressUpdateTimer: Timer?
    private var cancellables = Set<AnyCancellable>()
    private var amplitudeTimers: [String: Timer] = [:]
    private var audioEngine = AVAudioEngine()
    
    func startProgressUpdateTimer() {
        progressUpdateTimer?.invalidate()
        progressUpdateTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.updateProgress()
        }
    }

    func updateProgress() {
        guard let player = audioPlayers.values.first else { return }
        let progress = player.currentTime / player.duration
        DispatchQueue.main.async {
            self.audioProgressValues = progress
        }
    }
    
    override init() {
        super.init()
        // Set up observer for app lifecycle events
        NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appWillTerminate), name: UIApplication.willTerminateNotification, object: nil)
    }
    
    // Function to pick audio files
    func pickAudioFile() {
        resetApp()
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
            // Ensure the file can be accessed and read
            guard FileManager.default.isReadableFile(atPath: url.path) else {
                print("File not accessible: \(fileName)")
                continue
            }
            
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
                    self.audiosPanSliderValues[fileName] = 0.5
                    self.audioProgressValues = 0
                    self.audioAmplitudes[fileName] = Array(repeating: 0.0, count: 10)
                }
            } catch {
                print("Error loading duration: \(error.localizedDescription)")
            }
        }
    }
    
    func pauseResumeMix() {
        isMixPaused.toggle()
        
        if isMixPaused {
            for player in players.values {
                pausedTime = Double(floor((pausedTime + player.currentTime) / 2))
                playerDeviceCurrTime = player.deviceCurrentTime
                player.pause()
            }
            print("All players paused successfully \(pausedTime)")
        } else {
    
            for (_, player) in players {
               
                player.currentTime = pausedTime - 2.5
                player.play(atTime: playerDeviceCurrTime)
            }
        }
    }

    func playAudio() {
        startProgressUpdateTimer()
        isMixBtnClicked = false
        DispatchQueue.global(qos: .userInitiated).async {
            let dispatchGroup = DispatchGroup()

            for (fileName, url) in self.audioFileURLs {
                dispatchGroup.enter()

                do {
                    let player = try AVAudioPlayer(contentsOf: url)
                    player.numberOfLoops = 0
                    player.isMeteringEnabled = true
                    player.delegate = self
                    self.players[fileName] = player
                    player.prepareToPlay()
                    dispatchGroup.leave()
                } catch {
                    print("Error creating audio player for \(fileName): \(error.localizedDescription)")
                    dispatchGroup.leave()
                }
            }

            dispatchGroup.notify(queue: .main) {
                let startDelay: TimeInterval = 1 // Delay to ensure all players are ready
                let startTime = self.players.values.first?.deviceCurrentTime ?? startDelay
                
                print("\(startTime) ::::: This is the start-time init :::::")

                self.players.forEach { (fileName, player) in
                    player.currentTime = 0.0
                    player.play(atTime: startTime + startDelay)
                    
                    self.audioPlayers[fileName] = player
                    self.startAmplitudeUpdate(for: fileName)
                }
            }
        }
        
        isMasterControlShowing = true
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
    
    func setPlaybackPosition(for fileName: String, to progress: Double) {
        guard let player = audioPlayers[fileName] else { return }
        let duration = player.duration
        let newTime = progress * duration

        // Updating player time on the main thread to ensure sync
        DispatchQueue.main.sync {
            player.currentTime = newTime
        }
    }


    
    func setAudioProgress(point: Double) {
        audioProgressValues = point
//        for (fileName, player) in audioPlayers {
//                setPlaybackPosition(for: fileName, to: point)
//            }
    }

    func editPanX(pan:Float, fileName: String) {
        guard let player = audioPlayers[fileName] else {
            print("Player does not exist for \(fileName)")
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async {
            player.pan = pan
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
    
    func resetApp() {
            progressUpdateTimer?.invalidate() //
            audioEngine.reset()
            players.removeAll()
            // Stop all audio players
            audioPlayers.values.forEach { $0.stop() }
            
            // Invalidate amplitude update timers
            amplitudeTimers.values.forEach { $0.invalidate() }

            // Clear dictionaries and arrays
            audioPlayers.removeAll()
            audioFileURLs.removeAll()
            audioProperties.removeAll()
            audiosVolumesSliderValues.removeAll()
            audioAmplitudes.removeAll()
            pausedTime = 0.0
            playerDeviceCurrTime = 0.0

            // Reset flags
            isMixBtnClicked = false
            isMixPaused = false
            isMasterControlShowing = false
        
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
