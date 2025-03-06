# Neural Amp (NAM +AIDA-X) Model Loader with IR
This app is an Android Guitar Effects Processor which supports loading neural amp models. 
It also has an Impulse Response Loader.

You can download models / IR cab impulses from tonehunt.org

## Features
- Supports Neural Amp Modeler .nam files
- Supports RTNeural (AIDA-X) .json and .aidax files
- Supports .wav Impulse Response files with resample support
- Low(est possible) Latency on Android 
- Floating point audio processing at native device sample rate
- Record audio to MP3, Opus and Wav format
- Unlimited recording support
- USB OTG Audio Interface and USB Soundcard Support
- No Ads
- Open Source
- Made with ❤

## Latency
The app runs at native device sample rate and uses the Google Oboe Library.
It processes audio through a native callback mechanism. The audio samples 
sent to the app via Oboe's callback are directly processed by the plugin 
engine and sent back. It means that the app adds no latency of its own. Even
low end devices should be able to run NAM models easily.