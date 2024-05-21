#include <iostream>
#include <sndfile.h>
#include <vector>
#include <algorithm>

std::vector<float> mix_audio(const char *file1, const char *file2, const char *outfile)
{
    SF_INFO sfinfo1, sfinfo2;
    SNDFILE *sndfile1 = sf_open(file1, SFM_READ, &sfinfo1);
    if (!sndfile1)
    {
        throw std::runtime_error("Error opening file " + std::string(file1));
        sf_close(sndfile1);
        return;
    }

    SNDFILE *sndfile2 = sf_open(file2, SFM_READ, &sfinfo2);
    if (!sndfile2)
    {
        throw std::runtime_error("Error opening file " + std::string(file2));
        sf_close(sndfile2);
        return;
    }

    if (sfinfo1.channels != sfinfo2.channels || sfinfo1.samplerate != sfinfo2.samplerate)
    {
        throw std::runtime_error("Input files must have the same number of channels and sample rate");
        sf_close(sndfile1);
        sf_close(sndfile2);
        return;
    }

    sf_count_t frames1 = sfinfo1.frames;
    sf_count_t frames2 = sfinfo2.frames;
    sf_count_t frames = std::max(frames1, frames2);
    int channels = sfinfo1.channels;

    std::vector<float> buffer1(frames * channels);
    std::vector<float> buffer2(frames * channels);
    std::vector<float> outbuffer(frames * channels);

    sf_readf_float(sndfile1, buffer1.data(), frames);
    sf_readf_float(sndfile2, buffer2.data(), frames);

    float weight1 = 0.6f; // Weight for buffer1
    float weight2 = 0.4f; // Weight for buffer2

    for (sf_count_t i = 0; i < frames * channels; ++i)
    {
        outbuffer[i] = (buffer1[i] * weight1 + buffer2[i] * weight2); // Weighted averaging
    }

    sf_close(sndfile1);
    sf_close(sndfile2);

    return outbuffer;
}
