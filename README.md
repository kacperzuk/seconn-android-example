seconn-android-example
===========

seconn-android-example is part of SeConn project. It's a protocol and set of libraries for secure communication. This repository contains example of using SeConn with Android. See also other repositories:

* [seconn](https://github.com/kacperzuk/seconn) - description of design and protocol, you should read it.
* [seconn-avr](https://github.com/kacperzuk/seconn-avr) - AVR library that implements the SeConn protocol
* [seconn-java](https://github.com/kacperzuk/seconn-java) - Java library that implements the SeConn protocol
* [seconn-arduino-example](https://github.com/kacperzuk/seconn-arduino-example) - Example Arduino sketch that uses seconn-avr

Usage
=====

1. Enable Bluetooth and pair with `seconn` device that supports SeConn protocol (for example Arduino board with [seconn-arduino-example](https://github.com/kacperzuk/seconn-arduino-example) on it).
2. Import this project into AndroidStudio.
3. Run on your device
4. App will automatically connect to `seconn` device and establish SeConn connection.
5. After establishing connection (when it says `SeConn state: AUTHENTICATED`). You can start sending messages.
