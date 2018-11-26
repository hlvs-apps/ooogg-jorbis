# OOOGG-JOrbis (OOOGG Vorbis Plugin)
OOOGG-JOrbis is a plugin for OOOGG that adds support for the Opus codec to it, allowing playback of OGG Vorbis files through Java Sound API.

It's based on the JOrbis library (developed by JCraft, Inc.), but without unnecessary source code.

OOOGG-JOrbis replaces Vorbis SPI, developed by JavaZOOM, and can be used in place of it.

OOOGG-JOrbis has only one dependency: OOOGG, an object-oriented OGG container implementation. OOOGG source code can be obtained in: https://github.com/allantaborda/ooogg

To include OOOGG-JOrbis as a dependency in the pom.xml file (in the case of using Maven), simply include the following excerpt inside the "dependencies" tag of the file:

```
<dependency>
	<groupId>com.allantaborda</groupId>
	<artifactId>ooogg-jorbis</artifactId>
	<version>0.0.23-SNAPSHOT</version>
</dependency>
```
