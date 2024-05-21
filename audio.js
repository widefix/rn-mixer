const ffmpeg = require('fluent-ffmpeg');
const fs = require('fs');

// Input audio file paths
const inputFile1 = 'fileO.wav';
const inputFile2 = 'fileT.wav';

// Output mixed audio file path
const outputFile = 'output.wav';

// Create a new ffmpeg command
const command = ffmpeg();

// Add input files to the command
command.input(inputFile1).input(inputFile2);

// Mix the audio from both input files
command.complexFilter(['amix=inputs=2:duration=longest']);

// Set output format and save the mixed audio to disk
command.output(outputFile).on('end', () => {
    console.log('Audio mixing complete.');
}).on('error', (err) => {
    console.error('Error mixing audio:', err);
}).run();
