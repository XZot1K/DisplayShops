**[Wiki](https://github.com/XZot1K/DisplayShopsAPI/wiki) | [Commands](https://github.com/XZot1K/DisplayShopsAPI/wiki/commands) | [Permissions](https://github.com/XZot1K/DisplayShopsAPI/wiki/permissions)
| [How Does It Work?](https://github.com/XZot1K/DisplayShopsAPI/wiki/shop-guide) | [Developer API](https://github.com/XZot1K/DisplayShopsAPI/wiki/developer-api)
| [Java Docs](https://xzot1k.github.io/DisplayShopsAPI/)**

## v2.0+ code is unde rthe "recode" branch while v1.7.x code (pre-2.0) is under the "master" branch

This is the core DisplayShops source code, which depends on the [DisplayShopsAPI](https://github.com/XZot1K/DisplayShopsAPI). Sicne the core depends on the API, the DisplayShopsAPI will need to be cloned, built, and installed to your local Maven respository. 

Alternatively, the Latest API found on GitHub can be swapped out in the POM files of the core using jitpack.

<img src="https://imgur.com/mkPfGtg.png" width="150px" height="150px">

# DisplayShops

Create immersive simplistic shops with animations, efficient transaction handling, and much more!
***

** NOTE: all major MC jar version releases will need to be built using BuildTools for the individual per-version modules up until 1.20.4 (you can remove or disable them, but code will need adjustments) 

* To build DisplayShops core, clone the repository and open the project in Intellij IDE.
* Ensure the DisplayShopsAPI is installed and linked to the core as a seperate module.
* Run the "Build Jar" run configuration at the top-right of the IDE near the run/debug buttons. 
* If successful, the JAR will be located in the "target" folder found in the project directory.
