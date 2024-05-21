#include "audio_mixer.h"
#include <algorithm>

AVFormatContext *open_input_file(const char *filename)
{
    AVFormatContext *fmt_ctx = nullptr;
    if (avformat_open_input(&fmt_ctx, filename, nullptr, nullptr) < 0)
    {
        fprintf(stderr, "Could not open input file '%s'\n", filename);
        return nullptr;
    }
    if (avformat_find_stream_info(fmt_ctx, nullptr) < 0)
    {
        fprintf(stderr, "Could not find stream information\n");
        return nullptr;
    }
    return fmt_ctx;
}

AVCodecContext *get_codec_context(AVFormatContext *fmt_ctx)
{
    AVStream *audio_stream = nullptr;
    AVCodecParameters *codec_par = nullptr;
    for (unsigned int i = 0; i < fmt_ctx->nb_streams; ++i)
    {
        if (fmt_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO)
        {
            audio_stream = fmt_ctx->streams[i];
            codec_par = audio_stream->codecpar;
            break;
        }
    }
    if (!audio_stream)
    {
        fprintf(stderr, "Could not find audio stream\n");
        return nullptr;
    }

    AVCodec *codec = avcodec_find_decoder(codec_par->codec_id);
    if (!codec)
    {
        fprintf(stderr, "Unsupported codec!\n");
        return nullptr;
    }

    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx)
    {
        fprintf(stderr, "Could not allocate codec context\n");
        return nullptr;
    }

    if (avcodec_parameters_to_context(codec_ctx, codec_par) < 0)
    {
        fprintf(stderr, "Could not initialize codec context\n");
        return nullptr;
    }

    if (avcodec_open2(codec_ctx, codec, nullptr) < 0)
    {
        fprintf(stderr, "Could not open codec\n");
        return nullptr;
    }

    return codec_ctx;
}

void mix_audio_data(const AVFrame *frame1, const AVFrame *frame2, AVFrame *output_frame)
{
    int num_samples = std::min(frame1->nb_samples, frame2->nb_samples);

    for (int i = 0; i < num_samples; ++i)
    {
        float *data1 = (float *)frame1->data[0];
        float *data2 = (float *)frame2->data[0];
        float *output_data = (float *)output_frame->data[0];

        output_data[i] = data1[i] * 0.5f + data2[i] * 0.5f;
    }

    output_frame->nb_samples = num_samples;
}

void write_output_file(const char *filename, AVCodecContext *codec_ctx, AVFormatContext *fmt_ctx, AVFrame *frame)
{
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = nullptr;
    pkt.size = 0;

    int ret = avcodec_send_frame(codec_ctx, frame);
    if (ret < 0)
    {
        fprintf(stderr, "Error sending frame to codec context\n");
        return;
    }

    ret = avcodec_receive_packet(codec_ctx, &pkt);
    if (ret < 0)
    {
        fprintf(stderr, "Error receiving packet from codec context\n");
        return;
    }

    pkt.stream_index = 0;

    ret = av_write_frame(fmt_ctx, &pkt);
    if (ret < 0)
    {
        fprintf(stderr, "Error writing frame to output file\n");
    }

    av_packet_unref(&pkt);
}
