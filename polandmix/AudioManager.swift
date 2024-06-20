import AVFoundation
import SwiftUI
import MobileCoreServices
import UIKit
import Combine

class AudioManager: NSObject, ObservableObject, AVAudioPlayerDelegate {
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
    
    // Predefined list of URLs
    private let audioFileURLsList: [URL] = [
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/226/original/Way_Maker__0_-_E_-_Original_--_1-Click.m4a")!,
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/228/original/Way_Maker__0_-_E_-_Original_--_2-Guide.m4a")!, 
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/236/original/Way_Maker__0_-_E_-_Original_--_11-Lead_Vox.m4a")!,
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/227/original/Way_Maker__0_-_E_-_Original_--_3-Drums.m4a")!,URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/229/original/Way_Maker__0_-_E_-_Original_--_4-Percussion.m4a")!,URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/232/original/Way_Maker__0_-_E_-_Original_--_5-Bass.m4a")!,URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/230/original/Way_Maker__0_-_E_-_Original_--_6-Acoustic.m4a")!,URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/234/original/Way_Maker__0_-_E_-_Original_--_7-Electric_1.m4a")!,URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/235/original/Way_Maker__0_-_E_-_Original_--_8-Electric_2.m4a")!,
         URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/237/original/Way_Maker__0_-_E_-_Original_--_9-Main_Keys.m4a")!,
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/231/original/Way_Maker__0_-_E_-_Original_--_10-Aux_Keys.m4a")!,
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/238/original/Way_Maker__0_-_E_-_Original_--_12-Soprano_Vox.m4a")!,
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/239/original/Way_Maker__0_-_E_-_Original_--_13-Tenor_Vox.m4a")!,
        URL(string: "https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/233/original/Way_Maker__0_-_E_-_Original_--_14-Choir.m4a")!,
        
    ]
    
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
    
    // Function to configure the audio session
    private func configureAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default)
            try audioSession.setActive(true)
        } catch {
            print("Failed to set audio session category: \(error.localizedDescription)")
        }
    }
    
    override init() {
        super.init()
        configureAudioSession()
        // Set up observer for app lifecycle events
        NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appWillTerminate), name: UIApplication.willTerminateNotification, object: nil)
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

    // Function to download audio files from URLs
    func downloadAudioFiles() {
        resetApp()
        let urls = audioFileURLsList
        let totalFiles = urls.count
        var downloadedFiles = 0

        let dispatchGroup = DispatchGroup()
        
        for url in urls {
            dispatchGroup.enter()
            let fileName = url.lastPathComponent
            
            let downloadTask = URLSession.shared.dataTask(with: url) { (data, response, error) in
                guard let data = data, error == nil else {
                    print("Failed to download file: \(error?.localizedDescription ?? "Unknown error")")
                    dispatchGroup.leave()
                    return
                }
                
                do {
                    let player = try AVAudioPlayer(data: data)
                    player.prepareToPlay()
                    DispatchQueue.main.async {
                        self.audioPlayers[fileName] = player
                        self.getAudioProperties(for: url)
                        downloadedFiles += 1
                        self.downloadProgress = Double(downloadedFiles) / Double(totalFiles)
                    }
                    dispatchGroup.leave()
                } catch {
                    print("Error creating audio player for \(fileName): \(error.localizedDescription)")
                    dispatchGroup.leave()
                }
            }
            
            downloadTask.resume()
        }
        
        dispatchGroup.notify(queue: .main) {
            self.isMixBtnClicked = true
            self.downloadProgress = 1.0
        }
    }

    func pauseResumeMix() {
        isMixPaused.toggle()
        
        if isMixPaused {
            for player in players.values {
                player.pause()
                pausedTime = player.currentTime
                playerDeviceCurrTime = player.deviceCurrentTime
            }
            print("All players paused successfully \(pausedTime) \(playerDeviceCurrTime)")
        } else {
            let startDelay: TimeInterval = 1 // Delay to ensure all players are ready
            let startTime = players.values.first?.deviceCurrentTime ?? startDelay
            
            print("\(startTime) ::::: This is the start-time init :::::")

            players.forEach { (_, player) in
                player.currentTime = pausedTime
                player.play(atTime: startTime + startDelay)
            }
        }
    }

    func playAudio() {
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
                
                print("\(startTime) ::::: This is the start-time init :::::")

                self.players.forEach { (fileName, player) in
                    player.currentTime = 0.0
                    player.play(atTime: startTime + startDelay)
                    
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
    
    func setPlaybackPosition(to progress: Double) {
        let duration = players.values.first?.duration ?? 0.00
        let newTime = progress * duration

        let startDelay: TimeInterval = 0.25 // Delay to ensure all players are ready
        let startTime = players.values.first?.deviceCurrentTime ?? startDelay
        
        print("\(startTime) ::::: This is the start-time init :::::")

        players.forEach {  (_, player) in
            player.currentTime = newTime
            player.play(atTime: (startTime) + startDelay)
        }
        startProgressUpdateTimer()
        isMixPaused.toggle()
    }

    func audioSliderChanged(point: Double) {
        print("Audio slider just changed to ---> ", point)
        setPlaybackPosition(to: Double(point))
    }
    
    func setAudioProgress(point: Double) {
        isMixPaused = true
        if isMixPaused {
            for player in players.values {
                player.pause()
                pausedTime = player.currentTime
                playerDeviceCurrTime = player.deviceCurrentTime
            }
            
            audioProgressValues = point
            progressUpdateTimer?.invalidate() //
        }
    }

    func editPanX(pan: Float, fileName: String) {
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
        progressUpdateTimer?.invalidate()
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
