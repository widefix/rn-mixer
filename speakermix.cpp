#include <iostream>
#include <vector>
#include <algorithm>
#include <portaudio.h>

// Define your mixing function as before
void mix_audio(const float *buffer1, const float *buffer2, float *outbuffer, int frames, int channels)
{
    for (int i = 0; i < frames * channels; ++i)
    {
        outbuffer[i] = (buffer1[i] + buffer2[i]) / 2.0f; // Simple averaging
    }
}

int main(int argc, char *argv[])
{
    // Initialize PortAudio
    PaError err = Pa_Initialize();
    if (err != paNoError)
    {
        std::cerr << "PortAudio initialization failed: " << Pa_GetErrorText(err) << std::endl;
        return 1;
    }

    // Define audio parameters
    const int sampleRate = 44100;
    const int framesPerBuffer = 256;
    const int channels = 2; // Assuming stereo audio

    // Open PortAudio stream
    PaStream *stream;
    err = Pa_OpenDefaultStream(&stream, 0, channels, paFloat32, sampleRate, framesPerBuffer, nullptr, nullptr);
    if (err != paNoError)
    {
        std::cerr << "PortAudio stream opening failed: " << Pa_GetErrorText(err) << std::endl;
        Pa_Terminate();
        return 1;
    }

    // Allocate buffers for audio data
    std::vector<float> buffer1(framesPerBuffer * channels);
    std::vector<float> buffer2(framesPerBuffer * channels);
    std::vector<float> outbuffer(framesPerBuffer * channels);

    // Generate dummy audio data for demonstration
    for (int i = 0; i < framesPerBuffer * channels; ++i)
    {
        buffer1[i] = 0.5f * std::sin(2.0f * 3.14159f * i / framesPerBuffer); // Sine wave for buffer1
        buffer2[i] = 0.3f * std::sin(2.0f * 3.14159f * i / framesPerBuffer); // Sine wave for buffer2
    }

    // Start the PortAudio stream
    err = Pa_StartStream(stream);
    if (err != paNoError)
    {
        std::cerr << "PortAudio stream starting failed: " << Pa_GetErrorText(err) << std::endl;
        Pa_CloseStream(stream);
        Pa_Terminate();
        return 1;
    }

    // Main loop for audio playback
    while (true)
    {
        // Mix audio data
        mix_audio(buffer1.data(), buffer2.data(), outbuffer.data(), framesPerBuffer, channels);

        // Play mixed audio
        err = Pa_WriteStream(stream, outbuffer.data(), framesPerBuffer);
        if (err != paNoError)
        {
            std::cerr << "PortAudio write failed: " << Pa_GetErrorText(err) << std::endl;
            break;
        }
    }

    // Stop and close the PortAudio stream
    Pa_StopStream(stream);
    Pa_CloseStream(stream);

    // Terminate PortAudio
    Pa_Terminate();

    return 0;
}
