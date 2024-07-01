import AVFoundation
import SwiftUI
import MobileCoreServices
import UIKit
import Combine
import React

@objc(Armsaudio)
class Armsaudio: RCTEventEmitter, ObservableObject, UIDocumentPickerDelegate, AVAudioPlayerDelegate  {
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
    @Published var downloadProgress: Double = 0
    private var players: [String: AVAudioPlayer] = [:]
    private var pausedTime: TimeInterval = 0.0
    private var playerDeviceCurrTime: TimeInterval = 0.0
    private var progressUpdateTimer: Timer?
    private var cancellables = Set<AnyCancellable>()
    private var amplitudeTimers: [String: Timer] = [:]
    private var audioEngine = AVAudioEngine()

    override init() {
        super.init()
        configureAudioSession()
        NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appWillTerminate), name: UIApplication.willTerminateNotification, object: nil)
    }
      override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func supportedEvents() -> [String]! {
    return ["DownloadProgress", 
            "DownloadComplete", 
            "AppReset",
            "PlaybackProgress",
            "DownloadErrors",
            "TracksAmplitudes",
            "AppErrorsX",
            "DownloadStart"]
   }

    func sendProgressUpdate(_ progress: Double) {
        self.sendEvent(withName: "DownloadProgress", body: ["progress": progress])
    }
    
    func sendTrackAmplitueUpdate() {
        self.sendEvent(withName: "TracksAmplitudes", body: ["amplitudes": self.audioAmplitudes])
    }
    
    func sendDownloadErrors(errors: String) {
        self.sendEvent(withName: "DownloadErrors", body: ["errMsg": errors])
    }
    
    func sendGenAppErrors(errors: String) {
        self.sendEvent(withName: "AppErrorsX", body: ["errMsg": errors])
    }

    func sendDownloadStart() {
        self.sendEvent(withName: "DownloadStart", body: ["status": "DownloadStart"])
    }
    
  @objc func startProgressUpdateTimer() {
    DispatchQueue.main.async {
        print("Starting progress update timer")
        self.progressUpdateTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self else {
                print("Self is nil")
                return
            }
            self.updateProgress()
        }
        print("Timer is valid: \(self.progressUpdateTimer?.isValid ?? false)")
    }
}

   @objc func updateProgress() {
    guard let player = players.values.first else {
        sendGenAppErrors(errors: "No active player found")
        return
    }
    let progress = player.currentTime / player.duration
    DispatchQueue.main.async {
        self.audioProgressValues = progress
        self.sendEvent(withName: "PlaybackProgress", body: ["progress": progress])
    }
}
    
   @objc func pickAudioFile() {
    resetApp()
    DispatchQueue.main.async {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return
        }

        let documentPicker: UIDocumentPickerViewController
        if #available(iOS 14.0, *) {
            documentPicker = UIDocumentPickerViewController(forOpeningContentTypes: [UTType.audio], asCopy: true)
        } else {
            // Fallback on earlier versions
            documentPicker = UIDocumentPickerViewController(documentTypes: ["public.audio"], in: .import)
        }

        documentPicker.delegate = self
        documentPicker.allowsMultipleSelection = true
        windowScene.windows.first?.rootViewController?.present(documentPicker, animated: true, completion: nil)
    }
}

   func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        for url in urls {
            let fileName = url.lastPathComponent
            guard FileManager.default.isReadableFile(atPath: url.path) else {
                print("File not accessible: \(fileName)")
                continue
            }
            
            audioFileURLs[fileName] = url
            getAudioProperties(for: url)
        }
        isMixBtnClicked = true
    }

    @objc func playAudio() {
        startProgressUpdateTimer()
        isMixBtnClicked = false
        DispatchQueue.global(qos: .userInitiated).async {
            let dispatchGroup = DispatchGroup()

            for (fileName, player) in self.audioPlayers {
                dispatchGroup.enter()
                
                player.numberOfLoops = 0
                player.isMeteringEnabled = true
                player.delegate = self
                self.players[fileName] = player
                player.prepareToPlay()
                dispatchGroup.leave()
            }

            dispatchGroup.notify(queue: .main) {
                let startDelay: TimeInterval = 1 // Delay to ensure all players are ready
                let startTime = self.players.values.first?.deviceCurrentTime ?? startDelay

                self.players.forEach { (fileName, player) in
                    player.currentTime = 0.0
                    player.play(atTime: startTime + startDelay)
                    
                    self.startAmplitudeUpdate(for: fileName)
                }
            }
        }
        
        isMasterControlShowing = true
    }

    @objc func pauseResumeMix() {
        isMixPaused.toggle()
        
        if isMixPaused {
            for player in players.values {
                player.pause()
                pausedTime = player.currentTime
                playerDeviceCurrTime = player.deviceCurrentTime
            }
            print("All players paused successfully \(pausedTime) \(playerDeviceCurrTime)")
        } else {
            let startDelay: TimeInterval = 1
            let startTime = players.values.first?.deviceCurrentTime ?? startDelay
            players.forEach { (_, player) in
                player.currentTime = pausedTime
                player.play(atTime: startTime + startDelay)
            }
        }
    }

    @objc func setVolume(_ volume: Float, forFileName fileName: String) {
        guard let player = audioPlayers[fileName] else {
            sendGenAppErrors(errors: "Player does not exist for \(fileName)")
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            player.volume = volume
        }
    }

    @objc func setPan(_ pan: Float, forFileName fileName: String) {
        guard let player = audioPlayers[fileName] else {
            sendGenAppErrors(errors: "Player does not exist for \(fileName)")
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            player.pan = pan
        }
    }

    func startAmplitudeUpdate(for fileName: String) {
        amplitudeTimers[fileName]?.invalidate()
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
            self.sendTrackAmplitueUpdate()
        }
    }

    @objc func setPlaybackPosition(to progress: Double) {
        let duration = players.values.first?.duration ?? 0.0
        let newTime = progress * duration
        let startDelay: TimeInterval = 0.25
        let startTime = players.values.first?.deviceCurrentTime ?? startDelay
        players.forEach { (_, player) in
            player.currentTime = newTime
            player.play(atTime: startTime + startDelay)
        }
        startProgressUpdateTimer()
        isMixPaused.toggle()
    }

    @objc func audioSliderChanged(_ point: Double) {
//        print("Audio slider just changed to ---> ", point)
        setPlaybackPosition(to: Double(point))
    }

   @objc func setAudioProgress(_ point: Double) {
        isMixPaused = true
        if isMixPaused {
            for player in players.values {
                player.pause()
                pausedTime = player.currentTime
                playerDeviceCurrTime = player.deviceCurrentTime
            }
            audioProgressValues = point
            progressUpdateTimer?.invalidate()
        }
    }

    @objc func resetApp() -> String {
    // Stop and reset all audio players
    audioPlayers.values.forEach { $0.stop() }
    audioPlayers.removeAll()

    // Invalidate and reset amplitude timers
    amplitudeTimers.values.forEach { $0.invalidate() }
    amplitudeTimers.removeAll()

    // Reset the audio engine
    audioEngine.reset()

    // Clear all stored data
    players.removeAll()
    audioFileURLs.removeAll()
    audioProperties.removeAll()
    audiosVolumesSliderValues.removeAll()
    audiosPanSliderValues.removeAll()
    audioAmplitudes.removeAll()

    // Reset progress values and flags
    audioProgressValues = 0
    pausedTime = 0.0
    playerDeviceCurrTime = 0.0
    isMixBtnClicked = false
    isMixPaused = false
    isMasterControlShowing = false

    // Invalidate the progress update timer
    progressUpdateTimer?.invalidate()
    progressUpdateTimer = nil

    // Notify React Native about the reset (if needed)
    sendEvent(withName: "AppReset", body: ["status": "complete"])

    return "App has been reset successfully"
}

    // func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
    //     DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
    //         self.resetApp()
    //     }
    // }

    @objc func appDidEnterBackground() {
        resetApp()
    }

    @objc func appWillTerminate() {
        resetApp()
    }

    private func configureAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default)
            try audioSession.setActive(true)
        } catch {
            print("Failed to set audio session category: \(error.localizedDescription)")
        }
    }
    
    func getAudioProperties(for url: URL) {
    let asset = AVAsset(url: url)
    var properties: [String: Any] = [:]

    Task {
        do {
            var durationInSeconds: Float64 = 0

            if #available(iOS 15, *) {
                let duration = try await asset.load(.duration)
                durationInSeconds = CMTimeGetSeconds(duration)
            } else {
                // Fallback on earlier versions
                durationInSeconds = CMTimeGetSeconds(asset.duration)
            }

            DispatchQueue.main.async {
                properties["duration"] = durationInSeconds
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
            sendGenAppErrors(errors: "Error loading duration: \(error.localizedDescription)")
        }
    }
    }
     // Function to download audio files from URLs
 @objc func downloadAudioFiles(_ urlStrings: [String]) {
     resetApp()
     sendDownloadStart()
    let totalFiles = urlStrings.count
    var downloadedFiles = 0
    var downloadedFileNames: [String] = []
    var hasErrorOccurred = false
     
    let dispatchGroup = DispatchGroup()
    
    for urlString in urlStrings {
        guard let url = URL(string: urlString) else {
            print("Invalid URL string: \(urlString)")
            sendDownloadErrors(errors: "Invalid URL string: \(urlString)")
            break
        }
        
        dispatchGroup.enter()
        let fileName = url.lastPathComponent
        
        let downloadTask = URLSession.shared.dataTask(with: url) { (data, response, error) in
            guard let data = data, error == nil else {
                print("Failed to download file: \(error?.localizedDescription ?? "Unknown error")")
                self.sendDownloadErrors(errors: "Failed to download file: \(error?.localizedDescription ?? "Unknown error")")
                hasErrorOccurred = true
                dispatchGroup.leave()
                return
            }
            
            if hasErrorOccurred {
                return
            }
            do {
                let player = try AVAudioPlayer(data: data)
                player.prepareToPlay()
                DispatchQueue.main.async {
                    self.audioPlayers[fileName] = player
                    self.getAudioProperties(for: url)
                    downloadedFiles += 1
                    downloadedFileNames.append(fileName)
                    let progress = Double(downloadedFiles) / Double(totalFiles)
                    self.sendProgressUpdate(progress)
                }
                dispatchGroup.leave()
            } catch {
                hasErrorOccurred = true
                self.sendDownloadErrors(errors: "Error creating audio player for \(fileName): \(error.localizedDescription)")
                
                dispatchGroup.leave()
            }
        }
        
        
        downloadTask.resume()
        
        // Break the loop if any error has occurred
               if hasErrorOccurred {
                   break
               }
        
    }
    
     dispatchGroup.notify(queue: .main) {
            if hasErrorOccurred {
                self.resetApp()
            } else {
                self.isMixBtnClicked = true
                self.sendProgressUpdate(1.0)
                self.sendEvent(withName: "DownloadComplete", body: ["fileNames": downloadedFileNames])
            }
        }
}


}




