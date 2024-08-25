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

#include "SampleSource.h"

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
                    if (buffer[frameIndex] > amplitudeMax) {
                        amplitudeMax = buffer[frameIndex];
                    }
                    outBuff[frameIndex] += buffer[frameIndex++] * mGain;
                }
            } else if ((sampleChannels == 1) && (numChannels == 2)) {
                // STEREO output from MONO samples
                int dstSampleIndex = 0;
                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex++;
                    if (buffer[frameIndex] > amplitudeMax) {
                        amplitudeMax = buffer[frameIndex];
                    }
                    outBuff[dstSampleIndex++] += buffer[frameIndex] * mLeftGain;
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mRightGain;
                }
            } else if ((sampleChannels == 2) && (numChannels == 1)) {
                // MONO output from STEREO samples
                int dstSampleIndex = 0;
                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex += 2;
                    if (buffer[frameIndex] > amplitudeMax) {
                        amplitudeMax = buffer[frameIndex];
                    }
                    if (buffer[frameIndex + 1] > amplitudeMax) {
                        amplitudeMax = buffer[frameIndex + 1];
                    }
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mLeftGain +
                                                 buffer[frameIndex++] * mRightGain;
                }
            } else if ((sampleChannels == 2) && (numChannels == 2)) {
                // STEREO output from STEREO samples
                int dstSampleIndex = 0;

                for (int32_t frameIndex = 0; frameIndex < numWriteFrames * sampleChannels;) {
                    mCurSampleIndex += 2;
                    if (buffer[frameIndex] > amplitudeMax) {
                        amplitudeMax = buffer[frameIndex];
                    }
                    if (buffer[frameIndex + 1] > amplitudeMax) {
                        amplitudeMax = buffer[frameIndex + 1];
                    }
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mLeftGain;
                    outBuff[dstSampleIndex++] += buffer[frameIndex++] * mRightGain;
                }
            }

            if (mCurSampleIndex >= numSamples) {
                mIsPlaying = false;
            }
        }

        mLastAmplitude = amplitudeMax;
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