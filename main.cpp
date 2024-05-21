#include <iostream>
#include <sndfile.h>
#include <vector>
#include <algorithm>

void mix_audio(const char *file1, const char *file2, const char *outfile)
{
    SF_INFO sfinfo1, sfinfo2;
    SNDFILE *sndfile1 = sf_open(file1, SFM_READ, &sfinfo1);
    if (!sndfile1)
    {
        std::cerr << "Error opening file " << file1 << std::endl;
        return;
    }

    SNDFILE *sndfile2 = sf_open(file2, SFM_READ, &sfinfo2);
    if (!sndfile2)
    {
        std::cerr << "Error opening file " << file2 << std::endl;
        sf_close(sndfile1);
        return;
    }

    if (sfinfo1.channels != sfinfo2.channels || sfinfo1.samplerate != sfinfo2.samplerate)
    {
        std::cerr << "Input files must have the same number of channels and sample rate" << std::endl;
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

    SF_INFO sfinfo_out = sfinfo1;
    SNDFILE *sndfile_out = sf_open(outfile, SFM_WRITE, &sfinfo_out);
    if (!sndfile_out)
    {
        std::cerr << "Error creating output file " << outfile << std::endl;
        sf_close(sndfile1);
        sf_close(sndfile2);
        return;
    }

    sf_writef_float(sndfile_out, outbuffer.data(), frames);

    sf_close(sndfile1);
    sf_close(sndfile2);
    sf_close(sndfile_out);

    std::cout << "This is the buffer's data:  " << outbuffer.data() << std::endl;

    std::cout << "Mixing completed and written to " << outfile << std::endl;
}

int main(int argc, char *argv[])
{
    if (argc < 4)
    {
        std::cerr << "Usage: " << argv[0] << " <input_file1> <input_file2> <output_file>" << std::endl;
        return 1;
    }

    const char *file1 = argv[1];
    const char *file2 = argv[2];
    const char *outfile = argv[3];

    mix_audio(file1, file2, outfile);

    return 0;
}
