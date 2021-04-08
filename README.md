# youtubearchive
Automatically downloads given videos and checks if they are still online, also compares the videos to report on any video changes. 
Currently wip, don't know when it's gonna be finished but very usable at the moment.
# Get started
You will need:
- A database (best one would be MariaDB but MySQL works as well)
- Java Runtime Engine
- A simple batch file to start the jar
- A settings file (will be created automatically when first started)

# Example for Batch file and Settings file
## start.bat
`java -Dfile.encoding=UTF-8 -jar YoutubeArchive.jar`

`PAUSE`

## settings.txt
This settings file connects to a database called "test" running on MySQL. 
The drive is needed to show how much free space is left. If you don't know which one to use, just try to put different numbers (starting with 0), or just leave it at 0.

`url jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=utf8`

`user (user)`

`password (password)`

`drive 0`