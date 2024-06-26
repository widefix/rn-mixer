import React, { useState } from 'react';
import { View, Text, ScrollView } from 'react-native';
import Slider from '@react-native-community/slider';

interface TrackControlProps {
    items: string[];
    setVolumeX: (value: number, item: string) => void;
    setPanX: (value: number, item: string) => void;
}

const TrackControl: React.FC<TrackControlProps> = ({ items, setPanX, setVolumeX }) => {
    const handleSliderChange = (item: string, value: number, type: string) => {
        console.log(`Item: ${item}, ${type}: ${value}`);
        // Add your function logic here
        if (type === 'volume') {
            setVolumeX(value, item);
        } else if (type === 'span') {
            setPanX(value, item);
        }
    };


    return (
        <ScrollView style={{ flex: 1 }}>
            {items.map((t, i) => (
                <View key={i} style={{ marginBottom: 20 }}>
                    <Text style={{ color: 'grey' }}>{i + 1 + '. '}{t}</Text>
                    <Text>Span</Text>
                    <Slider
                        style={{ width: 200, height: 40 }}
                        minimumValue={0}
                        maximumValue={1}
                        value={0.5}
                        onValueChange={(value) => handleSliderChange(t, value, 'span')}
                    />
                    <Text>Volume</Text>
                    <Slider
                        style={{ width: 200, height: 40 }}
                        minimumValue={0}
                        maximumValue={1}
                        value={0.5}
                        onValueChange={(value) => handleSliderChange(t, value, 'volume')}
                    />
                </View>
            ))}
        </ScrollView>
    );
};

export default TrackControl;
