import React, { useState, useEffect } from 'react';
import { View, Text } from 'react-native';
import Slider from '@react-native-community/slider';

interface PlayBackSliderProps {
    initialValue: number;
    onSlidingStart: (value: number) => void;
    onSlidingComplete: (value: number) => void;
}

const PlayBackSlider: React.FC<PlayBackSliderProps> = ({
    initialValue,
    onSlidingStart,
    onSlidingComplete
}) => {
    const [value, setValue] = useState<number>(initialValue);

    useEffect(() => {
        setValue(initialValue);
    }, [initialValue]);

    const handleSlidingStart = (newValue: number) => {
        console.log("Slider value on start: ", newValue);
        onSlidingStart(newValue);
    };

    const handleSlidingComplete = (newValue: number) => {
        console.log("Slider value on completion: ", newValue);
        setValue(newValue);
        onSlidingComplete(newValue);
    };

    return (
        <View style={{ marginVertical: 10, justifyContent: 'center', alignItems: 'center' }}>

            <Slider
                style={{ width: 300, height: 40 }}
                minimumValue={0}
                maximumValue={1}
                //step={0.001} // use for iOS
                onSlidingStart={handleSlidingStart}
                onSlidingComplete={handleSlidingComplete}
                value={value}
                minimumTrackTintColor="#1fb28a"
                maximumTrackTintColor="#d3d3d3"
                thumbTintColor="#b9e4c9"
            />
        </View>
    );
};

export default PlayBackSlider;
