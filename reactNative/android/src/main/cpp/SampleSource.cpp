/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <math.h>
#include "SampleSource.h"

static const char *TAG = "SampleSource";
static const float MIN_DB = -40;

namespace iolib {

    SampleSource::SampleSource(const char* fileName, float pan)
            :
              mFileName(fileName),
              mFileDescriptor(open(fileName, O_RDONLY)),
              mStream(parselib::FileInputStream(mFileDescriptor)),  // Initialize mStream with the file descriptor
              mReader(parselib::WavStreamReader(&mStream)),  // Initialize the reader with the stream
              mCurSampleIndex(0),
              mIsPlaying(false),
              mGain(1.0f)
    {
        mReader.parse();
        setPan(pan);

        auto total = mReader.getNumSampleFrames();

        float minDecibels = 0;
        float maxDecibels = MIN_DB;

        // We'll take average for a chunk since that is a good practical approximation
        int chunkSize = 2048;
        float* buffer = new float[2048];
        for (int i = 0; i < total; i+=chunkSize) {
            // Skipping last chunk to avoid dealing with end-of-data bounds
            if ((total - i) < chunkSize)
                continue;

            // Calculate average amplitude for the chunk
            float minAmplitude = 1;
            float maxAmplitude = 0;
            mReader.getDataFloat(buffer, 2048 / mReader.getNumChannels());
            for (int j = 0; j < chunkSize; j++) {
                float f = buffer[j];
                if (f < 0) f *= -1;

                if (f > maxAmplitude)
                    maxAmplitude = f;

                if (f < minAmplitude)
                    minAmplitude = f;
            }

            float tmpMinDecibels;
            if (minAmplitude == 0)
                tmpMinDecibels = MIN_DB;
            else tmpMinDecibels = fmax(MIN_DB, log10(minAmplitude) * (float)10);
            if (tmpMinDecibels < minDecibels)
                minDecibels = tmpMinDecibels;

            float tmpMaxDecibels = log10(maxAmplitude) * (float)10;
            if (tmpMaxDecibels > maxDecibels)
                maxDecibels = tmpMaxDecibels;
        }

        delete[] buffer;
        mMinDecibels = minDecibels;
        mMaxDecibels = maxDecibels;
    }

    void SampleSource::mixAudio(float* outBuff, int numChannels, int32_t numFrames) {
        int32_t sampleChannels = mReader.getNumChannels();
        int32_t numSamples = mReader.getNumSampleFrames() * sampleChannels;
        int32_t samplesLeft = numSamples - mCurSampleIndex;
        int32_t numWriteFrames = mIsPlaying
                                 ? std::min(numFrames, samplesLeft / sampleChannels)
                                 : 0;

        float* buffer = new float[numWriteFrames * sampleChannels];

        mReader.getDataFloat(buffer, numWriteFrames);

        float amplitudeMax = 0;

        if (numWriteFrames != 0) {
            if ((sampleChannels == 1) && (numChannels == 1)) {
                // MONO output from MONO samples
                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex++;
                    if (abs(buffer[frameIndex]) > amplitudeMax) {
                        amplitudeMax = abs(buffer[frameIndex]);
                    }
                    outBuff[frameIndex] += buffer[frameIndex++] * mGain;
                }
            } else if ((sampleChannels == 1) && (numChannels == 2)) {
                // STEREO output from MONO samples
                int dstSampleIndex = 0;
                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex++;
                    if (abs(buffer[frameIndex]) > amplitudeMax) {
                        amplitudeMax = abs(buffer[frameIndex]);
                    }
                    outBuff[dstSampleIndex++] += buffer[frameIndex] * mLeftGain;
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mRightGain;
                }
            } else if ((sampleChannels == 2) && (numChannels == 1)) {
                // MONO output from STEREO samples
                int dstSampleIndex = 0;
                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex += 2;
                    if (abs(buffer[frameIndex]) > amplitudeMax) {
                        amplitudeMax = abs(buffer[frameIndex]);
                    }
                    if (abs(buffer[frameIndex + 1]) > amplitudeMax) {
                        amplitudeMax = abs(buffer[frameIndex + 1]);
                    }
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mLeftGain +
                                                 buffer[frameIndex++] * mRightGain;
                }
            } else if ((sampleChannels == 2) && (numChannels == 2)) {
                // STEREO output from STEREO samples
                int dstSampleIndex = 0;

                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex += 2;
                    if (abs(buffer[frameIndex]) > amplitudeMax) {
                        amplitudeMax = abs(buffer[frameIndex]);
                    }
                    if (abs(buffer[frameIndex + 1]) > amplitudeMax) {
                        amplitudeMax = abs(buffer[frameIndex + 1]);
                    }
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mLeftGain;
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mRightGain;
                }
            }

            if (mCurSampleIndex >= numSamples) {
                mIsPlaying = false;
            }
        }

        float logPower = fmax((float)MIN_DB, log10(amplitudeMax) * (float)10);
        float scaledPower = fmin((float)1, (logPower - mMinDecibels) / (mMaxDecibels - mMinDecibels));
        mLastAmplitude = scaledPower * mGain;
        delete[] buffer;

        // silence
        // no need as the output buffer would need to have been filled with silence
        // to be mixed into
    }

    float SampleSource::getPosition() {
        auto current = static_cast<float>(mCurSampleIndex);
        auto total = static_cast<float>(mReader.getNumSampleFrames());

        return current / total;
    }

    void SampleSource::setPosition(float position) {
        auto total = static_cast<float>(mReader.getNumSampleFrames());
        auto newPosition = static_cast<int>(position * total);
        if (newPosition % 2 == 1)
            newPosition--;

        mCurSampleIndex = newPosition;
        mReader.setDataPosition(mCurSampleIndex);
    }

    float SampleSource::getAmplitude() {
        return mLastAmplitude;
    }

}