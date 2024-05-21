#ifndef AUDIO_MIXER_H
#define AUDIO_MIXER_H

extern "C"
{
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
#include <libavutil/audio_fifo.h>
}

// Function declarations
AVFormatContext *open_input_file(const char *filename);
AVCodecContext *get_codec_context(AVFormatContext *fmt_ctx);
void mix_audio_data(const AVFrame *frame1, const AVFrame *frame2, AVFrame *output_frame);
void write_output_file(const char *filename, AVCodecContext *codec_ctx, AVFormatContext *fmt_ctx, AVFrame *frame);

#endif // AUDIO_MIXER_H
