## 0. context
Developping in java with android studio on Windows
Command line is git-bash
SDK location: C:\Users\josel\AppData\Local\Android\Sdk
Gradle JDK: 	C:\Users\josel\softs_portable/jdk-17.0.14
## 1. calibration screen
Currently calibration has 2 steps: ping delay and phase, add a third step between the first 2.
The goal of this step is to refine the delay measurement done by the pind delay.
Use a 500ms chirp tone, the chirp begins a 200Hz and ends at 4000Hz, the frequency sweeping
between the start frequency and end frequency is exponential that is proportional to e^t, do
3 chirps separated by 500ms, show chirp delay measurements on screen.
Allow the user to repeat separately each phase of calibration
## 2. search window and sampling rate
Make the search window 300ms to account for possible electronic delays, display the sampling frequency on the first page for
  information